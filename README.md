# Notification Saver

Android app (Java, no third-party libraries, Android 10+) that saves every notification
shown in the status bar — from regular apps and from system apps — into a local database.

## Features
- Notification access (NotificationListenerService) captures all posted notifications
- Stores app name, package, title, text, expanded text, sub text, category, channel, time
- Marks notifications from system apps
- Also grabs notifications already in the shade when saving starts
- Search, long-press to delete one, clear all
- Export everything to `Download/notifications_export_<time>.csv`
- All data stays on the device (no internet permission)

## Get the APK

### Option A: GitHub Actions (no setup on your PC)
1. Create a new GitHub repository and upload this whole folder (keep `.github/`).
2. Open the **Actions** tab, run **Build APK** (it also runs on every push).
3. Download the `NotificationSaver-apk` artifact and unzip it to get `app-debug.apk`.

### Option B: Android Studio
Open this folder, wait for Gradle sync, then **Build > Build APK(s)**.
The file is `app/build/outputs/apk/debug/app-debug.apk`.

### Option C: command line
Needs JDK 17, the Android SDK (platform 34) and Gradle 8.9:

    gradle assembleDebug

## Install and enable
1. Copy the APK to your phone and install it (allow "install unknown apps" if asked).
2. Open the app and tap **Grant access**, then switch on **Notification Saver**.
3. Android 13 and newer: if the switch is greyed out, open
   Settings > Apps > Notification Saver > ⋮ (top right) > **Allow restricted settings**,
   then go back and enable notification access.
4. On phones with aggressive battery savers (Xiaomi, Oppo, Vivo, Samsung, etc.) set the app
   to **Unrestricted** battery use so Android doesn't stop the listener.

The debug APK is signed with the standard Android debug key, which is fine for personal use.
