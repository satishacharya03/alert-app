const { GoogleAuth } = require('google-auth-library');
const fetch = require('node-fetch');

exports.handler = async (event, context) => {
    // Only allow POST requests
    if (event.httpMethod !== "POST") {
        return { statusCode: 405, body: "Method Not Allowed" };
    }

    try {
        if (!event.body) {
            return { statusCode: 400, body: JSON.stringify({ success: false, error: "Missing request body" }) };
        }

        const { token, message, from } = JSON.parse(event.body);

        if (!token) {
            return { statusCode: 400, body: JSON.stringify({ success: false, error: "Missing recipient token" }) };
        }

        // 1. Get Service Account
        let serviceAccount;
        if (process.env.FIREBASE_SERVICE_ACCOUNT) {
            try {
                serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
            } catch (e) {
                return { statusCode: 500, body: JSON.stringify({ success: false, error: "Invalid JSON in FIREBASE_SERVICE_ACCOUNT environment variable." }) };
            }
        } else {
            // Fallback for local testing
            try {
                const fs = require('fs');
                const path = require('path');
                const keyPath = path.join(__dirname, '..', '..', '..', 'alert-sas-c981d0cdd77f.json');
                if (fs.existsSync(keyPath)) {
                    serviceAccount = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
                } else {
                    return { statusCode: 500, body: JSON.stringify({ success: false, error: "FIREBASE_SERVICE_ACCOUNT is not set in Netlify Environment Variables." }) };
                }
            } catch (err) {
                return { statusCode: 500, body: JSON.stringify({ success: false, error: "Failed to load service account key locally or from environment." }) };
            }
        }
        
        // 2. Authenticate with Google
        const auth = new GoogleAuth({
            credentials: serviceAccount,
            scopes: ['https://www.googleapis.com/auth/firebase.messaging'],
        });
        const client = await auth.getClient();
        const credentials = await client.getAccessToken();
        const accessToken = credentials.token;

        // 3. Prepare FCM v1 Payload
        const projectId = serviceAccount.project_id;
        const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
        
        const payload = {
            message: {
                token: token,
                data: {
                    type: "ALERT",
                    from: from || "AlertNow",
                    message: message || "Emergency Alert!",
                    sound: "alarm",
                    timestamp: String(Date.now())
                },
                android: {
                    priority: "high"
                }
            }
        };

        // 4. Send to Google
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${accessToken}`,
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(payload),
        });

        const result = await response.json();
        
        if (response.status !== 200) {
            return {
                statusCode: response.status,
                body: JSON.stringify({ success: false, error: result.error ? result.error.message : "FCM API Error" }),
            };
        }

        return {
            statusCode: 200,
            body: JSON.stringify({ success: true, data: result }),
        };

    } catch (error) {
        console.error("Netlify Function Error:", error);
        return {
            statusCode: 500,
            body: JSON.stringify({ success: false, error: error.message }),
        };
    }
};
