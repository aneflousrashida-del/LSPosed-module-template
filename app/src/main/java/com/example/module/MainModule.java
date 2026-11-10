package com.example.module;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;

@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
public class MainModule extends XposedModule {

    // معامل السرعة المستقر والمناسب للعب أونلاين
    private static final float SPEED_MULTIPLIER = 10.0f;

    private static long startRealTimeMillis = 0;
    private static long startFakeTimeMillis = 0;
    private static long startUptimeMillis = 0;
    private static long startFakeUptimeMillis = 0;
    private static long startElapsedRealtime = 0;
    private static long startFakeElapsedRealtime = 0;
    private static boolean isInitialized = false;

    // تم ترك الـ Constructor فارغاً تماماً وبدون أي سطر super لتجاوز شرط المترجم بنجاح
    public MainModule() {
    }

    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        if (param.getPackageName().equals("jp.konami.pesam")) {
            initTimers();
        }
    }

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (param.getPackageName().equals("jp.konami.pesam")) {
            try {
                // 1. Hook لدالة System.currentTimeMillis
                Method currentTimeMethod = System.class.getDeclaredMethod("currentTimeMillis");
                hook(currentTimeMethod).intercept(new CurrentTimeInterceptor());

                // 2. Hook لدالة SystemClock.uptimeMillis (المسؤولة عن الحركة والأنيميشن)
                Method uptimeMethod = SystemClock.class.getDeclaredMethod("uptimeMillis");
                hook(uptimeMethod).intercept(new UptimeInterceptor());

                // 3. Hook لدالة SystemClock.elapsedRealtime
                Method elapsedMethod = SystemClock.class.getDeclaredMethod("elapsedRealtime");
                hook(elapsedMethod).intercept(new ElapsedRealtimeInterceptor());

            } catch (Throwable t) {
                // معالجة الخطأ بصمت لمنع توقف التطبيق
            }
        }
    }

    private static synchronized void initTimers() {
        if (isInitialized) return;
        startRealTimeMillis = System.currentTimeMillis();
        startFakeTimeMillis = startRealTimeMillis;

        startUptimeMillis = SystemClock.uptimeMillis();
        startFakeUptimeMillis = startUptimeMillis;

        startElapsedRealtime = SystemClock.elapsedRealtime();
        startFakeElapsedRealtime = startElapsedRealtime;
        isInitialized = true;
    }

    private static class CurrentTimeInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            long realTime = (long) chain.proceed();
            if (!isInitialized) initTimers();
            long elapsed = realTime - startRealTimeMillis;
            return startFakeTimeMillis + (long) (elapsed * SPEED_MULTIPLIER);
        }
    }

    private static class UptimeInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            long realUptime = (long) chain.proceed();
            if (!isInitialized) initTimers();
            long elapsed = realUptime - startUptimeMillis;
            return startFakeUptimeMillis + (long) (elapsed * SPEED_MULTIPLIER);
        }
    }

    private static class ElapsedRealtimeInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            long realElapsed = (long) chain.proceed();
            if (!isInitialized) initTimers();
            long elapsed = realElapsed - startElapsedRealtime;
            return startFakeElapsedRealtime + (long) (elapsed * SPEED_MULTIPLIER);
        }
    }
}
