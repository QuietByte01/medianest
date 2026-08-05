# Implementation Plan - Fix Tablet Installation

If the app installs on your phone but not your tablet (both on Android 16), the issue is likely **Device Filtering** due to hardware requirements or a **Signature Conflict** on the tablet specifically.

## Analysis
- **API Level**: We will keep it at **36** as requested.
- **Signing**: The `debug.keystore` file is missing from the project root. Restoring the `debugConfig` in `build.gradle.kts` will cause the build to fail.
- **Hardware**: The `RECORD_AUDIO` permission implicitly requires a microphone. If the tablet lacks certain microphone hardware features, it might be filtered.
- **Screens**: Explicitly declaring support for tablet screens is a best practice to avoid system-level filtering.

## Proposed Changes

### [app]

#### [MODIFY] [AndroidManifest.xml](file:///Users/sachin/Desktop/Lab/VibeCoded/medianest/app/src/main/AndroidManifest.xml)
- Add `<uses-feature android:name="android.hardware.microphone" android:required="false" />`
- Add `<uses-feature android:name="android.hardware.telephony" android:required="false" />`
- Add `<supports-screens android:xlargeScreens="true" android:largeScreens="true" />`

## Verification & Recovery Steps

### 1. Manual Uninstall (CRITICAL)
> [!IMPORTANT]
> To rule out signature conflicts, please **manually uninstall** MediaNest from the tablet before trying to install the new build. Since the `debug.keystore` is missing, we are using the default Android debug key now, which will conflict with any old installation.

### 2. Automated Build
- Run `./gradlew assembleDebug` to ensure the manifest changes are valid and the build completes.

### 3. Deployment
- Attempt to install the new build on the tablet via Android Studio or ADB.

**Shall I proceed with the manifest updates?**
