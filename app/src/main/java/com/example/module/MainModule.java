package com.example.module;

import androidx.annotation.NonNull;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class MainModule extends XposedModule {

    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        
        // التحقق من أن التطبيق المفتوح هو اللعبة المستهدفة
        if (param.getPackageName().equals("jp.konami.pesam")) {
            try {
                // تحميل مكتبة libspeedhack.so في ذاكرة اللعبة
                System.loadLibrary("speedhack");
            } catch (Throwable t) {
                log("Failed to load speedhack library: " + t.getMessage());
            }
        }
    }
}
