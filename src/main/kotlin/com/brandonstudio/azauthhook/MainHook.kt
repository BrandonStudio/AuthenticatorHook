package com.brandonstudio.azauthhook

import com.brandonstudio.azauthhook.hook.PhaseHook
import com.brandonstudio.azauthhook.hook.UIManagerHook
import com.brandonstudio.azauthhook.hook.ValidationExecutorHook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET) return

        log("$TAG loaded for ${lpparam.packageName}")

        // Layer 1: Primary hook — intercept handleValidationFailure (single convergence point for all failure UI)
        ValidationExecutorHook.install(lpparam.classLoader)

        // Layer 2: Safety net — intercept ValidationUIManager display methods
        UIManagerHook.install(lpparam.classLoader)

        // Layer 3: Defense in depth — intercept phase check methods
        PhaseHook.install(lpparam.classLoader)
    }

    companion object {
        private const val TARGET = "com.azure.authenticator"
    }
}
