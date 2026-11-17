package com.example.module;

import android.util.Log;
import androidx.annotation.NonNull;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class MainModule extends XposedModule {

    private static final String TAG = "Speedhack";

    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);

        if (param.getPackageName().equals("jp.konami.pesam")) {
            try {
                System.loadLibrary("shadowhook");
                System.loadLibrary("speedhack");
                log(Log.INFO, TAG, "Speedhack module loaded successfully!");
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Failed to load native libraries", t);
            }
        }
    }
}
