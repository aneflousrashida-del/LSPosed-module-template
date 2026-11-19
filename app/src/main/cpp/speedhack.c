// speedhack + UDP SEND-side chaos test module (jitter / bandwidth / burst loss)
// Receive path is NOT hooked at all (100% untouched).
// For testing YOUR OWN game under extreme conditions.
// Link: shadowhook, pthread (no logging)
#define _GNU_SOURCE
#include <sys/time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <pthread.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <time.h>
#include <jni.h>
#include "shadowhook.h"


// ---------------- Settings ----------------
#define QCAP       (64 * 1024)                 // max bytes queued by the bandwidth limiter
#define MAXPKT     2048
static int g_port = 0;                         // 0 = all UDP sockets, else only this remote port

// ---------------- Scenarios ----------------
// Tune these values: each row is one test.
typedef struct {
    const char *name;
    double speed;            // time multiplier
    int delay_ms, jitter_ms; // delay = base + random(0..jitter)
    int bw_kbps;             // 0 = unlimited
    double burst_p;          // chance per packet to start a burst
    int burst_min, burst_max;// burst length (packets)
    double loss_pct;         // random loss %
    int stall_every_ms;      // 0 = no stall; else gap between stalls
    int stall_ms;            // how long all packets are held, then released at once
} Scn;

static const Scn SCN[] = {
    // name                  speed delay jit bw burst min max loss stall_every stall
    {"6 all four (extreme)",  -10000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000.0, 0,  0,  0,  0.0,  0, 0,  0,     0,      0},
};
#define NSCN ((int)(sizeof(SCN) / sizeof(SCN[0])))

// ---------------- Real function pointers ----------------
typedef int (*CLOCK_GETTIME)(clockid_t, struct timespec *);
typedef int (*GETTIMEOFDAY)(struct timeval *, struct timezone *);
typedef ssize_t (*SENDTO)(int, const void *, size_t, int, const struct sockaddr *, socklen_t);
typedef ssize_t (*SEND)(int, const void *, size_t, int);

static CLOCK_GETTIME real_clock_gettime;
static GETTIMEOFDAY real_gettimeofday;
static SENDTO real_sendto;
static SEND real_send;

// ---------------- Time (speedhack) ----------------
typedef struct { clockid_t id; int64_t real0, fake0; int init; } Clk;
static Clk clk[3] = {{CLOCK_MONOTONIC}, {CLOCK_BOOTTIME}, {CLOCK_REALTIME}};
static double g_speed = 1.0;
static pthread_mutex_t g_tlock = PTHREAD_MUTEX_INITIALIZER;

static int64_t fake_ns(Clk *k, int64_t real_ns) { // call with g_tlock held
    if (!k->init) { k->real0 = k->fake0 = real_ns; k->init = 1; }
    return k->fake0 + (int64_t)((double)(real_ns - k->real0) * g_speed);
}

// Changing speed rebases each clock so time never jumps.
static void set_speed(double s) {
    pthread_mutex_lock(&g_tlock);
    for (int i = 0; i < 3; i++) {
        if (!clk[i].init) continue;
        struct timespec t;
        real_clock_gettime(clk[i].id, &t);
        int64_t r = (int64_t)t.tv_sec * 1000000000LL + t.tv_nsec;
        int64_t f = fake_ns(&clk[i], r);
        clk[i].real0 = r; clk[i].fake0 = f;
    }
    g_speed = s;
    pthread_mutex_unlock(&g_tlock);
}

int new_clock_gettime(clockid_t id, struct timespec *tp) {
    if (!real_clock_gettime) return -1;
    struct timespec c;
    int r = real_clock_gettime(id, &c);
    if (r != 0 || !tp) return r;
    Clk *k = id == CLOCK_MONOTONIC ? &clk[0] : id == CLOCK_BOOTTIME ? &clk[1] : NULL;
    if (!k) { *tp = c; return r; }
    pthread_mutex_lock(&g_tlock);
    int64_t ns = fake_ns(k, (int64_t)c.tv_sec * 1000000000LL + c.tv_nsec);
    pthread_mutex_unlock(&g_tlock);
    tp->tv_sec = ns / 1000000000LL;
    tp->tv_nsec = ns % 1000000000LL;
    return r;
}
int new_gettimeofday(struct timeval *tv, struct timezone *tz) {
    if (!real_gettimeofday) return -1;
    struct timeval c;
    int r = real_gettimeofday(&c, tz);
    if (r != 0 || !tv) return r;
    pthread_mutex_lock(&g_tlock);
    int64_t ns = fake_ns(&clk[2], (int64_t)c.tv_sec * 1000000000LL + (int64_t)c.tv_usec * 1000);
    pthread_mutex_unlock(&g_tlock);
    tv->tv_sec = ns / 1000000000LL;
    tv->tv_usec = (ns % 1000000000LL) / 1000;
    return r;
}

