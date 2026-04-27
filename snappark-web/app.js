/* ═══════════════════════════════════════════════════════════════
   SNAPPARK MOBILE — Frontend Logic (app.js)
   Handles both entry.html and exit.html
   ═══════════════════════════════════════════════════════════════ */

function getApiUrl() {
    const urlParams = new URLSearchParams(window.location.search);
    const apiParam = urlParams.get('api');
    if (apiParam) return apiParam.replace(/\/$/, "");
    return null; // No kiosk connected
}
const API = getApiUrl();
const API_KEY = new URLSearchParams(window.location.search).get('apiKey') || '';

// Helper: POST with API key auth
function authPost(url, body) {
    return fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-API-Key': API_KEY,
            'ngrok-skip-browser-warning': 'true'
        },
        body: JSON.stringify(body)
    });
}

// ── Guard: Show "Scan QR" screen if no Kiosk API is connected ──
if (!API) {
    document.addEventListener('DOMContentLoaded', () => {
        const container = document.querySelector('.app-container');
        if (container) {
            container.innerHTML = `
                <div style="min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:40px 20px;">
                    <div style="font-size:5rem;margin-bottom:20px;animation:float 3s ease-in-out infinite;">📷</div>
                    <h1 style="font-size:1.8rem;font-weight:900;margin-bottom:12px;background:linear-gradient(135deg,#00C2FF,#A855F7);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;">Scan the QR Code</h1>
                    <p style="color:#8B949E;font-size:0.95rem;max-width:340px;line-height:1.6;margin-bottom:32px;">
                        This page requires a connection to a <strong style="color:#E6EDF3;">SnapPark Kiosk</strong>. 
                        Visit any SnapPark-enabled parking lot and scan the QR code displayed on the Kiosk screen to get started.
                    </p>
                    <div style="background:#0D1117;border:1px solid #21262D;border-radius:14px;padding:24px 28px;max-width:320px;width:100%;">
                        <div style="font-size:0.7rem;font-weight:700;letter-spacing:2px;text-transform:uppercase;color:#484F58;margin-bottom:16px;">How It Works</div>
                        <div style="display:flex;align-items:center;gap:12px;margin-bottom:14px;text-align:left;">
                            <span style="font-size:1.3rem;">🏢</span>
                            <span style="font-size:0.8rem;color:#8B949E;">Drive to a SnapPark parking lot</span>
                        </div>
                        <div style="display:flex;align-items:center;gap:12px;margin-bottom:14px;text-align:left;">
                            <span style="font-size:1.3rem;">📱</span>
                            <span style="font-size:0.8rem;color:#8B949E;">Scan the QR code on the Kiosk</span>
                        </div>
                        <div style="display:flex;align-items:center;gap:12px;text-align:left;">
                            <span style="font-size:1.3rem;">🚗</span>
                            <span style="font-size:0.8rem;color:#8B949E;">Pick your slot and park instantly</span>
                        </div>
                    </div>
                    <a href="/" style="margin-top:28px;color:#00C2FF;text-decoration:none;font-size:0.85rem;font-weight:600;">← Back to SnapPark Home</a>
                </div>
                <style>
                    @keyframes float {
                        0%, 100% { transform: translateY(0px); }
                        50% { transform: translateY(-15px); }
                    }
                </style>
            `;
        }
    });
    // Stop all further JS execution
    throw new Error('No Kiosk API connected — showing scan QR screen');
}

// ── State ──
let selectedType   = 'CAR';
let selectedSlotId = null;
let sessionData    = null;   // populated after lookup on exit page
let slotsData      = [];
let ratesData      = null;
let plateTimeout   = null;

// ═════════════════════════════════════════════════════════════
//  ENTRY PAGE
// ═════════════════════════════════════════════════════════════
if (document.getElementById('step1')) {
    initEntryPage();
}

function initEntryPage() {
    fetchRates();
    setupTypeSelector();
    setupPlateDecoder();
    setupEntryNavigation();
    // Auto-refresh stats every 5s
    setInterval(fetchRates, 5000);
}

// ── Fetch Rates ──
async function fetchRates() {
    try {
        const res = await fetch(`${API}/api/rates`, { headers: { 'ngrok-skip-browser-warning': '1' } });
        ratesData = await res.json();
        updateRateDisplay();
        updateStatsBar();
    } catch (e) {
        console.warn('Could not fetch rates:', e);
    }
}

