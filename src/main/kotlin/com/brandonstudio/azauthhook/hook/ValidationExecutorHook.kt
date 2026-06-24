package com.brandonstudio.azauthhook.hook

import com.brandonstudio.azauthhook.TAG
import com.brandonstudio.azauthhook.log
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers

/**
 * Primary hook: intercept ValidationExecutor.handleValidationFailure()
 *
 * This is the single convergence point for both the sync path and cache path.
 * Replacement: dismissLoadingScreen() + invoke onComplete + return.
 * Prevents any BLOCK / WARN / WIPE UI from appearing.
 *
 * Uses XposedHelpers.findAndHookMethod which internally calls findMethodExact
 * (backed by getDeclaredMethods), so private methods are resolved correctly.
 */
object ValidationExecutorHook {

    private const val CLASS =
        "com.microsoft.authenticator.features.bastion.synccoordinator.internal.ValidationExecutor"

    fun install(classLoader: ClassLoader) {
        try {
            // Parameter types must be loaded dynamically from the target app's classLoader
            val displayModeClass = XposedHelpers.findClass(
                "com.microsoft.authenticator.core.validation.DisplayMode", classLoader
            )
            val function0Class = XposedHelpers.findClass(
                "kotlin.jvm.functions.Function0", classLoader
            )
            val integrationPhaseClass = XposedHelpers.findClass(
                "com.microsoft.authenticator.features.bastion.synccoordinator.IntegrationPhase",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                CLASS, classLoader,
                "handleValidationFailure",
                String::class.java,       // tenantId
                displayModeClass,         // displayMode
                String::class.java,       // source
                function0Class,           // onComplete (Function0<Unit>)
                integrationPhaseClass,    // maxPhase
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val executor = param.thisObject
                        val source = param.args[2] as? String ?: "?"
                        val displayMode = param.args[1]?.toString() ?: "?"

                        log("$TAG [BYPASS] handleValidationFailure intercepted (source=$source, mode=$displayMode)")

                        // 1. dismissLoadingScreen — prevent loading screen from getting stuck
                        try {
                            val uiManager = XposedHelpers.getObjectField(executor, "validationUIManager")
                            XposedHelpers.callMethod(uiManager, "dismissLoadingScreen")
                        } catch (e: Throwable) {
                            log("$TAG [BYPASS] dismissLoadingScreen failed: $e")
                        }

                        // 2. Invoke onComplete callback — notify caller that operation finished
                        try {
                            val onComplete = param.args[3]
                            if (onComplete != null) {
                                XposedHelpers.callMethod(onComplete, "invoke")
                            }
                        } catch (e: Throwable) {
                            log("$TAG [BYPASS] onComplete invoke failed: $e")
                        }

                        log("$TAG [BYPASS] handleValidationFailure bypassed")
                        return null // void method
                    }
                }
            )

            log("$TAG [HOOK] handleValidationFailure => BYPASS")
        } catch (e: Throwable) {
            log("$TAG [ERROR] ValidationExecutorHook install failed: $e")
        }
    }
}
