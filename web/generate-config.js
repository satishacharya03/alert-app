/**
 * generate-config.js
 *
 * Runs on Netlify's build server (Node.js is always available there).
 * Reads secrets from Netlify Environment Variables → writes web/config.js
 *
 * Keys are NEVER in git. They live in:
 *   Netlify Dashboard → Site → Site Settings → Environment Variables
 *
 * Locally: run  node generate-config.js  after setting env vars,
 *          OR just use  generate-config.bat  which calls PowerShell.
 */

const fs   = require('fs');
const path = require('path');

// ── Read from process.env (set by Netlify Dashboard or local shell) ──
const required = [
    'FIREBASE_API_KEY',
    'FIREBASE_AUTH_DOMAIN',
    'FIREBASE_DATABASE_URL',
    'FIREBASE_PROJECT_ID',
    'FIREBASE_STORAGE_BUCKET',
    'FIREBASE_MESSAGING_SENDER_ID',
    'FIREBASE_APP_ID',
    'FCM_SERVER_KEY',
    'ADMIN_USERNAME',
    'ADMIN_PASSWORD',
];

const missing = required.filter(k => !process.env[k]);
if (missing.length) {
    console.error('❌  Missing environment variables:');
    missing.forEach(k => console.error('    ' + k));
    console.error('\nSet them in Netlify Dashboard → Site Settings → Environment Variables');
    process.exit(1);
}

const config = `/**
 * config.js — AlertNow Web Config
 * AUTO-GENERATED at build time — DO NOT COMMIT (gitignored)
 */
window.ALERT_CONFIG = {
    firebase: {
        apiKey:            "${process.env.FIREBASE_API_KEY}",
        authDomain:        "${process.env.FIREBASE_AUTH_DOMAIN}",
        databaseURL:       "${process.env.FIREBASE_DATABASE_URL}",
        projectId:         "${process.env.FIREBASE_PROJECT_ID}",
        storageBucket:     "${process.env.FIREBASE_STORAGE_BUCKET}",
        messagingSenderId: "${process.env.FIREBASE_MESSAGING_SENDER_ID}",
        appId:             "${process.env.FIREBASE_APP_ID}",
        measurementId:     "${process.env.FIREBASE_MEASUREMENT_ID || ''}"
    },
    fcmServerKey:  "${process.env.FCM_SERVER_KEY}",
    adminUsername: "${process.env.ADMIN_USERNAME}",
    adminPassword: "${process.env.ADMIN_PASSWORD}"
};
`;

const outPath = path.join(__dirname, 'config.js');
fs.writeFileSync(outPath, config, 'utf8');
console.log('✅  config.js generated from environment variables.');