function updateStatsBar() {
    if (!ratesData) return;
    const el = (id) => document.getElementById(id);
    el('availCount').textContent = ratesData.available;
    el('occCount').textContent   = ratesData.occupied;
    el('occPct').textContent     = Math.round(ratesData.occupancyPct);
}

function updateRateDisplay() {
    if (!ratesData) return;
    const el = (id) => document.getElementById(id);

    // Update type button prices
    el('priceBike').textContent = `₹${ratesData.bike.effective}/hr`;
    el('priceCar').textContent  = `₹${ratesData.car.effective}/hr`;
    el('priceSuv').textContent  = `₹${ratesData.suv.effective}/hr`;

    // Rate preview
    const typeKey = selectedType.toLowerCase();
    const info = ratesData[typeKey];
    el('rateValue').textContent = `₹${info.effective}/hr`;

    // Surge or discount
    if (ratesData.isSurge && ratesData.surgePercent > 0) {
        el('surgeRow').style.display = 'flex';
        el('surgeLabel').textContent = ratesData.timeLabel;
        el('surgeBadge').textContent = `+${ratesData.surgePercent}%`;
        el('discountRow').style.display = 'none';
    } else if (ratesData.surgePercent < 0) {
        el('discountRow').style.display = 'flex';
        el('discountLabel').textContent = ratesData.timeLabel;
        el('discountBadge').textContent = `${ratesData.surgePercent}%`;
        el('surgeRow').style.display = 'none';
    } else {
        el('surgeRow').style.display = 'none';
        el('discountRow').style.display = 'none';
    }
}

// ── Vehicle Type Selector ──
function setupTypeSelector() {
    document.querySelectorAll('.type-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.type-btn').forEach(b => b.classList.remove('selected'));
            btn.classList.add('selected');
            selectedType = btn.dataset.type;
            updateRateDisplay();
        });
    });
}

// ── Live Plate Decoder ──
function setupPlateDecoder() {
    const input = document.getElementById('plateInput');
    if (!input) return;
    input.addEventListener('input', () => {
        input.value = input.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
        clearTimeout(plateTimeout);
        plateTimeout = setTimeout(() => decodePlate(input.value), 300);
    });
}

async function decodePlate(plate) {
    const badge = document.getElementById('plateBadge');
    if (plate.length < 2) { badge.innerHTML = ''; return; }
    try {
        const res = await fetch(`${API}/api/validate-plate?plate=${encodeURIComponent(plate)}`, { headers: { 'ngrok-skip-browser-warning': '1' } });
        const data = await res.json();
        if (data.valid) {
            badge.innerHTML = `<div class="plate-badge valid">
                <span class="state-dot" style="background:${data.stateColor}"></span>
                ✅ ${data.formatted} · ${data.state} · ${data.rto}
            </div>`;
        } else if (data.hint && data.hint.length > 0) {
            badge.innerHTML = `<div class="plate-badge partial">
                <span class="state-dot" style="background:${data.stateColor || '#888'}"></span>
                ${data.hint}
            </div>`;
        } else {
            badge.innerHTML = `<div class="plate-badge error">❌ ${data.error}</div>`;
        }
    } catch (e) {
        badge.innerHTML = '';
    }
}

// ── Step Navigation ──
function setupEntryNavigation() {
    const el = (id) => document.getElementById(id);
    const btnNext1 = el('btnNext1');
    const btnBack2 = el('btnBack2');
    const btnNext2 = el('btnNext2');

    if (btnNext1) btnNext1.addEventListener('click', () => {
        // Validate step 1
        const phone = el('phoneInput').value.replace(/[^0-9]/g, '');
        const plate = el('plateInput').value.trim();
        if (phone.length < 10) { showAlert('Enter a valid 10-digit phone number', 'error'); return; }
        if (plate.length < 6)  { showAlert('Enter a valid vehicle number', 'error'); return; }
        goToStep(2);
        loadSlotGrid();
        loadRecommendations();
    });

    if (btnBack2) btnBack2.addEventListener('click', () => goToStep(1));

    if (btnNext2) btnNext2.addEventListener('click', () => {
        if (!selectedSlotId) { showAlert('Select a parking slot', 'error'); return; }
        goToStep(3);
        doCheckin();
    });
}

