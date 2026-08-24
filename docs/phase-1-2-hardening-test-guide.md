# FocusGuard Phase 1/2 Hardening Test Guide

This document covers the Phase 1 emergency bypass hardening and Phase 2 psychological redesign changes, plus the recommended debug-to-release verification flow.

## Project Journey Log

This section tracks the audit-to-hardening journey so future work has context, not just a checklist.

### Starting Point

The project began this pass with a deep audit split into two parts:

- Part 1: Anti-bypass and architecture.
- Part 2: Psychology, UX, and roadmap.

The audit identified critical bypass vectors, architectural risks, and behavioral design weaknesses. The strongest confirmed risks were:

- Fixed friction timeout could be abused by waiting.
- Settings paths did not fully cover Clear Data, Force Stop, and Accessibility disable flows.
- Strict Mode used wall-clock time, which can be manipulated.
- Home button was immediately available in overlay.
- Failure injection could feel random and unfair.
- Messaging was repetitive and sometimes punitive.
- Reflection gate accepted meaningless short input.
- Pipeline was too predictable at lower levels.

### Audit Verification Notes

Before implementing, the audit claims were checked against the actual codebase.

Confirmed or mostly confirmed:

- `FrictionOrchestrator` used a hard session timeout.
- `FocusGuardPrefs` stored critical runtime state in SharedPreferences.
- `SettingsBlocker` had limited self-removal keyword coverage.
- Strict mode relied on `System.currentTimeMillis()`.
- `OverlayManager.resetUI()` made Home visible immediately.
- `FailureInjectionLayer` had high restart chance at higher levels.
- `PsychMessages` and layer message pools were small.
- `FrictionPipeline` was deterministic at low escalation levels.
- Reflection gate always denied access and only rejected blank text.

Partly stale or adjusted:

- A boot receiver already existed, so a duplicate `BootCompletedReceiver` was not added.
- The existing watchdog was preserved and supplemented with WorkManager.
- Some audit recommendations were best-effort only because Android does not let a normal app fully block Force Stop, Clear Data, ADB, root, or OEM process killing.

### Phase 1 Implementation Journey

Phase 1 focused on emergency bypass hardening.

Implemented:

- Replaced the fixed 2-minute friction timeout with an escalating timeout.
- Added `timeoutExploitCount`.
- Added timeout penalty behavior through `bypassPenalty`.
- Expanded SettingsBlocker keywords for Clear Data, Clear Storage, Manage Storage, Force Stop, Disable App, and related terms.
- Added Accessibility disable context detection.
- Reused existing `BootReceiver` instead of creating a duplicate receiver.
- Added `ServiceHealthWorker` as WorkManager-based service health backup.
- Scheduled the service-health worker from `FocusGuardApp`.
- Added watchdog rescheduling after boot.
- Added monotonic strict-mode timing via `SystemClock.elapsedRealtime()`.
- Kept wall-clock `blockEndTime` as backward-compatible fallback.
- Hid Home button by default.
- Added escalation-based Home reveal delay in normal friction mode.
- Kept Home hidden in Strict Mode.
- Reduced FailureInjection chance and skipped it on the first daily attempt.

Important Phase 1 decision:

- A duplicate boot receiver was intentionally avoided because `.service.BootReceiver` was already registered in `AndroidManifest.xml`.

### Phase 1 Verification Journey

Initial build attempts hit environment issues:

- Default Gradle home pointed at `C:\Users\CodexSandboxOffline\.gradle` and failed to create wrapper lock files.
- Repo-local Gradle home was used: `.gradle-home`.
- Android tooling wanted user Android config under the sandbox profile.
- Repo-local Android user home was used: `.android-home`.
- Missing dependencies required network access once.
- Kotlin daemon had access-denied issues, but Gradle fallback compilation succeeded.

Verified result:

- `assembleDebug` completed successfully.

### Phase 2 Implementation Journey

Phase 2 focused on psychological redesign and user-facing behavior.

Implemented:

