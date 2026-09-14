Yes, absolutely! You can build the `.aab` (Android App Bundle) with Gradle, and then use Google's official **`bundletool`** to generate the exact device-specific APKs from that `.aab`.

Here are the step-by-step instructions:

---

### Step 1: Build the `.aab` Bundle

Run this Gradle command (or select **Build → Build Bundle(s) / APK(s) → Build Bundle(s)** in Android Studio):

```bash
# For Release AAB
./gradlew :app:bundleRelease

# Or for Debug AAB (testing)
./gradlew :app:bundleDebug
```

Your generated `.aab` will be located at:
* **`app/build/outputs/bundle/release/app-release.aab`** (or `app-debug.aab`)

---

### Step 2: Generate APKs from the `.aab` using `bundletool`

Google provides `bundletool` (the exact same tool Play Store runs on its servers) to convert an `.aab` into APKs.

1. **Install `bundletool`** (if not already installed via Homebrew or downloaded from GitHub):
   ```bash
   brew install bundletool
   ```
   *(Or download `bundletool-all.jar` from [Google's GitHub repository](https://github.com/google/bundletool/releases))*

2. **Generate the APK Set (`.apks`) from the AAB**:

    * **Option A: Universal Standalone APK (single standalone installable `.apk`)**:
      ```bash
      bundletool build-apks \
        --bundle=app/build/outputs/bundle/release/app-release.aab \
        --output=app-release.apks \
        --mode=universal
      ```
      *Then simply extract the `.apks` file (it’s a zip) to get `universal.apk`*:
      ```bash
      unzip -p app-release.apks universal.apk > app-universal.apk
      ```

    * **Option B: Connected Device-Specific Split APKs (matching your connected phone)**:
      ```bash
      # Builds the split APKs optimized specifically for your connected test device
      bundletool build-apks \
        --connected-device \
        --bundle=app/build/outputs/bundle/release/app-release.aab \
        --output=device-app.apks
      ```

---

### Step 3: Install the Generated APKs to your Device

To install the `.apks` set generated from the AAB directly onto a connected phone:
```bash
bundletool install-apks --apks=device-app.apks
```
*(Or if you extracted `app-universal.apk`, simply run `adb install app-universal.apk`)*.