function goToStep(n) {
    const el = (id) => document.getElementById(id);
    // Hide all steps
    document.querySelectorAll('.step-screen').forEach(s => {
        s.classList.remove('active');
    });
    el(`step${n}`).classList.add('active');

    // Update dots
    for (let i = 1; i <= 3; i++) {
        const dot = el(`dot${i}`);
        dot.classList.remove('active', 'done');
        if (i < n) dot.classList.add('done'), dot.textContent = '✓';
        else if (i === n) dot.classList.add('active'), dot.textContent = i;
        else dot.textContent = i;
    }
    // Lines
    el('line1').classList.toggle('active', n >= 2);
    el('line2').classList.toggle('active', n >= 3);

    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// ── Slot Grid ──
async function loadSlotGrid() {
    try {
        const res = await fetch(`${API}/api/slots`, { headers: { 'ngrok-skip-browser-warning': '1' } });
        slotsData = await res.json();
        renderSlotGrid();
    } catch (e) {
        document.getElementById('slotGrid').innerHTML = '<div class="alert alert-error">Failed to load slots</div>';
    }
}

function renderSlotGrid() {
    const container = document.getElementById('slotGrid');
    const floors = {};

    slotsData.forEach(slot => {
        if (!floors[slot.floor]) floors[slot.floor] = [];
        floors[slot.floor].push(slot);
    });

    // Count matching available slots
    const matchingAvail = slotsData.filter(s => s.type === selectedType && s.status === 'AVAILABLE').length;
    const totalMatch = slotsData.filter(s => s.type === selectedType).length;

    // Slot legend + count
    let html = `<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;flex-wrap:wrap;gap:8px;">
        <div style="display:flex;gap:14px;font-size:0.75rem;color:var(--text-secondary);">
            <span style="display:flex;align-items:center;gap:4px;"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:var(--accent-green);"></span> Open</span>
            <span style="display:flex;align-items:center;gap:4px;"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:var(--accent-red);"></span> Taken</span>
            <span style="display:flex;align-items:center;gap:4px;"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:var(--accent-amber);"></span> Held</span>
        </div>
        <div style="font-size:0.75rem;font-weight:700;color:var(--accent-cyan);">${matchingAvail}/${totalMatch} ${selectedType} slots free</div>
    </div>`;

    const sortedFloors = Object.keys(floors).sort((a, b) => a - b);

    for (const floor of sortedFloors) {
        const floorSlots = floors[floor];
        html += `<div class="floor-section">`;
        html += `<div class="floor-title">🏢 Floor ${floor}</div>`;
        html += `<div class="slot-grid">`;

        // Group by type within floor
        const bikeSlots = floorSlots.filter(s => s.type === 'BIKE');
        const carSlots  = floorSlots.filter(s => s.type === 'CAR');
        const suvSlots  = floorSlots.filter(s => s.type === 'SUV');

        if (bikeSlots.length) {
            html += `<div class="zone-label">🏍️ Bike Zone</div>`;
            bikeSlots.forEach(s => { html += renderSlotCell(s); });
        }
        if (carSlots.length) {
            html += `<div class="zone-label">🚗 Car Zone</div>`;
            carSlots.forEach(s => { html += renderSlotCell(s); });
        }
        if (suvSlots.length) {
            html += `<div class="zone-label">🚙 SUV Zone</div>`;
            suvSlots.forEach(s => { html += renderSlotCell(s); });
        }

        html += `</div></div>`;
    }

    container.innerHTML = html;

    // Bind click events
    container.querySelectorAll('.slot-cell.available:not(.filtered-out)').forEach(cell => {
        cell.addEventListener('click', () => selectSlot(cell));
    });
}

function renderSlotCell(slot) {
    const isOcc    = slot.status === 'OCCUPIED';
    const isLocked = slot.status === 'LOCKED' || slot.status === 'LOCKING';
    const isAvail  = slot.status === 'AVAILABLE';
    const filtered = slot.type !== selectedType;

    let cls = 'slot-cell';
    if (isAvail && !filtered)  cls += ' available';
    if (isOcc)                 cls += ' occupied';
    if (isLocked)              cls += ' locked';
    if (filtered)              cls += ' filtered-out';
    if (slot.id === selectedSlotId) cls += ' selected';

    // Heatmap opacity
    const heatOpacity = slot.heatMax > 0 ? (slot.heatCount / slot.heatMax * 0.35) : 0;
    const num = slot.slotNumber.split('-')[1] || slot.slotNumber;

    const icon = isOcc ? '🔴' : isLocked ? '🟡' : '🟢';

    return `<div class="${cls}" data-id="${slot.id}" data-type="${slot.type}" 
                style="--heat-opacity:${heatOpacity}">
                <div class="heat-overlay"></div>
                <span class="slot-num">${num}</span>
                <span class="slot-icon">${icon}</span>
            </div>`;
}

function selectSlot(cell) {
    // Deselect previous
    document.querySelectorAll('.slot-cell.selected').forEach(c => c.classList.remove('selected'));
    cell.classList.add('selected');
    selectedSlotId = parseInt(cell.dataset.id);
    document.getElementById('btnNext2').disabled = false;
}

// ── AI Recommendations ──
async function loadRecommendations() {
    try {
        const floor = 1; // default
        const res = await fetch(`${API}/api/slots/recommend?type=${selectedType}&floor=${floor}`, { headers: { 'ngrok-skip-browser-warning': '1' } });
        const recs = await res.json();
        if (recs.length === 0) {
            document.getElementById('recCard').style.display = 'none';
            return;
        }
        document.getElementById('recCard').style.display = 'block';
        const list = document.getElementById('recList');
        list.innerHTML = recs.slice(0, 3).map(r => `
            <div class="rec-card" data-id="${r.slotId}" onclick="selectRecSlot(${r.slotId})">
                <span class="rec-star">⭐</span>
                <div class="rec-info">
                    <div class="rec-slot">${r.slotNumber}</div>
                    <div class="rec-reason">${r.reason}</div>
                </div>
                <span class="rec-score">${r.score}</span>
            </div>
        `).join('');
    } catch (e) {
        document.getElementById('recCard').style.display = 'none';
    }
}

function selectRecSlot(slotId) {
    selectedSlotId = slotId;
    // Highlight in grid
    document.querySelectorAll('.slot-cell.selected').forEach(c => c.classList.remove('selected'));
    const cell = document.querySelector(`.slot-cell[data-id="${slotId}"]`);
    if (cell) {
        cell.classList.add('selected');
        cell.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
    document.getElementById('btnNext2').disabled = false;
}

// ── Check-in API Call ──
async function doCheckin() {
    const el = (id) => document.getElementById(id);
    el('confirmLoading').classList.remove('hidden');
    el('confirmSuccess').classList.add('hidden');

    const phone = el('phoneInput').value.replace(/[^0-9]/g, '');
    const plate = el('plateInput').value.replace(/[\s-]/g, '').toUpperCase();

    try {
        const res = await authPost(`${API}/api/checkin`, {
            phone: phone,
            plate: plate,
            type: selectedType,
            slotId: selectedSlotId
        });

        const data = await res.json();

        if (!res.ok || data.error) {
            el('confirmLoading').classList.add('hidden');
            goToStep(2);
            showAlert(data.error || 'Check-in failed', 'error');
            return;
        }

        // Show success
        el('confirmLoading').classList.add('hidden');
        el('confirmSuccess').classList.remove('hidden');

        // PIN digits
        const pinDisplay = el('pinDisplay');
        pinDisplay.innerHTML = data.sessionPin.split('').map(d => `<span class="pin-digit">${d}</span>`).join('');

        // Info grid
        el('confirmGrid').innerHTML = `
            <div class="info-item">
                <div class="label">Slot</div>
                <div class="value">${data.slotNumber}</div>
            </div>
            <div class="info-item">
                <div class="label">Floor</div>
                <div class="value">${data.floor}</div>
            </div>
            <div class="info-item">
                <div class="label">Vehicle</div>
                <div class="value">${formatPlate(data.plate)}</div>
            </div>
            <div class="info-item">
                <div class="label">Rate</div>
                <div class="value">₹${data.rate}/hr ${data.isSurge ? '⚡' : ''}</div>
            </div>
        `;

        el('fineWarningText').textContent = data.fineWarning;

    } catch (e) {
        el('confirmLoading').classList.add('hidden');
        goToStep(2);
        showAlert('Network error. Try again.', 'error');
    }
}


// ═════════════════════════════════════════════════════════════
//  EXIT PAGE
// ═════════════════════════════════════════════════════════════
function initExitPage() {
    const el = (id) => document.getElementById(id);

    // Lookup button
    const btnLookup = el('btnLookup');
    if (btnLookup) btnLookup.addEventListener('click', doLookup);

    // Payment method toggle
    document.querySelectorAll('.pay-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.pay-btn').forEach(b => b.classList.remove('selected'));
            btn.classList.add('selected');
            const method = btn.dataset.method;
            if (method === 'UPI') {
                el('upiDetail').classList.remove('hidden');
                el('cashDetail').classList.add('hidden');
            } else {
                el('upiDetail').classList.add('hidden');
                el('cashDetail').classList.remove('hidden');
            }
        });
    });

    // Pay button
    const btnPay = el('btnPay');
    if (btnPay) btnPay.addEventListener('click', doCheckout);

    // Enter key on inputs
    const exitPlateInput = el('exitPlateInput');
    const exitPinInput = el('exitPinInput');
    if (exitPlateInput) exitPlateInput.addEventListener('keyup', (e) => {
        exitPlateInput.value = exitPlateInput.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
        if (e.key === 'Enter') doLookup();
    });
    if (exitPinInput) exitPinInput.addEventListener('keyup', (e) => {
        if (e.key === 'Enter') doLookup();
    });
}