- Rebuilt `PsychMessages` with larger supportive, data, urgent, and neutral pools.
- Added placeholder support for attempt count, streak days, exam name, days left, and saved minutes.
- Rotated message tone using day and attempt seed.
- Randomized non-first friction layers at all escalation levels where enough layers exist.
- Kept the first layer as a cognitive/PYQ task to preserve study value.
- Rewrote punitive copy into collaborative copy.
- Replaced wrong-answer copy with `Not quite - the answer is ...`.
- Replaced restart copy with habit-building language.
- Replaced settings penalty copy with protection-level language.
- Added focus streak state to `FocusGuardPrefs`.
- Added daily clean-day streak processing.
- Surfaced focus streak in overlay attempt info.
- Added relapse warning for first attempt after a 3+ day streak.
- Added 15-character minimum validation for reflection text input.

Additional Phase 2 fixes found during review:

- Restarts were originally still calling `escalationEngine.calculate()` again, which inflated `dailyAttemptCount` inside one blocked session.
- This was fixed so each blocked session counts once, even if the friction pipeline restarts.
- `PsychMessages.getDenialMessage(...)` was initially behind an `ifBlank` branch that never ran.
- This was fixed so contextual denial messages are actually used for non-exam-aligned reflection outcomes.

### Phase 2 Static Verification

Static checks confirmed:

- No remaining user-facing `Wrong.` strings in the reviewed blocker/friction paths.
- No remaining `Penalty added` string.
- No remaining `Still blocked` string.
- No remaining `Block limit reached` string.
- No remaining `Restarting from the beginning` string.
- `PsychMessages.getDenialMessage(...)` is called with contextual parameters.
- `escalationEngine.calculate()` is called once per friction session.

### Phase 2 Build Verification

`assembleDebug` was run after Phase 2.

Result:

- `BUILD SUCCESSFUL`

Warnings observed:

- Kotlin daemon access-denied warning, followed by fallback compilation.
- Deprecated Compose icon warnings.
- Some unused parameter warnings.
- One unnecessary safe-call warning.

None of these warnings blocked APK assembly.

### Current State Snapshot

Current state after Phase 1 and Phase 2:

- Debug build passes.
- APK path: `app/build/outputs/apk/debug/app-debug.apk`.
- Ready for real-device smoke testing.
- Release build should wait until debug smoke test passes.

### Decision Log

| Decision | Reason |
|---|---|
| Test debug build before release | Overlay/accessibility behavior needs fast iteration and logs. |
| Do not add duplicate boot receiver | Existing `BootReceiver` already handles boot actions. |
| Keep wall-clock strict end time as fallback | Backward compatibility for existing stored strict sessions. |
| Use WorkManager as secondary watchdog | Alarm watchdogs can be throttled or lost on OEM devices. |
| Keep first pipeline layer as PYQ | Every distraction attempt should create study value. |
| Do not promise absolute Force Stop/Clear Data prevention | Normal Android apps cannot guarantee that without device-owner/MDM control. |
| Count restarts inside one session as one attempt | Restart is part of the same distraction attempt, not a new user attempt. |
| Use debug APK for first smoke test | Faster install, easier logs, lower iteration cost. |

## Current Status

- Phase 1 emergency bypass hardening: implemented.
- Phase 2 psychological redesign: implemented.
- `assembleDebug`: passed.
- Next required step: real-device smoke testing with the debug APK.

## Why Test Debug First

Use the debug APK before making a release build because:

- Android overlay, accessibility, watchdog, and settings-screen behavior must be tested on a real phone.
- Debug builds are faster to install and iterate.
- Logs are easier to inspect if overlay or service behavior fails.
- Release should only be created after the debug smoke test passes.

Debug APK path:

```powershell
app/build/outputs/apk/debug/app-debug.apk
```

Install command:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Build Command

Use the repo-local Android and Gradle homes:

```powershell
$env:ANDROID_USER_HOME=(Resolve-Path '.android-home').Path
./gradlew --gradle-user-home .gradle-home --no-daemon assembleDebug
```

Known environment note:

- Kotlin daemon may fail with an access-denied warning in the sandbox/user environment.
- Gradle can fall back to non-daemon Kotlin compilation.
- If the final result says `BUILD SUCCESSFUL`, the APK was assembled successfully.

## Phase 1 Changes To Verify

### 1. Escalating Friction Timeout

