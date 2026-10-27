# DigitalTV

**DigitalTV** is an Android TV home-screen launcher with customizable wallpaper and app rows, an idle clock, and built-in prayer-time and weather cards. It is designed for Android TV and Google TV devices running Android 8 (API 26) or newer; device-specific firmware differences can affect individual features.

The prayer and weather feature is part of the launcher, not a separate prayer-times app. It uses Aladhan for prayer schedules and Open-Meteo for weather after you configure a location. The app explains which location details are sent when you set it up.

## Build locally

- JDK 17
- Android SDK Platform 34 and Build Tools 34.0.0

```powershell
cd launcher
.\gradlew.bat assembleDebug testDebugUnitTest
```

The launcher application ID remains `com.gathin.tvhome` to preserve compatibility with existing installs and TV setup scripts. The app’s displayed name is DigitalTV.

## GitHub releases and updates

The workflow in `.github/workflows/release-android.yml` builds the launcher and optional updater when a version tag such as `v1.0.0` is pushed. Before creating a public release, configure the repository Actions secrets for the Android signing keystore. Keep the keystore and its passwords private and backed up: future updates must use the same signing key.

The optional updater asks for the GitHub account and repository name, then checks the latest release. Android still asks before installing an update. The launcher itself does not silently update.

## About this project

DigitalTV is a community fork based on the MIT-licensed [CleanYourTV project](https://github.com/Gathin23/cleanyourtv). It keeps the upstream license and attribution while developing a separate launcher experience.