// ── Lookup Session ──
async function doLookup() {
    const el = (id) => document.getElementById(id);
    const plate = (el('exitPlateInput').value || '').replace(/[\s-]/g, '').toUpperCase();
    const pin   = el('exitPinInput').value.trim();

    if (!plate && !pin) { showAlert('Enter vehicle number or session PIN', 'error'); return; }

    showLoading('Looking up your session...');
    try {
        const res = await authPost(`${API}/api/lookup-session`, { plate: plate, pin: pin });
        const data = await res.json();
        hideLoading();

        if (!res.ok || data.error) {
            showAlert(data.error || 'Session not found', 'error');
            return;
        }

        sessionData = data;
        renderBill(data);
        el('lookupSection').classList.add('hidden');
        el('billSection').classList.remove('hidden');
    } catch (e) {
        hideLoading();
        showAlert('Network error. Try again.', 'error');
    }
}

function renderBill(data) {
    const el = (id) => document.getElementById(id);

    el('billPlate').textContent    = formatPlate(data.plate);
    el('billType').textContent     = data.vehicleType;
    el('billSlot').textContent     = data.slotNumber + ' (Floor ' + data.floor + ')';

    // State info
    if (data.stateName) {
        el('billState').textContent = data.stateName + (data.rtoName ? ' / ' + data.rtoName : '');
        el('billStateRow').style.display = 'flex';
    } else {
        el('billStateRow').style.display = 'none';
    }

    // Times
    el('billEntry').textContent    = formatDateTime(data.entryTime);
    el('billExit').textContent     = formatDateTime(data.currentTime);
    el('billDuration').textContent = `${data.durationHours}h ${data.durationMinutes}m`;

    // Rates
    el('billBaseRate').textContent = `₹${data.baseRate}/hr`;
    el('billEffRate').textContent  = `₹${data.effectiveRate}/hr`;

    if (data.isSurge && data.surgePercent > 0) {
        el('billSurgeRow').style.display = 'flex';
        el('billSurgeLabel').textContent = data.timeLabel;
        el('billSurgeVal').textContent   = `+${data.surgePercent}%`;
    } else {
        el('billSurgeRow').style.display = 'none';
    }

    el('billParkFee').textContent = `₹${data.parkingFee.toFixed(2)}`;

    // Grace
    if (data.graceApplied) {
        el('graceNote').classList.remove('hidden');
    }

    // Fines
    if (data.fines && data.fines.length > 0) {
        el('finesSection').classList.remove('hidden');
        el('finesList').innerHTML = data.fines.map(f => `
            <div class="fine-card">
                <div class="fine-type">⚠ ${f.label}</div>
                ${f.assignedSlot ? `<div class="fine-detail">Assigned: ${f.assignedSlot} → Actual: ${f.actualSlot}</div>` : ''}
                ${f.notes ? `<div class="fine-detail">${f.notes}</div>` : ''}
                <div class="fine-amount">₹${f.amount.toFixed(2)}</div>
            </div>
        `).join('');
    }

    el('billTotal').textContent = `₹${data.total.toFixed(2)}`;
    el('cashAmount').textContent = `₹${data.total.toFixed(0)}`;

    // Start UPI timer
    startUpiTimer();
}

