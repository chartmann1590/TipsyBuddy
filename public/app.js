// TipsyBuddy Live Location & Night Out Web Companion
// 100% Free - Leaflet & OpenStreetMap + Firestore REST

const FIRESTORE_PROJECT_ID = "party-quips-2026";
let map = null;
let friendMarker = null;
let currentSessionData = null;
let currentSessionId = null;
let updateInterval = null;
let countdownInterval = null;

// Parse Query Parameters
function getQueryParam(param) {
  const urlParams = new URLSearchParams(window.location.search);
  return urlParams.get(param);
}

// DOM Elements
const sessionView = document.getElementById("sessionView");
const homeView = document.getElementById("homeView");
const headerStatusBadge = document.getElementById("headerStatusBadge");
const liveStatusText = document.getElementById("liveStatusText");

const userNameDisplay = document.getElementById("userNameDisplay");
const venueNameDisplay = document.getElementById("venueNameDisplay");
const statusMessageDisplay = document.getElementById("statusMessageDisplay");
const batteryDisplay = document.getElementById("batteryDisplay");
const bacValue = document.getElementById("bacValue");
const bacZone = document.getElementById("bacZone");
const soberCountdown = document.getElementById("soberCountdown");
const timerRemaining = document.getElementById("timerRemaining");
const expiryLabel = document.getElementById("expiryLabel");
const safetyAlertBox = document.getElementById("safetyAlertBox");
const safetyAlertMessage = document.getElementById("safetyAlertMessage");
const lastUpdatedTimestamp = document.getElementById("lastUpdatedTimestamp");
const coordsDisplay = document.getElementById("coordsDisplay");

const uberLink = document.getElementById("uberLink");
const lyftLink = document.getElementById("lyftLink");
const callFriendBtn = document.getElementById("callFriendBtn");
const sendCheerBtn = document.getElementById("sendCheerBtn");
const recenterBtn = document.getElementById("recenterBtn");

const sessionInput = document.getElementById("sessionInput");
const trackSessionBtn = document.getElementById("trackSessionBtn");

// Initialize Map
function initMap(lat, lon) {
  if (map) return;

  map = L.map('liveMap', {
    zoomControl: false,
    attributionControl: true,
    scrollWheelZoom: false
  }).setView([lat, lon], 16);

  // Standard OpenStreetMap tiles do not require an API key.
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  }).addTo(map);

  L.control.zoom({ position: 'bottomright' }).addTo(map);

  // Custom Pulsing Neon Marker Icon
  const customIcon = L.divIcon({
    className: 'custom-live-pin',
    html: `
      <div style="
        position: relative;
        width: 36px;
        height: 36px;
        background: radial-gradient(circle, #F59E0B 0%, #D97706 100%);
        border: 3px solid #FFFFFF;
        border-radius: 50%;
        box-shadow: 0 0 15px rgba(245, 158, 11, 0.8);
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 18px;
      ">
        🍻
        <span style="
          position: absolute;
          width: 52px;
          height: 52px;
          border-radius: 50%;
          border: 2px solid #F59E0B;
          animation: pulseMarker 2s infinite;
          top: -10px;
          left: -10px;
        "></span>
      </div>
    `,
    iconSize: [36, 36],
    iconAnchor: [18, 18],
    popupAnchor: [0, -20]
  });

  friendMarker = L.marker([lat, lon], { icon: customIcon }).addTo(map);
}

// Update Map Position
function updateMapPosition(lat, lon, venue, userName) {
  if (!map) {
    initMap(lat, lon);
  } else {
    friendMarker.setLatLng([lat, lon]);
    map.panTo([lat, lon]);
  }

  friendMarker.bindPopup(`
    <div style="color: #000; font-family: sans-serif; font-size: 12px; padding: 4px;">
      <strong style="font-size: 14px;">${userName || 'Friend'}</strong><br/>
      📍 ${venue || 'Current Location'}<br/>
      <small style="color: #666;">Live updating</small>
    </div>
  `);
}