// ---------------- Chaos state ----------------
typedef struct { int burst_left; uint64_t pkts, drops, qdrops, delayed; } Dir;
typedef struct Pkt {
    struct Pkt *next; int fd, flags; size_t len; int64_t due;
    socklen_t tl; struct sockaddr_storage to; uint8_t data[MAXPKT];
} Pkt;

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static const Scn *g_scn = &SCN[0];
static int g_idx = 0;
static Dir ds;                      // send direction only
static Pkt *g_head = NULL;
static size_t g_qbytes = 0;
static int64_t g_link_free = 0;
static int64_t g_next_stall = 0, g_stall_until = 0;
static uint64_t rs = 88172645463325252ULL;

static uint32_t rnd(void) { rs ^= rs << 13; rs ^= rs >> 7; rs ^= rs << 17; return (uint32_t)(rs >> 11); }
static double frac(void) { return (rnd() & 0xFFFFFF) / 16777216.0; }

static int64_t now_ns(void) { // REAL monotonic time (never faked)
    struct timespec t;
    real_clock_gettime(CLOCK_MONOTONIC, &t);
    return (int64_t)t.tv_sec * 1000000000LL + t.tv_nsec;
}

static int should_drop(Dir *d, const Scn *s) { // g_lock held
    d->pkts++;
    if (d->burst_left > 0) { d->burst_left--; d->drops++; return 1; }
    if (s->burst_p > 0 && frac() < s->burst_p) {
        int span = s->burst_max - s->burst_min + 1;
        d->burst_left = s->burst_min + (span > 0 ? (int)(rnd() % span) : 0) - 1;
        if (d->burst_left < 0) d->burst_left = 0;
        d->drops++;
        return 1;
    }
    if (s->loss_pct > 0 && frac() * 100.0 < s->loss_pct) { d->drops++; return 1; }
    return 0;
}

static int is_target(int fd, const struct sockaddr *a) {
    int t; socklen_t l = sizeof t;
    if (getsockopt(fd, SOL_SOCKET, SO_TYPE, &t, &l) != 0 || t != SOCK_DGRAM) return 0;
    if (!g_port) return 1;
    struct sockaddr_storage ss; socklen_t sl = sizeof ss;
    if (!a) { if (getpeername(fd, (struct sockaddr *)&ss, &sl) != 0) return 0; a = (struct sockaddr *)&ss; }
    uint16_t p = a->sa_family == AF_INET ? ntohs(((const struct sockaddr_in *)a)->sin_port)
               : a->sa_family == AF_INET6 ? ntohs(((const struct sockaddr_in6 *)a)->sin6_port) : 0;
    return p == g_port;
}

