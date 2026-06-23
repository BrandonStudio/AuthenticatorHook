# Bastion Reverse Engineering Analysis

## Background

Microsoft Authenticator includes a proprietary SDK called **Bastion** for device integrity verification. Unlike traditional root detection that scans for su binaries or Magisk artifacts, Bastion uses a multi-layered architecture:

1. **Local validators** — Three methods (`validateAlpha`, `validateBravo`, `validateCharlie`) running in parallel as coroutines on the main and IO threads.
2. **Detection engine** — A standalone engine (`testResponse$71ca5ac2`) built on a reflection cache system. All API calls are routed through `accessgetCachedResultcp(int)` → Map lookup → `Method.invoke()`. All strings are decoded at runtime, making static analysis infeasible. This engine operates independently of the three validators above.
3. **Server-side feature flags** — The enforcement level is controlled by `IntegrationPhase`, a value delivered via the server-side `SyncCoordinatorIntegrationPhase` feature flag. This determines what happens when an integrity issue is detected: audit only, show warning, block actions, or wipe data.

Bastion is entirely Java-based with no JNI native methods. Because it does not use traditional file or process scanning, Zygisk-level root hiding tools like Shamiko cannot mask its detection.

## Analysis Process

### Phase 1: Frida Verification — Initial Hook Targets

Created `bastion_verify.js` in OBSERVE mode, hooking `validateAlpha/Bravo/Charlie`, `testResponse`, `getCachedResult`, `AtomicReference.set`, and `ValidationCache.setCheckResult`.

Results:
- `validateAlpha() => true` (environment passed)
- `validateBravo() => true` (environment passed)
- `validateCharlie() => false` (issue detected)
- `AtomicReference.set(true)` — final result is true
- `ValidationCache.setCheckResult(true)` — cached as true

### Phase 2: BYPASS_MODE — Validate Hooks Are Ineffective

Forced all three validate methods to return `true`. The BLOCK banner still appeared:

```
Validation completed: result=true (553ms)
Handling validation failure, phase=BLOCK
Updated banner state to show warning (phase=BLOCK)
```

**Conclusion:** The `testResponse$71ca5ac2` engine is independent. Even with all three validators returning "passed", the engine still detects root through other mechanisms. The validate methods are not the correct hook targets.

### Phase 3: Smali Analysis — Decision Chain Reconstruction

Decompiled `ValidationExecutor` and `ValidationUIManager` smali to reconstruct the full flow:

```
executeValidation()
  ├── [Cache path] cache.getCheckResult() != null → handleCachedResult()
  │     └── true → handleValidationFailure()
  └── [Sync path] performSyncValidation() → testResponse callback(result)
        └── true → handleValidationFailure()

handleValidationFailure()
  ├── getCurrentPhase() → server-side feature flag (3=BLOCK)
  └── switch(phase):
        ├── AUDIT (1) → telemetry only
        ├── WARN  (2) → showValidationWarning()
        ├── BLOCK (3) → showBlockingDialog()
        └── WIPE  (4) → wipePhaseHandler
```

`handleValidationFailure` is the single convergence point for all failure UI.

### Phase 4: Frida v2 — Correct Hook Points Verified

Created `bastion_bypass_v2.js` with three-layer hooks:
- **Primary:** `handleValidationFailure` → `dismissLoadingScreen()` + return
- **Safety net:** `showBanner` / `showBlockingDialog` → no-op
- **Defense in depth:** `shouldBlockActions` / `shouldShowWarningUI` / `shouldWipeData` → false

Result: 4 calls intercepted successfully, covering all trigger paths (AppLaunch, AccountListOnCreate, L2EntraPage) and all display modes (BANNER, DIALOG). **Banner did not appear. Bypass confirmed.**

## IntegrationPhase Enum

| Enum | value | Behavior |
|------|-------|----------|
| AUDIT | 1 | Telemetry only, no UI |
| WARN | 2 | Show warning UI |
| BLOCK | 3 | Show blocking UI (banner / dialog) |
| WIPE | 4 | Wipe application data |

Current server-side value is 3 (BLOCK). This module does not modify the flag; it prevents the enforcement from taking effect.

## Key Class Paths

| Class | Fully Qualified Name |
|-------|---------------------|
| ValidationExecutor | `com.microsoft.authenticator.features.bastion.synccoordinator.internal.ValidationExecutor` |
| ValidationUIManager | `com.microsoft.authenticator.features.bastion.ValidationUIManager` |
| IntegrationPhase | `com.microsoft.authenticator.features.bastion.synccoordinator.IntegrationPhase` |
| IntegrationPhase.Companion | `com.microsoft.authenticator.features.bastion.synccoordinator.IntegrationPhase$Companion` |
| ValidationCache | `com.microsoft.authenticator.features.bastion.synccoordinator.internal.ValidationCache` |
| DisplayMode | `com.microsoft.authenticator.core.validation.DisplayMode` |

## Caveats

1. `handleValidationFailure` is a **private** method — `findAndHookMethod` does not search private methods. Must use `getDeclaredMethod` + `XposedBridge.hookMethod`.
2. The replacement implementation **must call `dismissLoadingScreen()`** — otherwise the loading screen will remain stuck.
3. `IntegrationPhase.Companion` methods live on the Companion class, not the main class. The hook target class name must include `$Companion`.
4. In Frida, `IntegrationPhase.AUDIT.value` returns the `int` field (1), not the enum object, causing NPE. This is not an issue in LSPosed (Java type system handles it correctly).
