# AuthenticatorHook

XPosed module to bypass Root detection within Microsoft Authenticator (`com.azure.authenticator`).

## Background

Microsoft Authenticator ships with a proprietary device integrity SDK called **Bastion**. Unlike conventional root detection, Bastion employs a multi-layered detection architecture: parallel local validators (`validateAlpha/Bravo/Charlie`), a standalone detection engine built on a reflection cache system with runtime-decoded strings, and server-side feature flags (`IntegrationPhase`) that control the enforcement level. Because Bastion is entirely Java-based with no JNI native methods, it does not rely on traditional file or process scanning, rendering Zygisk-level root hiding tools like Shamiko ineffective.

For the full reverse engineering analysis, see [analysis.md](analysis.md).

## How It Works

Three-layer hook strategy (verified via Frida):

1. **Primary** — `ValidationExecutor.handleValidationFailure()` replaced with `dismissLoadingScreen()` + `onComplete.invoke()` + return. This is the single convergence point for all validation failure UI from both the sync path and cache path.
2. **Safety net** — `ValidationUIManager` display methods (`showBanner`, `showBlockingDialog`, `showValidationWarning`) replaced with `DO_NOTHING`.
3. **Defense in depth** — `IntegrationPhase.Companion` phase checks (`shouldBlockActions`, `shouldShowWarningUI`, `shouldWipeData`) replaced to return `false`.

## Requirements

- Xposed framework targeting libXposed API 100 and below (e.g., LSPosed / Vector)

## Installation

1. Download and install APK on device
2. LSPosed Manager → Modules → Enable AuthenticatorHook
3. Set scope to `com.azure.authenticator`
4. Force-stop Authenticator and relaunch

## Project Structure

```
src/main/kotlin/com/brandonstudio/azauthhook/
├── MainHook.kt                       # IXposedHookLoadPackage entry
├── Log.kt                            # Logging utility
└── hook/
    ├── ValidationExecutorHook.kt     # Primary hook (private method)
    ├── UIManagerHook.kt              # Safety net
    └── PhaseHook.kt                  # Defense in depth
```

## License
This project is licensed under [the MIT License](LICENSE).
