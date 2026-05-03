const { GoogleAuth } = require('google-auth-library');
const fetch = require('node-fetch');

exports.handler = async (event, context) => {
    // Only allow POST requests
    if (event.httpMethod !== "POST") {
        return { statusCode: 405, body: "Method Not Allowed" };
    }

    try {
        const { token, message, from } = JSON.parse(event.body);

        // 1. Get Service Account
        let serviceAccount;
        if (process.env.FIREBASE_SERVICE_ACCOUNT) {
            serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
        } else {
            // Fallback for local testing if the JSON file is present in the root folder
            try {
                const fs = require('fs');
                const path = require('path');
                // Path resolving out of web/netlify/functions to the root
                const keyPath = path.join(__dirname, '..', '..', '..', 'alert-sas-c981d0cdd77f.json');
                serviceAccount = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
            } catch (err) {
                throw new Error("Missing FIREBASE_SERVICE_ACCOUNT in Netlify Env Vars, and local JSON key not found.");
            }
        }
        
        // 2. Authenticate with Google
        const auth = new GoogleAuth({
            credentials: serviceAccount,
            scopes: ['https://www.googleapis.com/auth/firebase.messaging'],
        });
        const client = await auth.getClient();
        const accessToken = (await client.getAccessToken()).token;

        // 3. Prepare FCM v1 Payload
        const projectId = serviceAccount.project_id;
        const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
        
        const payload = {
            message: {
                token: token,
                data: {
                    type: "ALERT",
                    from: from || "AlertNow",
                    message: message,
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
        
        return {
            statusCode: 200,
            body: JSON.stringify({ success: !!result.name, data: result }),
        };

    } catch (error) {
        console.error("Netlify Function Error:", error);
        return {
            statusCode: 500,
            body: JSON.stringify({ success: false, error: error.message }),
        };
    }
};
