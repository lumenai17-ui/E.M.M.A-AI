---
description: Build the Bee-Movil APK and deliver it to the user's Desktop
---

# Build APK & Deliver to Desktop

This is the standard workflow for building and delivering the Bee-Movil Android APK after any code changes.

## Prerequisites

- Android SDK is installed at: `C:\Users\Usuario\android-sdk`
- SDK path is configured in `local.properties`: `sdk.dir=C:\\Users\\Usuario\\android-sdk`
- Project root: `C:\Users\Usuario\.gemini\antigravity\scratch\bee-movil-native`
- **IMPORTANT**: The real project is `bee-movil-native`, NOT `bee-movil` (which is an abandoned prototype)

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
- Build logs can be saved to `build_output.txt` if needed

4. Copy the APK to the user's Desktop with a descriptive name:
```powershell
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" -Destination "C:\Users\Usuario\Desktop\BeeMovil-<version>.apk"
```
- Use the current version tag (e.g., `v4.2.5`, `v4.3.0`)
- The user transfers this APK to their phone for installation

5. Confirm delivery to the user with:
   - Version name
   - What changed in this build
   - File location on Desktop

## Notes

- The APK is ~135 MB (debug build with all dependencies)
- Build time is ~20-30 seconds (incremental) or ~2-3 min (clean)
- Always deliver the APK to Desktop after ANY code change — the user expects this
