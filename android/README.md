# AlertNow Android Project

This is a ready-to-open **Android Studio** project for the AlertNow app.

## One thing you must do before building

1. Go to https://console.firebase.google.com  
2. Open your **alertnow** project → Project Settings → Add app → **Android**  
3. Package name: `com.alertnow`  
4. Download `google-services.json`  
5. **Copy that file into `app/`** (same folder as `app/build.gradle`)  

Without `google-services.json` the build will fail.

---

## How to open & build

1. Open **Android Studio**
2. File → **Open** → select the `android/` folder
3. Wait for Gradle sync to finish
4. Build → **Build Bundle(s)/APK(s)** → **Build APK(s)**
5. APK is at `app/build/outputs/apk/debug/app-debug.apk`

---

## Project structure

```
android/
├── build.gradle                   ← root Gradle (plugin declarations)
├── settings.gradle
└── app/
    ├── build.gradle               ← app dependencies (Firebase, Material)
    ├── google-services.json       ← YOU ADD THIS from Firebase Console
    └── src/main/
        ├── AndroidManifest.xml    ← all permissions + activity/service declarations
        ├── java/com/alertnow/
        │   ├── MainActivity.java          ← registration, DND, ID copy
        │   ├── MyFirebaseService.java     ← receives FCM → rings alarm → vibrates
        │   └── AlertFullScreenActivity.java ← big red screen on alert
        └── res/
            ├── layout/
            │   ├── activity_main.xml      ← home screen (create / created states)
            │   └── activity_alert.xml     ← full-screen alarm UI
            └── values/
                ├── strings.xml
                └── themes.xml
```

---

## How silent-mode bypass works

| Step | What happens |
|------|--------------|
| 1 | FCM message arrives (data-only, `priority: high`) — Android wakes the app even in background |
| 2 | `MyFirebaseService.ringAlarm()` plays audio on the **ALARM** stream — a separate volume channel unaffected by the ringer/silent switch |
| 3 | A full-screen-intent notification is posted — appears above the lock screen |
| 4 | `AlertFullScreenActivity` is launched — screen turns on, shows the red alert UI |
| 5 | If the user granted **Notification Policy Access** (DND access), the notification channel bypasses Do Not Disturb completely |

Users grant the DND permission by tapping the "Enable Silent Mode Bypass" button in the app (one time only).