// ── UPI Timer ──
let upiTimerInterval = null;
function startUpiTimer() {
    let secs = 300; // 5 minutes
    const timerEl = document.getElementById('upiTimer');
    if (!timerEl) return;
    clearInterval(upiTimerInterval);
    upiTimerInterval = setInterval(() => {
        secs--;
        if (secs <= 0) { clearInterval(upiTimerInterval); timerEl.textContent = '0:00'; return; }
        const m = Math.floor(secs / 60);
        const s = secs % 60;
        timerEl.textContent = `${m}:${s.toString().padStart(2, '0')}`;
    }, 1000);
}

// ── Checkout ──
async function doCheckout() {
    if (!sessionData) { showAlert('No session found', 'error'); return; }

    const method = document.querySelector('.pay-btn.selected')?.dataset?.method || 'UPI';

    showLoading('Processing payment...');

    // Simulate payment verification delay
    await new Promise(resolve => setTimeout(resolve, 2000));

    try {
        const res = await authPost(`${API}/api/checkout`, {
            sessionId: sessionData.sessionId,
            paymentMethod: method
        });
        const data = await res.json();
        hideLoading();

        if (!res.ok || data.error) {
            showAlert(data.error || 'Payment failed', 'error');
            return;
        }

        showExitPin(data);
    } catch (e) {
        hideLoading();
        showAlert('Network error. Try again.', 'error');
    }
}