Files:

- `app/src/main/java/com/focusguard/app/friction/FrictionOrchestrator.kt`
- `app/src/main/java/com/focusguard/app/persistence/FocusGuardPrefs.kt`

Expected behavior:

- Friction timeout is no longer a fixed 2 minutes.
- Timeout length escalates by attempt count.
- Each timeout exploit increases `timeoutExploitCount`.
- Each timeout exploit also increases `bypassPenalty`.

Manual test:

1. Open a blocked app.
2. Do nothing on the overlay until timeout.
3. Confirm the overlay says protection level increased.
4. Open the blocked app again.
5. Confirm the next session feels longer/harder.

### 2. Settings Self-Removal Protection

File:

- `app/src/main/java/com/focusguard/app/antibypass/SettingsBlocker.kt`

Expected behavior during opted-in Strict Mode with exit protection:

- FocusGuard blocks removal-related settings paths.
- Clear data, clear storage, manage storage, force stop, disable app, uninstall, and accessibility-disable contexts are treated as protection events.
- A bypass attempt increases protection level.

Manual test:

1. Enable Strict Mode and exit protection.
2. Go to Android Settings.
3. Try App info for FocusGuard.
4. Try Storage or Clear data screen.
5. Try Force stop screen.
6. Try Accessibility service screen.
7. Confirm FocusGuard forces Home and shows protection copy.

Important limitation:

- Normal Android apps cannot absolutely prevent force stop, clear data, ADB, root, or OEM-level process killing.
- This protection is best-effort unless the app is installed as device-owner or managed-device software.

### 3. Strict Mode Monotonic Clock

Files:

- `app/src/main/java/com/focusguard/app/persistence/FocusGuardPrefs.kt`
- `app/src/main/java/com/focusguard/app/data/settings/SettingsRepository.kt`

Expected behavior:

- Strict Mode uses `SystemClock.elapsedRealtime()` as the primary timer source.
- Changing the wall clock forward should not instantly end Strict Mode.
- Wall-clock `blockEndTime` remains as backward-compatible fallback.

Manual test:

1. Enable Strict Mode for a known duration.
2. Move device time forward manually.
3. Reopen a blocked app.
4. Confirm Strict Mode remains active.

### 4. Delayed Home Button

Files:

- `app/src/main/java/com/focusguard/app/overlay/OverlayManager.kt`
- `app/src/main/java/com/focusguard/app/friction/FrictionOrchestrator.kt`

Expected behavior:

- Home button starts hidden.
- It appears only after an escalation-based delay.
- In Strict Mode, Home button should remain hidden.

Manual test:

1. Open a blocked app.
2. Confirm Home button is not immediately visible.
3. Wait for the delay.
4. Confirm Home appears in normal friction mode.
5. Repeat in Strict Mode.
6. Confirm Home does not appear during Strict Mode.

### 5. Failure Injection Cap

File:

- `app/src/main/java/com/focusguard/app/friction/layers/FailureInjectionLayer.kt`

Expected behavior:

- Failure injection never triggers on the first attempt of the day.
- Failure chance is capped at 35%.
- Restart copy is less punitive.

Manual test:

1. On first distraction attempt of the day, verify failure injection does not restart the flow.
2. On later attempts, verify occasional restart is possible but not constant.

### 6. Service Restart Watchdog

Files:

- `app/src/main/java/com/focusguard/app/service/BootReceiver.kt`
- `app/src/main/java/com/focusguard/app/service/ServiceHealthWorker.kt`
- `app/src/main/java/com/focusguard/app/FocusGuardApp.kt`

Expected behavior:

- Existing `BootReceiver` handles boot restart.
- `ServiceHealthWorker` periodically checks service health.
- `WatchdogReceiver` is rescheduled after boot and worker checks.

Manual test:

1. Enable guard.
2. Reboot device.
3. Confirm foreground service returns if guard should be active.
4. Confirm blocked app detection still works after reboot.

## Phase 2 Changes To Verify

### 1. Expanded Message Pools

File:

- `app/src/main/java/com/focusguard/app/ui/PsychMessages.kt`

Expected behavior:

