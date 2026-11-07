# Focus Timer

A calm Android app for studying and focused work: timers, Pomodoro, a stopwatch, alarms with wake-up missions, projects with study hours, background sounds and detailed history, in a soft "clay" design. Available in 10 languages, with light and dark themes.

The full feature list is in [docs/FEATURES.txt](docs/FEATURES.txt).

## Build

No Gradle. `build.ps1` builds the APK directly with the Android SDK tools.

Requirements:
- Android SDK with `build-tools\37.0.0` and `platforms\android-36` in `%LOCALAPPDATA%\Android\Sdk`
- JDK (`javac`, `jar`, `keytool` on PATH)

```powershell
.\build.ps1                    # -> TimerApp.apk
adb install -r TimerApp.apk
```

On the first build, the script creates a debug signing key (`debug.keystore`).

Min SDK 26 (Android 8.0), target SDK 36.

## Layout

```
app/src/main/   AndroidManifest.xml, java/, res/ (layouts, 10 languages, sounds, music)
build.ps1       build script
docs/           feature list
promo/          scripts that render the promo video and its music
```

## Credits

The background music recordings are free to use. Performers are credited in the app under Settings > Music credits.
