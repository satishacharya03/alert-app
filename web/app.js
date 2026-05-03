// ==========================================
// 1. FIREBASE CONFIGURATION
// Replace the values below with your own from Firebase Console
// ==========================================
const firebaseConfig = {
    apiKey: "YOUR_API_KEY",
    authDomain: "YOUR_PROJECT.firebaseapp.com",
    databaseURL: "https://YOUR_PROJECT-default-rtdb.firebaseio.com",
    projectId: "YOUR_PROJECT_ID",
    storageBucket: "YOUR_PROJECT.appspot.com",
    messagingSenderId: "YOUR_SENDER_ID",
    appId: "YOUR_APP_ID"
};

// Found in Firebase Console -> Project Settings -> Cloud Messaging (Legacy)
const FCM_SERVER_KEY = "YOUR_FCM_SERVER_KEY";

// Admin Credentials
const ADMIN_USER = "admin";
const ADMIN_PASS = "alertnow123";

// Initialize Firebase
if (firebaseConfig.apiKey !== "YOUR_API_KEY") {
    firebase.initializeApp(firebaseConfig);
} else {
    console.warn("AlertNow: Please update your firebaseConfig in app.js");
}

const db = firebase.database();
let targetToken = "";
let targetUserId = "";

// ==========================================
// 2. USER FACING FUNCTIONS (index.html)
// ==========================================

async function verifyId() {
    const idInput = document.getElementById('alert-id');
    const statusDiv = document.getElementById('verify-status');
    const alertId = idInput.value.trim().toUpperCase();

    if (!alertId) return;

    statusDiv.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Verifying...';

    try {
        const snapshot = await db.ref('users/' + alertId).once('value');
        const userData = snapshot.val();

        if (userData && userData.fcmToken) {
            targetToken = userData.fcmToken;
            targetUserId = alertId;
            
            document.getElementById('step-verify').style.display = 'none';
            document.getElementById('step-message').style.display = 'block';
            document.getElementById('recipient-name').innerText = `Sending to: ${userData.name}`;
        } else {
            statusDiv.innerHTML = '<span class="status-badge status-invalid">ID Not Found or No Token</span>';
        }
    } catch (error) {
        console.error(error);
        statusDiv.innerHTML = '<span class="status-badge status-invalid">Error connecting to database</span>';
    }
}

async function sendAlert() {
    const msg = document.getElementById('alert-msg').value.trim() || "Emergency Alert!";
    const btn = document.getElementById('send-btn');
    
    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> SENDING...';

    const success = await triggerFCM(targetToken, msg);

    if (success) {
        btn.style.background = "#22c55e";
        btn.innerHTML = '<i class="fas fa-check"></i> SENT SUCCESSFULLY';
        setTimeout(() => location.reload(), 2000);
    } else {
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-exclamation-triangle"></i> FAILED - RETRY';
    }
}

// ==========================================
// 3. ADMIN FUNCTIONS (admin.html)
// ==========================================

function checkLogin() {
    const user = document.getElementById('admin-user').value;
    const pass = document.getElementById('admin-pass').value;

    if (user === ADMIN_USER && pass === ADMIN_PASS) {
        sessionStorage.setItem('adminLoggedIn', 'true');
        document.getElementById('login-modal').style.display = 'none';
        loadUsers();
    } else {
        alert("Invalid credentials");
    }
}

function loadUsers() {
    const userList = document.getElementById('user-list');
    
    db.ref('users').on('value', (snapshot) => {
        userList.innerHTML = '';
        const users = snapshot.val();
        
        if (!users) {
            userList.innerHTML = '<div style="color: var(--text-muted);">No users registered yet.</div>';
            return;
        }

        for (let id in users) {
            const user = users[id];
            const card = document.createElement('div');
            card.className = 'user-card glass';
            card.innerHTML = `
                <div class="user-info">
                    <h3>${user.name}</h3>
                    <p>${id}</p>
                    <div style="margin-top: 1rem;">
                        <button class="btn btn-primary" style="padding: 0.5rem 1rem; font-size: 0.8rem;" 
                                onclick="quickAlert('${user.fcmToken}', '${user.name}', '${id}')">
                            <i class="fas fa-bell"></i> Alert
                        </button>
                    </div>
                </div>
            `;
            userList.appendChild(card);
        }
    });
}

function quickAlert(token, name, id) {
    targetToken = token;
    targetUserId = id;
    document.getElementById('step-verify').style.display = 'none';
    document.getElementById('step-message').style.display = 'block';
    document.getElementById('recipient-name').innerText = `Sending to: ${name}`;
    // Scroll to message if needed or just use a modal (skipped for brevity)
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function showBroadcastModal() {
    document.getElementById('broadcast-modal').style.display = 'flex';
}

function closeModal() {
    document.getElementById('broadcast-modal').style.display = 'none';
}

async function sendBroadcast() {
    const msg = document.getElementById('broadcast-msg').value.trim() || "Global Emergency Alert!";
    const btn = document.querySelector('#broadcast-modal .btn-danger');
    
    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> BROADCASTING...';

    // In a real app, you'd use FCM topics, but for this simple version 
    // we'll send to all tokens in the database
    const snapshot = await db.ref('users').once('value');
    const users = snapshot.val();
    
    let count = 0;
    for (let id in users) {
        if (users[id].fcmToken) {
            await triggerFCM(users[id].fcmToken, msg);
            count++;
        }
    }

    btn.innerHTML = `<i class="fas fa-check"></i> SENT TO ${count} USERS`;
    setTimeout(() => {
        closeModal();
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-broadcast-tower"></i> SEND TO ALL USERS';
    }, 3000);
}

// ==========================================
// 4. CORE FCM TRIGGER (Shared)
// ==========================================

async function triggerFCM(token, message) {
    if (FCM_SERVER_KEY === "YOUR_FCM_SERVER_KEY") {
        alert("Please set your FCM_SERVER_KEY in app.js");
        return false;
    }

    const payload = {
        to: token,
        data: {
            type: "ALERT",
            from: "AlertNow Admin",
            message: message,
            sound: "alarm",
            timestamp: new Date().getTime()
        },
        priority: "high"
    };

    try {
        const response = await fetch('https://fcm.googleapis.com/fcm/send', {
            method: 'POST',
            headers: {
                'Authorization': 'key=' + FCM_SERVER_KEY,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });

        const data = await response.json();
        return data.success === 1;
    } catch (error) {
        console.error("FCM Error:", error);
        return false;
    }
}
