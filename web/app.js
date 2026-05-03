// ============================================================
//  app.js — AlertNow Web App Logic
//  Secrets come from config.js (window.ALERT_CONFIG)
//  which is generated from your .env file
// ============================================================

// ── Load config from config.js (loaded before this script in HTML) ──
const _cfg = window.ALERT_CONFIG;
if (!_cfg) {
    alert("ERROR: config.js not loaded. Make sure <script src='config.js'> is above app.js in your HTML.");
    throw new Error("ALERT_CONFIG not found");
}

const firebaseConfig  = _cfg.firebase;
const FCM_SERVER_KEY  = _cfg.fcmServerKey;
const ADMIN_USER      = _cfg.adminUsername;
const ADMIN_PASS      = _cfg.adminPassword;

// ── Initialize Firebase ──────────────────────────────────────
firebase.initializeApp(firebaseConfig);
const db = firebase.database();

let targetToken   = "";
let targetUserId  = "";

// ============================================================
//  1. USER — Verify ID & Send Alert  (index.html)
// ============================================================

async function verifyId() {
    const idInput   = document.getElementById('alert-id');
    const statusDiv = document.getElementById('verify-status');
    const alertId   = idInput.value.trim().toUpperCase();

    if (!alertId) return;

    statusDiv.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Verifying…';

    try {
        const snapshot = await db.ref('users/' + alertId).once('value');
        const userData = snapshot.val();

        if (userData && userData.fcmToken) {
            targetToken   = userData.fcmToken;
            targetUserId  = alertId;

            document.getElementById('step-verify').style.display  = 'none';
            document.getElementById('step-message').style.display = 'block';
            document.getElementById('recipient-name').innerText   = `Sending to: ${userData.name}`;
        } else {
            statusDiv.innerHTML = '<span class="status-badge status-invalid">ID Not Found</span>';
        }
    } catch (err) {
        console.error(err);
        statusDiv.innerHTML = '<span class="status-badge status-invalid">Database connection error</span>';
    }
}

async function sendAlert() {
    const msg = document.getElementById('alert-msg').value.trim() || "Emergency Alert!";
    const btn = document.getElementById('send-btn');

    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> SENDING…';

    const ok = await triggerFCM(targetToken, msg);

    if (ok) {
        btn.style.background = "#22c55e";
        btn.innerHTML = '<i class="fas fa-check"></i> SENT SUCCESSFULLY';
        setTimeout(() => location.reload(), 2000);
    } else {
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-exclamation-triangle"></i> FAILED — RETRY';
    }
}

// ============================================================
//  2. ADMIN PANEL  (admin.html)
// ============================================================

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

    db.ref('users').on('value', snapshot => {
        userList.innerHTML = '';
        const users = snapshot.val();

        if (!users) {
            userList.innerHTML = '<div style="color:var(--text-muted)">No users registered yet.</div>';
            return;
        }

        for (const id in users) {
            const u    = users[id];
            const card = document.createElement('div');
            card.className = 'user-card glass';
            card.innerHTML = `
                <div class="user-info">
                    <h3>${u.name}</h3>
                    <p>${id}</p>
                    <small style="color:var(--text-muted);font-size:0.75rem">
                        Last seen: ${u.lastSeen ? new Date(u.lastSeen).toLocaleString() : 'Unknown'}
                    </small>
                    <div style="margin-top:1rem">
                        <button class="btn btn-primary"
                                style="padding:0.5rem 1rem;font-size:0.8rem"
                                onclick="quickAlert('${u.fcmToken}','${u.name}','${id}')">
                            <i class="fas fa-bell"></i> Alert
                        </button>
                    </div>
                </div>`;
            userList.appendChild(card);
        }
    });
}

function quickAlert(token, name, id) {
    targetToken  = token;
    targetUserId = id;
    // If on admin page, show an inline prompt or redirect
    const msg = prompt(`Send emergency message to ${name}:`);
    if (msg) triggerFCM(token, msg).then(ok => alert(ok ? "✅ Alert sent!" : "❌ Failed to send"));
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

    btn.disabled  = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> BROADCASTING…';

    const snapshot = await db.ref('users').once('value');
    const users    = snapshot.val();

    let count = 0;
    for (const id in users) {
        if (users[id].fcmToken) {
            await triggerFCM(users[id].fcmToken, msg);
            count++;
        }
    }

    btn.innerHTML = `<i class="fas fa-check"></i> SENT TO ${count} USERS`;
    setTimeout(() => {
        closeModal();
        btn.disabled  = false;
        btn.innerHTML = '<i class="fas fa-broadcast-tower"></i> SEND TO ALL USERS';
    }, 3000);
}

// ============================================================
//  3. CORE FCM TRIGGER  (shared)
// ============================================================

// ============================================================
//  3. CORE FUNCTION TRIGGER (Modern Secure Way)
// ============================================================

async function triggerFCM(token, message) {
    try {
        const response = await fetch('/.netlify/functions/send-alert', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                token: token,
                message: message,
                from: "AlertNow Web"
            })
        });
        
        const result = await response.json();
        console.log("Netlify Function response:", result);
        return result.success === true;
    } catch (err) {
        console.error("Netlify Function Error:", err);
        return false;
    }
}
