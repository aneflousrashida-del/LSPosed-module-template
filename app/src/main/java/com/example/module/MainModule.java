package com.example.module;

import android.annotation.SuppressLint;
import android.util.Log;
import androidx.annotation.NonNull;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class MainModule extends XposedModule {

    private static final String TAG = "Speedhack";

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);

        if (param.getPackageName().equals("jp.konami.pesam")) {
            try {
                String modulePath = getApplicationInfo().nativeLibraryDir;
                System.load(modulePath + "/libshadowhook.so");
                System.load(modulePath + "/libspeedhack.so");
                log(Log.INFO, TAG, "Native libraries loaded from: " + modulePath);
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Failed to load native libraries", t);
            }
        }
    }
}
