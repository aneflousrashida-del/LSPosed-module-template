package com.example.module;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.view.View;
import androidx.annotation.NonNull;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.util.Collections;

@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
public class MainModule extends XposedModule {

    // معامل السرعة المستقر والمناسب للعب أونلاين
    private static final float SPEED_MULTIPLIER = 500.0f;
    
    // معامل تكبير حجم المجسم ومساحة التصادم محلياً (تم التعديل إلى ضعف الحجم 2.0x)
    private static final float SCALE_MULTIPLIER = 2.0f;

    private static long startRealTimeMillis = 0;
    private static long startFakeTimeMillis = 0;
    private static long startUptimeMillis = 0;
    private static long startFakeUptimeMillis = 0;
    private static long startElapsedRealtime = 0;
    private static long startFakeElapsedRealtime = 0;
    private static boolean isInitialized = false;

    // تم ترك الـ Constructor فارغاً تماماً لتجاوز شرط المترجم بنجاح
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
                // 1. تثبيت الـ Hooks الخاصة بالسرعة وتعديل الوقت المحلي
                Method currentTimeMethod = System.class.getDeclaredMethod("currentTimeMillis");
                hook(currentTimeMethod).intercept(new CurrentTimeInterceptor());

                Method uptimeMethod = SystemClock.class.getDeclaredMethod("uptimeMillis");
                hook(uptimeMethod).intercept(new UptimeInterceptor());

                Method elapsedMethod = SystemClock.class.getDeclaredMethod("elapsedRealtime");
                hook(elapsedMethod).intercept(new ElapsedRealtimeInterceptor());

                // =======================================================
                // 2. خدعة التكبير (Scale Hack) المباشرة عبر الجافا
                // =======================================================
                try {
                    Method setScaleXMethod = View.class.getMethod("setScaleX", float.class);
                    hook(setScaleXMethod).intercept(new ScaleXInterceptor());

                    Method setScaleYMethod = View.class.getMethod("setScaleY", float.class);
                    hook(setScaleYMethod).intercept(new ScaleYInterceptor());
                    
                    Method setScaleZMethod = View.class.getMethod("setScaleZ", float.class);
                    hook(setScaleZMethod).intercept(new ScaleZInterceptor());
                } catch (Throwable ignored) {}

                // =======================================================
                // 3. خدعة الصلابة ومنع التعثر والارتداد (Solid Physical / Anti-Stun)
                // =======================================================
                
                // إلغاء قوة الاحتكاك أو التباطؤ الناتج عن الاصطدام
                try {
                    Method setFrictionMethod = View.class.getMethod("setFriction", float.class);
                    hook(setFrictionMethod).intercept(new ZeroFrictionInterceptor());
                } catch (Throwable ignored) {}

                // تصفير دوال استقبال قوى الارتداد (Translation) الناتجة عن اصطدام المدافعين بك
                try {
                    Method setTranslationXMethod = View.class.getMethod("setTranslationX", float.class);
                    hook(setTranslationXMethod).intercept(new ZeroTranslationXInterceptor());

                    Method setTranslationYMethod = View.class.getMethod("setTranslationY", float.class);
                    hook(setTranslationYMethod).intercept(new ZeroTranslationYInterceptor());
                    
                    Method setTranslationZMethod = View.class.getMethod("setTranslationZ", float.class);
                    hook(setTranslationZMethod).intercept(new ZeroTranslationZInterceptor());
                } catch (Throwable ignored) {}

            } catch (Throwable t) {
                // معالجة صامتة لمنع انهيار اللعبة أثناء التشغيل
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

    // =======================================================
    // فئات اعتراض الوقت (Time Interceptors)
    // =======================================================
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

    // =======================================================
    // فئات اعتراض الحجم لتكبير كبسولة التصادم (Scale Interceptors)
    // =======================================================
    private static class ScaleXInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            float newScale = SCALE_MULTIPLIER;
            if (chain.getArgs() != null && chain.getArgs().size() > 0 && chain.getArgs().get(0) instanceof Float) {
                newScale = ((Float) chain.getArgs().get(0)) * SCALE_MULTIPLIER;
            }
            return chain.proceed(Collections.singletonList(newScale));
        }
    }

    private static class ScaleYInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            float newScale = SCALE_MULTIPLIER;
            if (chain.getArgs() != null && chain.getArgs().size() > 0 && chain.getArgs().get(0) instanceof Float) {
                newScale = ((Float) chain.getArgs().get(0)) * SCALE_MULTIPLIER;
            }
            return chain.proceed(Collections.singletonList(newScale));
        }
    }

    private static class ScaleZInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            float newScale = SCALE_MULTIPLIER;
            if (chain.getArgs() != null && chain.getArgs().size() > 0 && chain.getArgs().get(0) instanceof Float) {
                newScale = ((Float) chain.getArgs().get(0)) * SCALE_MULTIPLIER;
            }
            return chain.proceed(Collections.singletonList(newScale));
        }
    }

    // =======================================================
    // فئات اعتراض قوى الالتحام والارتداد (Anti-Stun Interceptors)
    // =======================================================
    private static class ZeroFrictionInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            return chain.proceed(Collections.singletonList(0.0f));
        }
    }

    private static class ZeroTranslationXInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            return chain.proceed(Collections.singletonList(0.0f));
        }
    }

    private static class ZeroTranslationYInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            return chain.proceed(Collections.singletonList(0.0f));
        }
    }

    private static class ZeroTranslationZInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            return chain.proceed(Collections.singletonList(0.0f));
        }
    }
}
