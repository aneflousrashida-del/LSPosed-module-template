#include <sys/time.h>
#include <stddef.h>
#include <inttypes.h>
#include <time.h>
#include <jni.h>
#include "shadowhook.h"

typedef int (*CLOCK_GETTIME)(clockid_t clk_id, struct timespec *tp);
typedef int (*GETTIMEOFDAY)(struct timeval *tv, struct timezone *tz);

CLOCK_GETTIME real_clock_gettime = NULL;
GETTIMEOFDAY real_gettimeofday = NULL;

static double speed_multiplier = 10.0;

static struct timespec start_real_ts;
static struct timespec start_fake_ts;
static int ts_initialized = 0;

static struct timeval start_real_tv;
static struct timeval start_fake_tv;
static int tv_initialized = 0;

int new_clock_gettime(clockid_t clk_id, struct timespec *tp) {
    if (real_clock_gettime == NULL) return -1;
    struct timespec current_time;
    int result = real_clock_gettime(clk_id, &current_time);
    if (result != 0 || tp == NULL) return result;

    if (clk_id == CLOCK_MONOTONIC || clk_id == CLOCK_BOOTTIME || clk_id == 1) {
        if (!ts_initialized) {
            start_real_ts = current_time;
            start_fake_ts = current_time;
            ts_initialized = 1;
        }
        double elapsed = (current_time.tv_sec - start_real_ts.tv_sec) +
                         (current_time.tv_nsec - start_real_ts.tv_nsec) / 1000000000.0;
        double fake_elapsed = elapsed * speed_multiplier;

        int64_t fake_sec = start_fake_ts.tv_sec + (int64_t)fake_elapsed;
        int64_t fake_nsec = start_fake_ts.tv_nsec + (int64_t)((fake_elapsed - (int64_t)fake_elapsed) * 1000000000.0);
        if (fake_nsec >= 1000000000) {
            fake_sec += fake_nsec / 1000000000;
            fake_nsec %= 1000000000;
        }
        tp->tv_sec = fake_sec;
        tp->tv_nsec = fake_nsec;
    } else {
        *tp = current_time;
    }
    return result;
}

int new_gettimeofday(struct timeval *tv, struct timezone *tz) {
    if (real_gettimeofday == NULL) return -1;
    struct timeval current_tv;
    int result = real_gettimeofday(&current_tv, tz);
    if (result != 0 || tv == NULL) return result;

    if (!tv_initialized) {
        start_real_tv = current_tv;
        start_fake_tv = current_tv;
        tv_initialized = 1;
    }
    double elapsed = (current_tv.tv_sec - start_real_tv.tv_sec) +
                     (current_tv.tv_usec - start_real_tv.tv_usec) / 1000000.0;
    double fake_elapsed = elapsed * speed_multiplier;

    int64_t fake_sec = start_fake_tv.tv_sec + (int64_t)fake_elapsed;
    int64_t fake_usec = start_fake_tv.tv_usec + (int64_t)((fake_elapsed - (int64_t)fake_elapsed) * 1000000.0);
    if (fake_usec >= 1000000) {
        fake_sec += fake_usec / 1000000;
        fake_usec %= 1000000;
    }
    tv->tv_sec = fake_sec;
    tv->tv_usec = fake_usec;
    return result;
}

void __attribute__((constructor)) init_speedhack() {
    shadowhook_init(SHADOWHOOK_MODE_SHARED, false);
    shadowhook_hook_sym_name("libc.so", "clock_gettime", (void *)new_clock_gettime, (void **)&real_clock_gettime);
    shadowhook_hook_sym_name("libc.so", "gettimeofday", (void *)new_gettimeofday, (void **)&real_gettimeofday);
}