- Messages rotate across supportive, data, urgent, and neutral tones.
- Denial messages include attempt count, streak days, exam name, and days-left placeholders where relevant.
- Copy should feel collaborative, not punitive.

Manual test:

1. Trigger friction multiple times.
2. Confirm messages are not identical every attempt.
3. Confirm exam and streak data appears when available.

### 2. Randomized Pipeline

File:

- `app/src/main/java/com/focusguard/app/friction/FrictionPipeline.kt`

Expected behavior:

- Every attempt still starts with a cognitive task/PYQ.
- Non-first layers are shuffled when enough layers exist.
- Low escalation levels are less predictable than before.

Manual test:

1. Trigger multiple blocked attempts.
2. Compare layer order and wait/delay behavior.
3. Confirm the first meaningful step is still a PYQ.

### 3. Collaborative Copy

Files:

- `app/src/main/java/com/focusguard/app/friction/FrictionOrchestrator.kt`
- `app/src/main/java/com/focusguard/app/friction/layers/CognitiveTaskLayer.kt`
- `app/src/main/java/com/focusguard/app/detection/FocusedSurfaceBlocker.kt`
- `app/src/main/java/com/focusguard/app/antibypass/SettingsBlocker.kt`

Expected behavior:

- Wrong-answer copy says `Not quite - the answer is ...`.
- Restart copy says the user is building the habit.
- Settings protection copy says protection level increased, not penalty added.
- Denial copy is more supportive and context-aware.

Manual test:

1. Answer a PYQ incorrectly.
2. Trigger a restart.
3. Trigger settings protection.
4. Complete a normal friction session.
5. Confirm tone is collaborative.

### 4. Focus Streak Surfacing

Files:

- `app/src/main/java/com/focusguard/app/persistence/FocusGuardPrefs.kt`
- `app/src/main/java/com/focusguard/app/friction/EscalationEngine.kt`
- `app/src/main/java/com/focusguard/app/friction/FrictionOrchestrator.kt`

Expected behavior:

- A clean day is one or fewer distraction attempts.
- Streak updates when a new day is first processed.
- Overlay surfaces a focus streak when streak is at least 2 days.
- First attempt after a 3+ day streak shows relapse warning.

Manual test:

1. Seed or simulate `focusStreakDays >= 2`.
2. Open a blocked app.
3. Confirm streak text appears in attempt info.
4. Seed or simulate `focusStreakDays >= 3` and first attempt.
5. Confirm relapse warning appears.

### 5. Reflection Input Validation

File:

- `app/src/main/java/com/focusguard/app/friction/FrictionOrchestrator.kt`

Expected behavior:

- Reflection text responses require at least 15 characters.
- Short inputs show progress like `(7/15)`.

Manual test:

1. Reach reflection gate.
2. Enter `aaa`.
3. Confirm it is rejected.
4. Enter 15+ characters.
5. Confirm it proceeds.

## Release Build Gate

Do not create a release build until these are true:

- `assembleDebug` passes.
- Real-device smoke test passes.
- No crash during overlay display.
- Accessibility service remains responsive.
- Strict Mode clock-forward test passes.
- Settings protection test passes.
- Reboot recovery test passes.

After debug smoke test passes, create a release build:

```powershell
$env:ANDROID_USER_HOME=(Resolve-Path '.android-home').Path
./gradlew --gradle-user-home .gradle-home --no-daemon assembleRelease
```

Then install/test the release artifact separately. Release builds can behave differently because of signing, shrinking, manifest processing, and optimization.

## Known Caveats

- Force Stop cannot be fully prevented by a normal Android app.
- Clear Data cannot be fully prevented by a normal Android app.
- ADB/root bypasses are outside normal app control.
- OEM battery managers may still kill services.
- Strongest anti-bypass mode requires device-owner, managed-device, or MDM-style deployment.

## Recommended Next Phase

After debug and release smoke testing:

1. Split `MainActivity.kt` into smaller screens/navigation files.
2. Replace shared mutable overlay view with a state-driven renderer.
3. Tie `FrictionOrchestrator` coroutine scope to service lifecycle.
4. Enable Room schema export and add migration tests.
5. Replace `SimpleDateFormat` with `java.time.LocalDate`.
