package com.brandonstudio.azauthhook.hook

import com.brandonstudio.azauthhook.TAG
import com.brandonstudio.azauthhook.log
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers

/**
 * Safety net: intercept all ValidationUIManager display methods.
 *
 * Even if some code path bypasses handleValidationFailure,
 * these hooks prevent any UI from showing.
 */
object UIManagerHook {

    private const val CLASS =
        "com.microsoft.authenticator.features.bastion.ValidationUIManager"

    fun install(classLoader: ClassLoader) {
        try {
            val clazz = XposedHelpers.findClass(CLASS, classLoader)
            val noop = XC_MethodReplacement.DO_NOTHING

            // showBanner(IntegrationPhase)
            try {
                val phaseClass = XposedHelpers.findClass(
                    "com.microsoft.authenticator.features.bastion.synccoordinator.IntegrationPhase",
                    classLoader
                )
                XposedHelpers.findAndHookMethod(clazz, "showBanner", phaseClass, noop)
                log("$TAG [HOOK] showBanner => no-op")
            } catch (e: Throwable) {
                log("$TAG [WARN] showBanner hook failed: $e")
            }

            // showBlockingDialog(DisplayMode)
            try {
                val dmClass = XposedHelpers.findClass(
                    "com.microsoft.authenticator.core.validation.DisplayMode", classLoader
                )
                XposedHelpers.findAndHookMethod(clazz, "showBlockingDialog", dmClass, noop)
                log("$TAG [HOOK] showBlockingDialog => no-op")
            } catch (e: Throwable) {
                log("$TAG [WARN] showBlockingDialog hook failed: $e")
            }

            // showValidationWarning(DisplayMode)
            try {
                val dmClass = XposedHelpers.findClass(
                    "com.microsoft.authenticator.core.validation.DisplayMode", classLoader
                )
                XposedHelpers.findAndHookMethod(clazz, "showValidationWarning", dmClass, noop)
                log("$TAG [HOOK] showValidationWarning(DisplayMode) => no-op")
            } catch (e: Throwable) {
                log("$TAG [WARN] showValidationWarning(DisplayMode) hook failed: $e")
            }

            // showValidationWarning(DisplayMode, Function0) — overload with callback
            // Must invoke the Function0 callback before returning, otherwise callers
            // waiting for completion may get stuck (e.g. loading state never dismissed)
            try {
                val dmClass = XposedHelpers.findClass(
                    "com.microsoft.authenticator.core.validation.DisplayMode", classLoader
                )
                val fn0Class = XposedHelpers.findClass(
                    "kotlin.jvm.functions.Function0", classLoader
                )
                XposedHelpers.findAndHookMethod(
                    clazz, "showValidationWarning", dmClass, fn0Class,
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any? {
                            val callback = param.args[1]
                            if (callback != null) {
                                try {
                                    XposedHelpers.callMethod(callback, "invoke")
                                } catch (e: Throwable) {
                                    log("$TAG [WARN] showValidationWarning callback invoke failed: $e")
                                }
                            }
                            return null
                        }
                    }
                )
                log("$TAG [HOOK] showValidationWarning(DisplayMode, Function0) => invoke callback + return")
            } catch (e: Throwable) {
                log("$TAG [WARN] showValidationWarning(DisplayMode, Function0) hook failed: $e")
            }
        } catch (e: Throwable) {
            log("$TAG [ERROR] UIManagerHook install failed: $e")
        }
    }
}
