# MediaNest Google Play Store Permission Declarations & Justifications

This guide provides the exact copy-paste text for Google Play Console permission declaration forms under **App Content > Sensitive App Permissions**.

---

## 1. All Files Access Permission Declaration (`MANAGE_EXTERNAL_STORAGE`)

When uploading MediaNest, Google Play Console will prompt you to complete the **All Files Access Declaration Form**.

### Play Console Form Answers (Copy & Paste):

* **Core Feature Category:** Select **Media Management** or **File Manager**.
* **Reason / Justification Text:**
  > MediaNest is a local media player and gallery application that includes a Hidden Media & Folder Management feature. Standard Android MediaStore APIs automatically filter out and exclude files stored in `.nomedia` directories and hidden dot-folders (e.g., `.vault`, `.private`, `.gallery`). Full Storage Access (`MANAGE_EXTERNAL_STORAGE`) is strictly required so users can scan, organize, view, and play their local media files stored in hidden directories across external storage.

* **Video Demonstration Link (Mandatory by Google):**
  Create a short 1-minute unlisted YouTube video or Google Drive link demonstrating:
  1. Opening MediaNest.
  2. Navigating to Hidden / Excluded Folders in the app.
  3. Showing MediaNest discovering and playing a video/photo stored inside a `.nomedia` or dot-folder on the device.

---

## 2. Foreground Service Declaration (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`)

Under **App Content > Foreground Service Permissions**:

* **Foreground Service Type:** Select **Media Playback**.
* **Justification Text:**
  > MediaNest uses a Foreground Service with mediaPlayback type to allow continuous background audio/video playback and floating Picture-in-Picture (PiP) window controls while the user interacts with other applications or turns off their screen.

---

## 3. Financial / Monetization Declaration

* **In-app Advertising:** If AdMob is enabled, declare *"Yes, my app contains ads"*.
* **Data Safety Questionnaire:**
  - **Does your app collect or share data?** Yes (Firebase Analytics collects anonymous device model, OS version, and crash diagnostic metrics).
  - **Is data encrypted in transit?** Yes (HTTPS/TLS).
  - **Can users request data deletion?** Yes (Users can clear local app cache/data or opt-out in Settings > Telemetry & Analytics).

---

## 4. App Target & Build Summary

- **Package Name:** `com.medianest.app`
- **Target SDK:** `36` (Android 16 - fully compliant with Google Play target API rules)
- **Compile SDK:** `37`
- **Minimum SDK:** `30` (Android 11+)
- **Release Bundle Location:** `app/build/outputs/bundle/release/app-release.aab`
- **Privacy Policy URL:** `https://QuietByte01.github.io/medianest/`
