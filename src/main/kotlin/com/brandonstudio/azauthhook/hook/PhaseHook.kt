package com.brandonstudio.azauthhook.hook

import com.brandonstudio.azauthhook.TAG
import com.brandonstudio.azauthhook.log
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers

/**
 * Defense in depth: intercept IntegrationPhase.Companion phase check methods.
 *
 * Ensures other parts of the app that check the phase also see "no issue".
 * shouldBlockActions / shouldShowWarningUI / shouldWipeData all return false.
 *
 * Note: these methods are defined on the Companion class (IntegrationPhase$Companion),
 * not on the main IntegrationPhase class.
 */
object PhaseHook {

    private const val COMPANION_CLASS =
        "com.microsoft.authenticator.features.bastion.synccoordinator.IntegrationPhase\$Companion"

    fun install(classLoader: ClassLoader) {
        val clazz = XposedHelpers.findClass(COMPANION_CLASS, classLoader)
        val returnFalse = XC_MethodReplacement.returnConstant(false)

        for (name in listOf("shouldBlockActions", "shouldShowWarningUI", "shouldWipeData")) {
            try {
                XposedHelpers.findAndHookMethod(clazz, name, returnFalse)
                log("$TAG [HOOK] $name => false")
            } catch (e: Throwable) {
                log("$TAG [WARN] $name hook failed: $e")
            }
        }
    }
}
