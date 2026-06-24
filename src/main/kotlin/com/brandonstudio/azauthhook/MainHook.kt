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

        // Each layer is independently guarded so one failure does not disable the rest

        // Layer 1: Primary hook — intercept handleValidationFailure (single convergence point for all failure UI)
        try {
            ValidationExecutorHook.install(lpparam.classLoader)
        } catch (e: Throwable) {
            log("$TAG [ERROR] Layer 1 (ValidationExecutorHook) failed: $e")
        }

        // Layer 2: Safety net — intercept ValidationUIManager display methods
        try {
            UIManagerHook.install(lpparam.classLoader)
        } catch (e: Throwable) {
            log("$TAG [ERROR] Layer 2 (UIManagerHook) failed: $e")
        }

        // Layer 3: Defense in depth — intercept phase check methods
        try {
            PhaseHook.install(lpparam.classLoader)
        } catch (e: Throwable) {
            log("$TAG [ERROR] Layer 3 (PhaseHook) failed: $e")
        }
    }

    companion object {
        private const val TARGET = "com.azure.authenticator"
    }
}
