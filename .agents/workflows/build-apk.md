---
description: Build the E.M.M.A. AI APK and deliver it to the user's Desktop
---

# Build APK & Deliver to Desktop

This is the standard workflow for building and delivering the E.M.M.A. AI Android APK after any code changes.

## Prerequisites

- Android SDK is installed at: `C:\Users\Usuario\android-sdk`
- SDK path is configured in `local.properties`: `sdk.dir=C:\\Users\\Usuario\\android-sdk`
- Project root: `C:\Users\Usuario\.gemini\antigravity\scratch\E.M.M.A. Ai`

## Steps

// turbo-all

1. Stage and commit all changes:
```powershell
git add -A; git status
git commit -m "<descriptive message>"
```

2. Push to GitHub:
```powershell
git push origin main
```

3. Build the debug APK using Gradle:
```powershell
.\gradlew.bat assembleDebug
```
- The APK output is at: `app\build\outputs\apk\debug\app-debug.apk`

4. Copy the APK to the user's Desktop with a descriptive name:
```powershell
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" -Destination "C:\Users\Usuario\Desktop\EMMA_Ai_v6.0.0-debug.apk"
```

5. Confirm delivery to the user with:
   - Version name
   - What changed in this build
   - File location on Desktop

## Release Build (Signed APK / AAB)

For a production release:

1. Generate a keystore (one-time):
```powershell
keytool -genkey -v -keystore emma-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias emma
```

2. Create `keystore.properties` in project root:
```
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=emma
storeFile=../emma-release.jks
```

3. Build signed APK:
```powershell
.\gradlew.bat assembleRelease
```
Output: `app\build\outputs\apk\release\app-release.apk`

4. Build AAB for Play Store:
```powershell
.\gradlew.bat bundleRelease
```
Output: `app\build\outputs\bundle\release\app-release.aab`

## Notes

- Debug APK is ~194 MB (full dependencies, no minification)
- Release APK will be significantly smaller with R8 + resource shrinking
- Build time: ~20-30 seconds (incremental) or ~2-3 min (clean)
- Always deliver the APK to Desktop after ANY code change