// Stall: every stall_every_ms, hold ALL packets for stall_ms, then release them at once.
// Returns the (possibly postponed) due time. g_lock held.
static int64_t stall_hold(const Scn *s, int64_t now, int64_t due) {
    if (s->stall_ms <= 0 || s->stall_every_ms <= 0) return due;
    if (g_next_stall == 0) g_next_stall = now + (int64_t)s->stall_every_ms * 1000000LL;
    if (now >= g_next_stall) {
        g_stall_until = now + (int64_t)s->stall_ms * 1000000LL;
        g_next_stall = g_stall_until + (int64_t)s->stall_every_ms * 1000000LL;
    }
    if (now < g_stall_until && due < g_stall_until) due = g_stall_until;
    return due;
}
// Returns 1 if handled (result in *ret), 0 = pass through untouched.
static int chaos_send(int fd, const void *b, size_t n, int fl, const struct sockaddr *to, socklen_t tl, ssize_t *ret) {
    if (n > MAXPKT) return 0;
    pthread_mutex_lock(&g_lock);
    const Scn *s = g_scn;
    *ret = (ssize_t)n;
    if (should_drop(&ds, s)) { pthread_mutex_unlock(&g_lock); return 1; }

    int64_t now = now_ns(), due = now;
    if (s->bw_kbps > 0) { // serialization delay + tail-drop queue
        if (g_qbytes + n > QCAP) { ds.qdrops++; pthread_mutex_unlock(&g_lock); return 1; }
        int64_t st = g_link_free > now ? g_link_free : now;
        g_link_free = st + (int64_t)n * 8 * 1000000LL / s->bw_kbps;
        due = g_link_free;
    }
    if (s->delay_ms || s->jitter_ms)
        due += ((int64_t)s->delay_ms + (s->jitter_ms ? (int)(rnd() % (s->jitter_ms + 1)) : 0)) * 1000000LL;

    due = stall_hold(s, now, due);
    if (due > now && s->bw_kbps == 0 && g_qbytes + n > QCAP) { ds.qdrops++; pthread_mutex_unlock(&g_lock); return 1; }

    if (due <= now) { // nothing to simulate: send now
        pthread_mutex_unlock(&g_lock);
        *ret = real_sendto(fd, b, n, fl, to, tl);
        return 1;
    }
    Pkt *p = (Pkt *)malloc(sizeof(Pkt));
    if (!p) { pthread_mutex_unlock(&g_lock); return 0; }
    p->fd = fd; p->flags = fl; p->len = n; p->due = due; p->tl = 0;
    memcpy(p->data, b, n);
    if (to && tl > 0 && tl <= sizeof(p->to)) { memcpy(&p->to, to, tl); p->tl = tl; }
    Pkt **pp = &g_head; // sorted by due time (jitter => natural reordering)
    while (*pp && (*pp)->due <= due) pp = &(*pp)->next;
    p->next = *pp; *pp = p;
    g_qbytes += n; ds.delayed++;
    pthread_mutex_unlock(&g_lock);
    return 1;
}

// ---------------- Network hooks ----------------
ssize_t new_sendto(int fd, const void *b, size_t n, int fl, const struct sockaddr *to, socklen_t tl) {
    ssize_t r;
    if (real_sendto && is_target(fd, to) && chaos_send(fd, b, n, fl, to, tl, &r)) return r;
    return real_sendto(fd, b, n, fl, to, tl);
}
ssize_t new_send(int fd, const void *b, size_t n, int fl) {
    ssize_t r;
    if (real_send && real_sendto && is_target(fd, NULL) && chaos_send(fd, b, n, fl, NULL, 0, &r)) return r;
    return real_send(fd, b, n, fl);
}
// ---------------- Scenario apply + worker ----------------
static void apply_scn(int i) {
    pthread_mutex_lock(&g_lock);
    memset(&ds, 0, sizeof ds);
    g_scn = &SCN[i]; g_idx = i; g_link_free = 0; g_next_stall = 0; g_stall_until = 0;
    pthread_mutex_unlock(&g_lock);
    set_speed(SCN[i].speed);
}

static void *worker(void *arg) {
    (void)arg;
    for (;;) {
        usleep(500);
        int64_t now = now_ns();
        pthread_mutex_lock(&g_lock);
        while (g_head && g_head->due <= now) {
            Pkt *p = g_head; g_head = p->next; g_qbytes -= p->len;
            pthread_mutex_unlock(&g_lock);
            real_sendto(p->fd, p->data, p->len, p->flags, p->tl ? (struct sockaddr *)&p->to : NULL, p->tl);
            free(p);
            pthread_mutex_lock(&g_lock);
        }
        pthread_mutex_unlock(&g_lock);
    }
    return NULL;
}

void __attribute__((constructor)) init_speedhack() {
    shadowhook_init(SHADOWHOOK_MODE_SHARED, false);
    shadowhook_hook_sym_name("libc.so", "clock_gettime", (void *)new_clock_gettime, (void **)&real_clock_gettime);
    shadowhook_hook_sym_name("libc.so", "gettimeofday", (void *)new_gettimeofday, (void **)&real_gettimeofday);
    shadowhook_hook_sym_name("libc.so", "sendto", (void *)new_sendto, (void **)&real_sendto);
    shadowhook_hook_sym_name("libc.so", "send", (void *)new_send, (void **)&real_send);
    apply_scn(0); // scenario 6 only, active immediately
    pthread_t th;
    pthread_create(&th, NULL, worker, NULL);
    pthread_detach(th);
}