// Parse Firestore Document Fields
function parseFirestoreDoc(doc) {
  if (!doc || !doc.fields) return null;
  const fields = doc.fields;
  return {
    userName: fields.userName ? (fields.userName.stringValue || "Friend") : "Friend",
    venue: fields.venue ? fields.venue.stringValue : "Night Out",
    status: fields.status ? fields.status.stringValue : "Active",
    latitude: fields.latitude ? parseFloat(fields.latitude.doubleValue || fields.latitude.integerValue || 0) : 0,
    longitude: fields.longitude ? parseFloat(fields.longitude.doubleValue || fields.longitude.integerValue || 0) : 0,
    bac: fields.bac ? parseFloat(fields.bac.doubleValue || fields.bac.integerValue || 0) : 0.0,
    battery: fields.battery ? parseInt(fields.battery.integerValue || 100) : 100,
    phone: fields.phone ? fields.phone.stringValue : "",
    homeAddress: fields.homeAddress ? fields.homeAddress.stringValue : "",
    expiresAt: fields.expiresAt ? fields.expiresAt.stringValue : null,
    updatedAt: fields.updatedAt ? fields.updatedAt.stringValue : new Date().toISOString()
  };
}

// Render Session Data
function renderSession(data) {
  currentSessionData = data;
  headerStatusBadge.style.display = "flex";
  liveStatusText.textContent = "Live Connected";

  userNameDisplay.textContent = `${data.userName}'s Night Out`;
  venueNameDisplay.textContent = data.venue || "Exploring the city";
  if (data.status && data.status !== "Active") {
    statusMessageDisplay.textContent = `💬 ${data.status}`;
    statusMessageDisplay.style.display = "block";
  } else {
    statusMessageDisplay.style.display = "none";
  }
  batteryDisplay.textContent = `🔋 ${data.battery}%`;

  // BAC display
  const bac = data.bac || 0.0;
  bacValue.textContent = bac.toFixed(3) + "%";

  // Zone pill
  bacZone.className = "zone-badge";
  if (bac < 0.02) {
    bacZone.textContent = "Sober";
    bacZone.classList.add("sober");
    soberCountdown.textContent = "Reflexes normal";
    safetyAlertBox.style.display = "none";
  } else if (bac < 0.05) {
    bacZone.textContent = "Buzzed";
    bacZone.classList.add("buzzed");
    soberCountdown.textContent = `Sober in ~${Math.ceil(bac / 0.015)} hrs`;
    safetyAlertBox.style.display = "none";
  } else if (bac < 0.08) {
    bacZone.textContent = "Tipsy";
    bacZone.classList.add("tipsy");
    soberCountdown.textContent = `Sober in ~${Math.ceil(bac / 0.015)} hrs`;
    safetyAlertBox.style.display = "flex";
    safetyAlertMessage.textContent = `${data.userName} is tipsy (BAC ${bac.toFixed(2)}%). Consider ordering them a ride!`;
  } else {
    bacZone.textContent = "Drunk / Intoxicated";
    bacZone.classList.add("drunk");
    soberCountdown.textContent = `Sober in ~${Math.ceil(bac / 0.015)} hrs`;
    safetyAlertBox.style.display = "flex";
    safetyAlertMessage.textContent = `⚠️ High Intoxication (${bac.toFixed(2)}% BAC). Driving is dangerous & illegal. Call them a taxi now!`;
  }

  // Location & Map
  if (data.latitude && data.longitude) {
    coordsDisplay.textContent = `Lat: ${data.latitude.toFixed(4)}, Lon: ${data.longitude.toFixed(4)}`;
    updateMapPosition(data.latitude, data.longitude, data.venue, data.userName);

    // Setup Uber & Lyft deep links
    const homeEncoded = encodeURIComponent(data.homeAddress || "");
    uberLink.href = `https://m.uber.com/ul/?action=setPickup&client_id=tipsybuddy&pickup[latitude]=${data.latitude}&pickup[longitude]=${data.longitude}&dropoff[formatted_address]=${homeEncoded}`;
    lyftLink.href = `https://lyft.com/ride?id=lyft&pickup[latitude]=${data.latitude}&pickup[longitude]=${data.longitude}`;
  }

  // Phone action
  if (data.phone) {
    callFriendBtn.href = `tel:${data.phone}`;
    callFriendBtn.style.display = "flex";
  } else {
    callFriendBtn.href = `tel:911`;
    callFriendBtn.textContent = "📞 Call Emergency";
  }

  // Last Updated
  const updatedTime = new Date(data.updatedAt);
  lastUpdatedTimestamp.textContent = `Updated ${updatedTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}`;

  // Start timer countdown
  updateCountdown();
}

