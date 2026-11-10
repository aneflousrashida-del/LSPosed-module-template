package com.example.module;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.util.List;

@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
public class MainModule extends XposedModule {

    private static final float SPEED_MULTIPLIER = 5000.0f;
    private static final float SCALE_MULTIPLIER = 50.0f;

    private static long startRealTimeMillis = 0;
    private static long startFakeTimeMillis = 0;
    private static long startUptimeMillis = 0;
    private static long startFakeUptimeMillis = 0;
    private static long startElapsedRealtime = 0;
    private static long startFakeElapsedRealtime = 0;
    private static boolean isInitialized = false;

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
                Method currentTimeMethod = System.class.getDeclaredMethod("currentTimeMillis");
                hook(currentTimeMethod).intercept(new CurrentTimeInterceptor());

                Method uptimeMethod = SystemClock.class.getDeclaredMethod("uptimeMillis");
                hook(uptimeMethod).intercept(new UptimeInterceptor());

                Method elapsedMethod = SystemClock.class.getDeclaredMethod("elapsedRealtime");
                hook(elapsedMethod).intercept(new ElapsedRealtimeInterceptor());

                try {
                    Method setScaleXMethod = View.class.getMethod("setScaleX", float.class);
                    hook(setScaleXMethod).intercept(new ScaleXInterceptor());

                    Method setScaleYMethod = View.class.getMethod("setScaleY", float.class);
                    hook(setScaleYMethod).intercept(new ScaleYInterceptor());
                    
                    Method setScaleZMethod = View.class.getMethod("setScaleZ", float.class);
                    hook(setScaleZMethod).intercept(new ScaleZInterceptor());
                } catch (Throwable ignored) {}

                try {
                    Method setFrictionMethod = View.class.getMethod("setFriction", float.class);
                    hook(setFrictionMethod).intercept(new ZeroFrictionInterceptor());
                } catch (Throwable ignored) {}

                try {
                    Method setTranslationXMethod = View.class.getMethod("setTranslationX", float.class);
                    hook(setTranslationXMethod).intercept(new ZeroTranslationXInterceptor());

                    Method setTranslationYMethod = View.class.getMethod("setTranslationY", float.class);
                    hook(setTranslationYMethod).intercept(new ZeroTranslationYInterceptor());
                    
                    Method setTranslationZMethod = View.class.getMethod("setTranslationZ", float.class);
                    hook(setTranslationZMethod).intercept(new ZeroTranslationZInterceptor());
                } catch (Throwable ignored) {}

                try {
                    Method dispatchTouchMethod = View.class.getMethod("dispatchTouchEvent", MotionEvent.class);
                    hook(dispatchTouchMethod).intercept(new InstantTouchInterceptor());
                } catch (Throwable ignored) {}

                try {
                    Method requestFocusMethod = View.class.getMethod("requestFocus");
                    hook(requestFocusMethod).intercept(new AutoBlockFocusInterceptor());

                    Method setFocusableMethod = View.class.getMethod("setFocusable", boolean.class);
                    hook(setFocusableMethod).intercept(new AutoBlockFocusableInterceptor());
                } catch (Throwable ignored) {}

                try {
                    Method clearAnimMethod = View.class.getMethod("clearAnimation");
                    hook(clearAnimMethod).intercept(new InstantAnimationInterceptor());
                } catch (Throwable ignored) {}

                try {
                    Method setFlagsMethod = Window.class.getMethod("setFlags", int.class, int.class);
                    hook(setFlagsMethod).intercept(new MaxPerformanceFlagsInterceptor());
                } catch (Throwable ignored) {}

            } catch (Throwable t) {
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

    private static class ScaleXInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            float newScale = SCALE_MULTIPLIER;
            List<Object> args = chain.getArgs();
            if (args != null && !args.isEmpty() && args.get(0) instanceof Float) {
                newScale = ((Float) args.get(0)) * SCALE_MULTIPLIER;
            }
            return chain.proceed(new Object[]{newScale});
        }
    }

    private static class ScaleYInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            float newScale = SCALE_MULTIPLIER;
            List<Object> args = chain.getArgs();
            if (args != null && !args.isEmpty() && args.get(0) instanceof Float) {
                newScale = ((Float) args.get(0)) * SCALE_MULTIPLIER;
            }
            return chain.proceed(new Object[]{newScale});
        }
    }

    private static class ScaleZInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            float newScale = SCALE_MULTIPLIER;
            List<Object> args = chain.getArgs();
            if (args != null && !args.isEmpty() && args.get(0) instanceof Float) {
                newScale = ((Float) args.get(0)) * SCALE_MULTIPLIER;
            }
            return chain.proceed(new Object[]{newScale});
        }
    }

    private static class ZeroFrictionInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            return chain.proceed(new Object[]{0.0f});
        }
    }

    private static class ZeroTranslationXInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            return chain.proceed(new Object[]{0.0f});
        }
    }

    private static class ZeroTranslationYInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            return chain.proceed(new Object[]{0.0f});
        }
    }

    private static class ZeroTranslationZInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            return chain.proceed(new Object[]{0.0f});
        }
    }

    private static class InstantTouchInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            Object result = chain.proceed(chain.getArgs().toArray());
            if (chain.getThisObject() instanceof View) {
                View view = (View) chain.getThisObject();
                view.invalidate();
            }
            return result;
        }
    }

    private static class AutoBlockFocusInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            return chain.proceed(chain.getArgs().toArray());
        }
    }

    private static class AutoBlockFocusableInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            return chain.proceed(new Object[]{true});
        }
    }

    private static class InstantAnimationInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            return chain.proceed(chain.getArgs().toArray());
        }
    }

    private static class MaxPerformanceFlagsInterceptor implements XposedInterface.Hooker {
        @NonNull
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            Object[] arguments = chain.getArgs().toArray();
            if (arguments.length > 0 && arguments[0] instanceof Integer) {
                int flags = (int) arguments[0];
                flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
                arguments[0] = flags;
            }
            return chain.proceed(arguments);
        }
    }
}
