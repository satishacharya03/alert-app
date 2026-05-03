# AlertNow 🔔

> Bypass silence. Reach them instantly.

A two-part emergency notification system:
- **Web Portal** (`web/`) — Anyone enters an AlertID + message → phone rings loudly
- **Android App** (`android/`) — Friends install it, register an ID, receive loud alerts even on silent

---

## Project Structure

```
alert-app/
├── .gitignore
├── README.md
├── alert-sas-c981d0cdd77f.json (gitignored — your local service account key)
├── build-android.bat       ← Android build script
│
├── web/                    ← 🌐 Website & Netlify Backend
│   ├── package.json        ← Installs deps for Netlify Function
│   ├── netlify.toml        ← Netlify build config (Base directory must be 'web')
│   ├── generate-config.js  ← Netlify build script (reads env vars → writes config.js)
│   ├── generate-config.bat ← Local Windows config generator
│   ├── index.html          ← Public sender page (with Download APK button)
│   ├── admin.html          ← Admin broadcast panel
│   ├── app.js              ← Frontend logic
│   ├── style.css           ← Styling
│   ├── config.js           ← AUTO-GENERATED (gitignored)
│   ├── .env                ← Your secrets (gitignored)
│   ├── .env.example        ← Template
│   └── netlify/
│       └── functions/
│           └── send-alert.js ← 🔒 Secure FCM v1 sender (free Netlify function)
│
└── android/                ← 📱 Android App (open in Android Studio)
```

---

## Quick Start

### Web (Netlify Deployment)

1. Push this repo to GitHub
2. Connect to [Netlify](https://netlify.com) → **New site from Git**
3. **CRITICAL:** Set the **Base directory** to `web` in Netlify's build settings.
4. Set **Environment Variables** in Netlify Dashboard:
   - All `FIREBASE_*` keys (from `web/.env.example`)
   - `FIREBASE_SERVICE_ACCOUNT` → paste the entire content of your JSON key
   - `ADMIN_USERNAME` / `ADMIN_PASSWORD`
5. Deploy — Netlify automatically runs `generate-config.js` and publishes your site.

### Android APK (Local Build)

1. Copy `google-services.json` from Firebase Console → `android/app/`
2. Double-click `build-android.bat` (or run it in your terminal)
3. APK is at `android/app/build/outputs/apk/debug/app-debug.apk`

### After Users Install the App

- They open the app → tap **Create My Alert ID** → get e.g. `HAWK-3821`
- They tap **Enable Silent Mode Bypass** once → grants DND override permission
- Anyone with their ID can now ring their phone from your website

---

## How Silent Mode Bypass Works

| Step | What happens |
|---|---|
| 1 | FCM data message arrives (`priority: high`) — wakes Android even in background |
| 2 | Sound plays on **ALARM** stream — unaffected by ringer/silent switch |
| 3 | Full-screen notification fires — appears above the lock screen |
| 4 | If DND permission granted — notification bypasses Do Not Disturb completely |
