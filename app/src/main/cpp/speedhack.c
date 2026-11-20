// speedhack only: constant time multiplier for YOUR OWN game.
// No network code, no extra threads, no logging.
// Link: shadowhook, pthread, m
#define _GNU_SOURCE
#include <sys/time.h>
#include <pthread.h>
#include <stdint.h>
#include <time.h>
#include "shadowhook.h"

#define SPEED 10.0   // time multiplier (1.0 = normal). Try 1.5 / 2.0 / 3.0

typedef int (*CLOCK_GETTIME)(clockid_t, struct timespec *);
typedef int (*GETTIMEOFDAY)(struct timeval *, struct timezone *);

static CLOCK_GETTIME real_clock_gettime;
static GETTIMEOFDAY real_gettimeofday;

// Every clock is anchored at the SAME instant, then runs at SPEED x real time,
// so all clock types stay consistent with each other.
typedef struct { clockid_t id; int64_t real0, fake0; } Clk;
enum { C_MONO, C_BOOT, C_REAL, C_RAW, C_MONO_C, C_REAL_C, C_N };
static Clk clk[C_N] = {
    {CLOCK_MONOTONIC, 0, 0}, {CLOCK_BOOTTIME, 0, 0}, {CLOCK_REALTIME, 0, 0},
    {CLOCK_MONOTONIC_RAW, 0, 0}, {CLOCK_MONOTONIC_COARSE, 0, 0}, {CLOCK_REALTIME_COARSE, 0, 0},
};
static const double g_speed = SPEED;
static int g_tinit = 0;
static pthread_mutex_t g_tlock = PTHREAD_MUTEX_INITIALIZER;

static void init_clocks(void) { // g_tlock held
    for (int i = 0; i < C_N; i++) {
        struct timespec t;
        if (real_clock_gettime(clk[i].id, &t) == 0)
            clk[i].real0 = clk[i].fake0 = (int64_t)t.tv_sec * 1000000000LL + t.tv_nsec;
    }
    g_tinit = 1;
}

static int64_t fake_ns(int i, int64_t real_ns) { // g_tlock held
    if (!g_tinit) init_clocks();
    return clk[i].fake0 + (int64_t)((double)(real_ns - clk[i].real0) * g_speed);
}

static int clk_index(clockid_t id) {
    switch (id) {
        case CLOCK_MONOTONIC:        return C_MONO;
        case CLOCK_BOOTTIME:         return C_BOOT;
        case CLOCK_REALTIME:         return C_REAL;
        case CLOCK_MONOTONIC_RAW:    return C_RAW;
        case CLOCK_MONOTONIC_COARSE: return C_MONO_C;
        case CLOCK_REALTIME_COARSE:  return C_REAL_C;
        default:                     return -1;
    }
}

int new_clock_gettime(clockid_t id, struct timespec *tp) {
    if (!real_clock_gettime) return -1;
    struct timespec c;
    int r = real_clock_gettime(id, &c);
    if (r != 0 || !tp) return r;
    int i = clk_index(id);
    if (i < 0) { *tp = c; return r; }
    pthread_mutex_lock(&g_tlock);
    int64_t ns = fake_ns(i, (int64_t)c.tv_sec * 1000000000LL + c.tv_nsec);
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
    int64_t ns = fake_ns(C_REAL, (int64_t)c.tv_sec * 1000000000LL + (int64_t)c.tv_usec * 1000);
    pthread_mutex_unlock(&g_tlock);
    tv->tv_sec = ns / 1000000000LL;
    tv->tv_usec = (ns % 1000000000LL) / 1000;
    return r;
}

time_t new_time(time_t *t) { // time(NULL) users get the sped-up wall clock too
    struct timeval tv;
    if (new_gettimeofday(&tv, NULL) != 0) return (time_t)-1;
    if (t) *t = tv.tv_sec;
    return tv.tv_sec;
}

void __attribute__((constructor)) init_speedhack() {
    static void *real_time_unused;
    shadowhook_init(SHADOWHOOK_MODE_SHARED, false);
    shadowhook_hook_sym_name("libc.so", "clock_gettime", (void *)new_clock_gettime, (void **)&real_clock_gettime);
    shadowhook_hook_sym_name("libc.so", "gettimeofday", (void *)new_gettimeofday, (void **)&real_gettimeofday);
    shadowhook_hook_sym_name("libc.so", "time", (void *)new_time, (void **)&real_time_unused);
    // if a hook failed, fall back to the plain libc function so nothing crashes on a NULL pointer
    if (!real_clock_gettime) real_clock_gettime = clock_gettime;
    if (!real_gettimeofday)  real_gettimeofday  = (GETTIMEOFDAY)gettimeofday;
}
