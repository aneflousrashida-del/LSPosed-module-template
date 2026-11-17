package com.example.module;

import androidx.annotation.NonNull;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class MainModule extends XposedModule {

    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);

        if (param.getPackageName().equals("jp.konami.pesam")) {
            try {
                System.loadLibrary("shadowhook");
                System.loadLibrary("speedhack");
                log("Speedhack module loaded successfully!");
            } catch (Throwable t) {
                log("Failed to load native libraries: " + t.getMessage());
            }
        }
    }
}