function showExitPin(data) {
    const el = (id) => document.getElementById(id);

    el('billSection').classList.add('hidden');
    el('exitPinSection').classList.remove('hidden');

    // PIN digits
    el('exitPinDisplay').innerHTML = data.exitPin.split('').map(d =>
        `<span class="pin-digit">${d}</span>`
    ).join('');

    // Info grid
    el('exitInfoGrid').innerHTML = `
        <div class="info-item">
            <div class="label">Total Paid</div>
            <div class="value" style="color:var(--accent-green)">₹${data.total.toFixed(2)}</div>
        </div>
        <div class="info-item">
            <div class="label">Method</div>
            <div class="value">${data.paymentMethod}</div>
        </div>
        <div class="info-item">
            <div class="label">Parking Fee</div>
            <div class="value">₹${data.parkingFee.toFixed(2)}</div>
        </div>
        <div class="info-item">
            <div class="label">Fines</div>
            <div class="value">${data.fineTotal > 0 ? '₹' + data.fineTotal.toFixed(2) : 'None'}</div>
        </div>
    `;

    // Receipt
    el('receiptPre').textContent = data.receipt ? data.receipt.replace(/\\n/g, '\n') : '';

    window.scrollTo({ top: 0, behavior: 'smooth' });
}


// ═════════════════════════════════════════════════════════════
//  SHARED UTILITIES
// ═════════════════════════════════════════════════════════════
function formatPlate(plate) {
    if (!plate || plate.length < 6) return plate;
    // Try to format as XX-00-XX-0000
    const m = plate.match(/^([A-Z]{2})(\d{1,2})([A-Z]{0,3})(\d{1,4})$/);
    if (m) return `${m[1]}-${m[2]}-${m[3]}-${m[4]}`;
    return plate;
}

function formatDateTime(isoStr) {
    if (!isoStr) return '—';
    const d = new Date(isoStr);
    if (isNaN(d.getTime())) {
        // Handle Java LocalDateTime format "2026-04-20T14:32:00"
        const parts = isoStr.replace('T', ' ').substring(0, 16);
        return parts;
    }
    return d.toLocaleString('en-IN', {
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit', hour12: true
    });
}

function showAlert(msg, type = 'error') {
    const area = document.getElementById('alertArea');
    if (!area) return;
    const icon = type === 'error' ? '❌' : type === 'success' ? '✅' : '⚠️';
    area.innerHTML = `<div class="alert alert-${type}">${icon} ${msg}</div>`;
    setTimeout(() => { area.innerHTML = ''; }, 5000);
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function showLoading(msg) {
    const overlay = document.getElementById('loadingOverlay');
    if (!overlay) return;
    overlay.classList.remove('hidden');
    overlay.style.display = 'flex';
    const msgEl = document.getElementById('loadingMsg');
    if (msgEl) msgEl.textContent = msg || 'Processing...';
}

function hideLoading() {
    const overlay = document.getElementById('loadingOverlay');
    if (!overlay) return;
    overlay.classList.add('hidden');
    overlay.style.display = 'none';
}
