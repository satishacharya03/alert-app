const fs = require('fs');
const path = require('path');

// Try to load .env file if it exists (for local development)
try {
    require('dotenv').config({ path: path.join(__dirname, 'web', '.env') });
} catch (err) {
    // Dotenv might not be installed or file missing, that's fine on Netlify
}

// This script generates web/config.js using environment variables
// It's designed to run during the Netlify build process.

const config = {
    firebase: {
        apiKey: process.env.FIREBASE_API_KEY,
        authDomain: process.env.FIREBASE_AUTH_DOMAIN,
        databaseURL: process.env.FIREBASE_DATABASE_URL,
        projectId: process.env.FIREBASE_PROJECT_ID,
        storageBucket: process.env.FIREBASE_STORAGE_BUCKET,
        messagingSenderId: process.env.FIREBASE_MESSAGING_SENDER_ID,
        appId: process.env.FIREBASE_APP_ID,
        measurementId: process.env.FIREBASE_MEASUREMENT_ID
    },
    adminUsername: process.env.ADMIN_USERNAME || 'admin',
    adminPassword: process.env.ADMIN_PASSWORD
};

const content = `// Auto-generated config file
window.ALERT_CONFIG = ${JSON.stringify(config, null, 2)};
`;

const outputPath = path.join(__dirname, 'web', 'config.js');

try {
    fs.writeFileSync(outputPath, content);
    console.log('✅ web/config.js has been generated successfully.');
} catch (err) {
    console.error('❌ Failed to generate web/config.js:', err);
    process.exit(1);
}
