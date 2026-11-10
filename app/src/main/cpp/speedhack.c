#include <sys/time.h>
#include <stddef.h>
#include <inttypes.h>
#include <math.h>
#include <stdio.h>
#include <time.h>
#include <jni.h>

// تحديد دالة الوقت الأصلي من النظام
typedef int (*CLOCK_GETTIME) (clockid_t clk_id, struct timespec *tp);
CLOCK_GETTIME real_clock_gettime = NULL;

// تم التعديل إلى 1.5 لضمان الاستقرار وتفادي مشاكل الحماية والسيرفر
static double speed_multiplier = 1.5; 

static struct timespec start_real_time;
static struct timespec start_fake_time;
static int is_initialized = 0;

// الدالة البديلة التي سيتم حقنها في اللعبة
int new_clock_gettime(clockid_t clk_id, struct timespec *tp) {
    if (real_clock_gettime == NULL) {
        return -1; 
    }

    struct timespec current_time;
    int result = real_clock_gettime(clk_id, &current_time);
    
    if (result != 0 || tp == NULL) {
        return result;
    }

    // تسريع الساعات الحساسة للحركة الفيزيائية داخل الملعب (MONOTONIC & BOOTTIME)
    if (clk_id == CLOCK_MONOTONIC || clk_id == CLOCK_BOOTTIME || clk_id == 1) {
        if (!is_initialized) {
            start_real_time = current_time;
            start_fake_time = current_time;
            is_initialized = 1;
        }

        // حساب الفارق الزمني الحقيقي
        double elapsed_sec = (current_time.tv_sec - start_real_time.tv_sec) + 
                             (current_time.tv_nsec - start_real_time.tv_nsec) / 1000000000.0;

        // ضرب الفارق في 1.5 لتوليد التوقيت المتسارع الآمن
        double fake_elapsed_sec = elapsed_sec * speed_multiplier;

        // حساب الوقت المزيف الجديد لإرساله للعبة
        int64_t fake_sec = start_fake_time.tv_sec + (int64_t)fake_elapsed_sec;
        int64_t fake_nsec = start_fake_time.tv_nsec + (int64_t)((fake_elapsed_sec - (int64_t)fake_elapsed_sec) * 1000000000.0);

        if (fake_nsec >= 1000000000) {
            fake_sec += fake_nsec / 1000000000;
            fake_nsec %= 1000000000;
        }

        tp->tv_sec = fake_sec;
        tp->tv_nsec = fake_nsec;
    } else {
        // بقية التواقيت تترك طبيعية لتجنب أي كراش في النظام
        *tp = current_time;
    }

    return result;
}

// دالة تهيئة الموديول عند الحقن داخل الرام عبر ShizuPosed
void __attribute__((constructor)) init_speedhack() {
    real_clock_gettime = (CLOCK_GETTIME)clock_gettime;
}