// Expiration Countdown
function updateCountdown() {
  if (!currentSessionData || !currentSessionData.expiresAt) {
    timerRemaining.textContent = "Active";
    expiryLabel.textContent = "Until night ends";
    return;
  }

  const expireTime = new Date(currentSessionData.expiresAt).getTime();
  const now = new Date().getTime();
  const diff = expireTime - now;

  if (diff <= 0) {
    timerRemaining.textContent = "Expired";
    timerRemaining.style.color = "#EF4444";
    expiryLabel.textContent = "Live share finished";
    liveStatusText.textContent = "Session Ended";
  } else {
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((diff % (1000 * 60)) / 1000);
    timerRemaining.textContent = `${hours > 0 ? hours + 'h ' : ''}${minutes}m ${seconds}s`;
    timerRemaining.style.color = "#F59E0B";
    expiryLabel.textContent = "Remaining live time";
  }
}

// Fetch Session from Firestore REST
async function fetchSessionData(sessionId) {
  try {
    const url = `https://firestore.googleapis.com/v1/projects/${FIRESTORE_PROJECT_ID}/databases/(default)/documents/sessions/${encodeURIComponent(sessionId)}`;
    const res = await fetch(url);
    if (!res.ok) {
      if (res.status === 404) {
        showHomeView(`Session "${sessionId}" not found. It may have expired or not started yet.`);
      }
      return null;
    }
    const doc = await res.json();
    return parseFirestoreDoc(doc);
  } catch (err) {
    console.error("Failed to load session:", err);
    return null;
  }
}

// Send a "wave" check-in ping back to the phone (partial update, doesn't touch other fields)
async function sendWave(sessionId) {
  try {
    const url = `https://firestore.googleapis.com/v1/projects/${FIRESTORE_PROJECT_ID}/databases/(default)/documents/sessions/${encodeURIComponent(sessionId)}?updateMask.fieldPaths=cheerSentAt`;
    const res = await fetch(url, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        fields: {
          cheerSentAt: { stringValue: new Date().toISOString() }
        }
      })
    });
    return res.ok;
  } catch (err) {
    console.error("Failed to send wave:", err);
    return false;
  }
}

// UI State Management
function showSessionView() {
  sessionView.style.display = "flex";
  homeView.style.display = "none";
}

function showHomeView(message) {
  sessionView.style.display = "none";
  homeView.style.display = "block";
  headerStatusBadge.style.display = "none";
  if (message) {
    alert(message);
  }
}

// Load Session
async function loadSession(sessionId) {
  currentSessionId = sessionId;
  showSessionView();
  liveStatusText.textContent = "Loading...";

  const data = await fetchSessionData(sessionId);
  if (data) {
    renderSession(data);
  } else {
    // If not found, load sample preview or show home
    showHomeView();
    return;
  }

  // Auto-refresh every 6 seconds
  if (updateInterval) clearInterval(updateInterval);
  updateInterval = setInterval(async () => {
    const refreshed = await fetchSessionData(sessionId);
    if (refreshed) {
      renderSession(refreshed);
    }
  }, 6000);

  if (countdownInterval) clearInterval(countdownInterval);
  countdownInterval = setInterval(updateCountdown, 1000);
}

// Event Listeners
document.addEventListener("DOMContentLoaded", () => {
  const sessionId = getQueryParam("session") || getQueryParam("id");

  if (sessionId) {
    loadSession(sessionId);
  } else {
    showHomeView();
  }

  recenterBtn.addEventListener("click", () => {
    if (map && currentSessionData && currentSessionData.latitude) {
      map.setView([currentSessionData.latitude, currentSessionData.longitude], 16);
    }
  });

  sendCheerBtn.addEventListener("click", async () => {
    if (!currentSessionId) return;
    sendCheerBtn.disabled = true;
    const ok = await sendWave(currentSessionId);
    sendCheerBtn.innerHTML = ok ? "🎉 Wave Sent!" : "⚠️ Wave failed, try again";
    setTimeout(() => {
      sendCheerBtn.innerHTML = '👋 Send "Check In" Wave';
      sendCheerBtn.disabled = false;
    }, 3000);
  });

  trackSessionBtn.addEventListener("click", () => {
    const code = sessionInput.value.trim();
    if (code) {
      window.location.search = `?session=${encodeURIComponent(code)}`;
    }
  });
});
