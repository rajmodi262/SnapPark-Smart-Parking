package com.snappark.service;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.snappark.dao.*;
import com.snappark.model.*;
import com.snappark.model.enums.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Embedded HTTP server that serves the mobile web pages and REST API.
 * Runs on port 8080 alongside the JavaFX kiosk application.
 */
public class WebServer {
    private static WebServer instance;
    private HttpServer server;
    private final int PORT = 8080;

    private final ParkingService    parkingService    = ParkingService.getInstance();
    private final BillingService    billingService    = BillingService.getInstance();
    private final SurgePricingService surgeService    = SurgePricingService.getInstance();
    private final FineService       fineService       = FineService.getInstance();
    private final PlateDecoderService plateDecoder    = PlateDecoderService.getInstance();
    private final SlotRecommendationService slotRec   = SlotRecommendationService.getInstance();
    private final HeatmapService    heatmapService    = HeatmapService.getInstance();
    private final VehicleDAO        vehicleDAO        = new VehicleDAO();
    private final SessionDAO        sessionDAO        = new SessionDAO();
    private final TransactionDAO    transactionDAO    = new TransactionDAO();
    private final FineDAO           fineDAO           = new FineDAO();

    private WebServer() {}

    public static WebServer getInstance() {
        if (instance == null) instance = new WebServer();
        return instance;
    }


    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // Root page — quick test that server is alive
            server.createContext("/", ex -> {
                try {
                    String ip = getLocalIP();
                    String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                        "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                        "<title>SnapPark Server</title>" +
                        "<style>body{background:#05080F;color:#E6EDF3;font-family:monospace;padding:40px;text-align:center}" +
                        "a{display:block;color:#00C2FF;font-size:20px;margin:20px 0;padding:16px;background:#0D1117;" +
                        "border:1px solid #00C2FF;border-radius:12px;text-decoration:none}" +
                        "a:hover{background:#161B22}h1{color:#00C2FF;font-size:32px}</style></head>" +
                        "<body><h1>🅿️ SnapPark Server</h1><p style='color:#00FF88'>✅ Server is running!</p>" +
                        "<a href='/mobile/entry.html'>📲 ENTRY — Park My Vehicle</a>" +
                        "<a href='/mobile/exit.html'>🏁 EXIT — Check Out</a>" +
                        "<a href='/mobile/history.html'>🎫 HISTORY — View Tickets</a>" +
                        "<p style='color:#8B949E;font-size:12px;margin-top:30px'>IP: " + ip + ":" + PORT + "</p>" +
                        "</body></html>";
                    byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    ex.sendResponseHeaders(200, bytes.length);
                    OutputStream os = ex.getResponseBody();
                    os.write(bytes);
                    os.flush();
                    os.close();
                } catch (Exception e) {
                    System.err.println("[WebServer] Root handler error: " + e.getMessage());
                }
            });

            // Static mobile pages
            server.createContext("/mobile/", this::handleStaticFile);

            // REST API endpoints
            server.createContext("/api/slots", this::handleGetSlots);
            server.createContext("/api/slots/recommend", this::handleRecommend);
            server.createContext("/api/validate-plate", this::handleValidatePlate);
            server.createContext("/api/rates", this::handleGetRates);
            server.createContext("/api/checkin", this::handleCheckin);
            server.createContext("/api/lookup-session", this::handleLookupSession);
            server.createContext("/api/checkout", this::handleCheckout);
            server.createContext("/api/verify-pin", this::handleVerifyPin);
            server.createContext("/api/history", this::handleHistory);

            // Use cached thread pool — no thread limit, no starvation
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();

            String ip = getLocalIP();
            System.out.println("========================================");
            System.out.println("  SnapPark WebServer RUNNING on port " + PORT);
            System.out.println("  Local:   http://localhost:" + PORT);
            System.out.println("  LAN:     http://" + ip + ":" + PORT);
            System.out.println("  Entry:   http://" + ip + ":" + PORT + "/mobile/entry.html");
            System.out.println("  Exit:    http://" + ip + ":" + PORT + "/mobile/exit.html");
            System.out.println("  History: http://" + ip + ":" + PORT + "/mobile/history.html");
            System.out.println("========================================");
        } catch (java.net.BindException e) {
            System.err.println("PORT 8080 IS ALREADY IN USE! Close the other app or kill the process.");
            System.err.println("Run: netstat -ano | findstr 8080");
        } catch (IOException e) {
            System.err.println("WebServer failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // ─── Public URL support (ngrok) ─────────────────────────────
    private String publicBaseUrl = null;

    public void setPublicUrl(String url) {
        this.publicBaseUrl = url;
        System.out.println("========================================");
        System.out.println("  🌐 PUBLIC URL SET: " + url);
        System.out.println("  Entry:   " + url + "/mobile/entry.html");
        System.out.println("  Exit:    " + url + "/mobile/exit.html");
        System.out.println("  History: " + url + "/mobile/history.html");
        System.out.println("  QR codes will now use this public URL!");
        System.out.println("========================================");
    }

    public String getBaseURL() {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) return publicBaseUrl;
        return "http://" + getLocalIP() + ":" + PORT;
    }

    public String getEntryURL() {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return "https://snappark-web.vercel.app/entry.html?api=" + publicBaseUrl;
        }
        return getBaseURL() + "/mobile/entry.html";
    }

    public String getExitURL() {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return "https://snappark-web.vercel.app/exit.html?api=" + publicBaseUrl;
        }
        return getBaseURL() + "/mobile/exit.html";
    }

    public String getLocalIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) { /* fallback */ }
        return "localhost";
    }

    // ══════════════════════════════════════════════════════════════════
    //  STATIC FILE SERVER (serves mobile HTML/CSS/JS)
    // ══════════════════════════════════════════════════════════════════
    private void handleStaticFile(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath(); // e.g. /mobile/entry.html
            System.out.println("[WebServer] Static request: " + path);

            // Determine content type
            String contentType = "text/html; charset=UTF-8";
            if (path.endsWith(".css"))  contentType = "text/css; charset=UTF-8";
            else if (path.endsWith(".js"))   contentType = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".png"))  contentType = "image/png";
            else if (path.endsWith(".json")) contentType = "application/json; charset=UTF-8";

            // Strategy 1: Try filesystem (works during development with mvn javafx:run)
            byte[] data = null;
            java.io.File fsFile = new java.io.File("target/classes/com/snappark" + path);
            if (!fsFile.exists()) {
                // Try relative to where the JAR/class might be
                fsFile = new java.io.File("src/main/resources/com/snappark" + path);
            }
            if (fsFile.exists() && fsFile.isFile()) {
                data = java.nio.file.Files.readAllBytes(fsFile.toPath());
                System.out.println("[WebServer] Served from filesystem: " + fsFile.getAbsolutePath() + " (" + data.length + " bytes)");
            }

            // Strategy 2: Classpath resource
            if (data == null) {
                String resource = "/com/snappark" + path;
                InputStream is = getClass().getResourceAsStream(resource);
                if (is != null) {
                    data = is.readAllBytes();
                    is.close();
                    System.out.println("[WebServer] Served from classpath: " + resource + " (" + data.length + " bytes)");
                }
            }

            if (data == null) {
                System.out.println("[WebServer] NOT FOUND: " + path);
                String notFound = "{\"error\":\"Not found: " + path + "\"}";
                byte[] nfBytes = notFound.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                ex.sendResponseHeaders(404, nfBytes.length);
                OutputStream os = ex.getResponseBody();
                os.write(nfBytes);
                os.flush();
                os.close();
                return;
            }

            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, data.length);
            OutputStream os = ex.getResponseBody();
            os.write(data);
            os.flush();
            os.close();
        } catch (Exception e) {
            System.err.println("[WebServer] Static file error: " + e.getMessage());
            e.printStackTrace();
            try {
                String err = "{\"error\":\"Server error\"}";
                byte[] errBytes = err.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(500, errBytes.length);
                OutputStream os = ex.getResponseBody();
                os.write(errBytes);
                os.flush();
                os.close();
            } catch (Exception ignored) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /api/slots — returns all slots with status + heatmap data
    // ══════════════════════════════════════════════════════════════════
    private void handleGetSlots(HttpExchange ex) throws IOException {
        setCors(ex);
        if (handlePreflight(ex)) return;
        List<ParkingSlot> slots = parkingService.getAllSlots();
        Map<Integer, Integer> heatmap = heatmapService.getAllTimeBookings();
        int maxHeat = heatmapService.getMaxCount(heatmap);

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < slots.size(); i++) {
            ParkingSlot s = slots.get(i);
            int heat = heatmap.getOrDefault(s.getId(), 0);
            if (i > 0) json.append(",");
            json.append(String.format(
                "{\"id\":%d,\"slotNumber\":\"%s\",\"floor\":%d,\"type\":\"%s\",\"status\":\"%s\",\"heatCount\":%d,\"heatMax\":%d}",
                s.getId(), s.getSlotNumber(), s.getFloor(), s.getType().name(), s.getStatus().name(), heat, maxHeat
            ));
        }
        json.append("]");
        sendResponse(ex, 200, json.toString());
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /api/slots/recommend?type=CAR&floor=1
    // ══════════════════════════════════════════════════════════════════
    private void handleRecommend(HttpExchange ex) throws IOException {
        setCors(ex);
        if (handlePreflight(ex)) return;
        Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
        VehicleType vt = VehicleType.valueOf(params.getOrDefault("type", "CAR"));
        int floor = Integer.parseInt(params.getOrDefault("floor", "1"));

        List<SlotRecommendationService.ScoredSlot> recs = slotRec.recommend(parkingService.getAllSlots(), vt, floor);
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < recs.size(); i++) {
            var r = recs.get(i);
            if (i > 0) json.append(",");
            json.append(String.format(
                "{\"slotId\":%d,\"slotNumber\":\"%s\",\"floor\":%d,\"type\":\"%s\",\"score\":%d,\"reason\":\"%s\"}",
                r.slot.getId(), r.slot.getSlotNumber(), r.slot.getFloor(), r.slot.getType().name(),
                r.totalScore, r.reason.replace("\"", "'")
            ));
        }
        json.append("]");
        sendResponse(ex, 200, json.toString());
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /api/validate-plate?plate=MH12AB1234
    // ══════════════════════════════════════════════════════════════════
    private void handleValidatePlate(HttpExchange ex) throws IOException {
        setCors(ex);
        if (handlePreflight(ex)) return;
        Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
        String plate = params.getOrDefault("plate", "");
        var result = plateDecoder.decode(plate);
        String json;
        if (result.valid) {
            json = String.format("{\"valid\":true,\"formatted\":\"%s\",\"state\":\"%s\",\"rto\":\"%s\",\"stateColor\":\"%s\"}",
                result.formatted, result.state, result.rto, result.stateColor);
        } else {
            String hint = plateDecoder.getStateHint(plate);
            json = String.format("{\"valid\":false,\"error\":\"%s\",\"hint\":\"%s\"}",
                result.error.replace("\"", "'"), hint);
        }
        sendResponse(ex, 200, json);
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /api/rates — current pricing with surge info
    // ══════════════════════════════════════════════════════════════════
    private void handleGetRates(HttpExchange ex) throws IOException {
        setCors(ex);
        if (handlePreflight(ex)) return;
        long occ = parkingService.getOccupiedCount();
        long total = parkingService.getAllSlots().size();
        var bike = surgeService.getBreakdown(VehicleType.BIKE, occ, total);
        var car = surgeService.getBreakdown(VehicleType.CAR, occ, total);
        var suv = surgeService.getBreakdown(VehicleType.SUV, occ, total);

        String json = String.format(
            "{\"bike\":{\"base\":%.0f,\"effective\":%.0f,\"surge\":%s}," +
            "\"car\":{\"base\":%.0f,\"effective\":%.0f,\"surge\":%s}," +
            "\"suv\":{\"base\":%.0f,\"effective\":%.0f,\"surge\":%s}," +
            "\"timeLabel\":\"%s\",\"occupancyLabel\":\"%s\"," +
            "\"available\":%d,\"occupied\":%d,\"total\":%d,\"occupancyPct\":%.0f," +
            "\"isSurge\":%s,\"surgePercent\":%d}",
            bike.baseRate, bike.effectiveRate, bike.isSurge,
            car.baseRate, car.effectiveRate, car.isSurge,
            suv.baseRate, suv.effectiveRate, suv.isSurge,
            car.timeLabel.replace("\"", "'"), car.occupancyLabel.replace("\"", "'"),
            total - occ, occ, total, (double) occ / total * 100,
            car.isSurge, car.getSurgePercent()
        );
        sendResponse(ex, 200, json);
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /api/checkin — mobile check-in
    //  Body: {"phone":"9876543210","plate":"MH12AB1234","type":"CAR","slotId":7}
    // ══════════════════════════════════════════════════════════════════
    private void handleCheckin(HttpExchange ex) throws IOException {
        setCors(ex);
        if (handlePreflight(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) { sendResponse(ex, 405, "{\"error\":\"POST only\"}"); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseJsonFlat(body);

        String phone = data.getOrDefault("phone", "").replaceAll("[^0-9]", "");
        String plate = data.getOrDefault("plate", "").toUpperCase().replaceAll("[\\s\\-]", "");
        String typeStr = data.getOrDefault("type", "CAR");
        int slotId = Integer.parseInt(data.getOrDefault("slotId", "0"));

        // Validations
        if (phone.length() < 10) { sendResponse(ex, 400, "{\"error\":\"Invalid phone number\"}"); return; }
        if (plate.length() < 6) { sendResponse(ex, 400, "{\"error\":\"Invalid vehicle number\"}"); return; }
        if (slotId <= 0) { sendResponse(ex, 400, "{\"error\":\"Select a parking slot\"}"); return; }

        // Check existing active session for this plate
        ParkingSession existing = sessionDAO.findActiveByPlate(plate);
        if (existing != null) {
            ParkingSlot existSlot = parkingService.getAllSlots().stream().filter(s->s.getId()==existing.getSlotId()).findFirst().orElse(null);
            sendResponse(ex, 409, String.format("{\"error\":\"This vehicle already has an active session at slot %s\"}",
                existSlot != null ? existSlot.getSlotNumber() : "?"));
            return;
        }

        // Lock slot
        boolean locked = parkingService.lockSlot(slotId, 0); // userId=0 for mobile/kiosk user
        if (!locked) { sendResponse(ex, 409, "{\"error\":\"Slot already taken! Choose another.\"}"); return; }

        // Save vehicle
        VehicleType vt = VehicleType.valueOf(typeStr);
        Vehicle vehicle = new Vehicle(0, plate, phone, vt, phone); // ownerName=phone for mobile users
        vehicle = vehicleDAO.save(vehicle);

        // Confirm booking
        parkingService.confirmBooking(slotId, 0);

        // Create session
        ParkingSession session = sessionDAO.startSession(vehicle.getId(), slotId, 0, phone);
        if (session == null) {
            sendResponse(ex, 500, "{\"error\":\"Failed to create parking session\"}");
            return;
        }

        // Get slot info
        ParkingSlot slot = parkingService.getAllSlots().stream().filter(s->s.getId()==slotId).findFirst().orElse(null);
        String slotNumber = slot != null ? slot.getSlotNumber() : "?";

        // Get rate info
        var breakdown = surgeService.getBreakdown(vt, parkingService.getOccupiedCount(), parkingService.getAllSlots().size());

        String json = String.format(
            "{\"success\":true,\"sessionId\":%d,\"sessionPin\":\"%s\"," +
            "\"slotNumber\":\"%s\",\"floor\":%d,\"vehicleType\":\"%s\"," +
            "\"plate\":\"%s\",\"phone\":\"%s\"," +
            "\"entryTime\":\"%s\",\"rate\":%.0f,\"rateLabel\":\"%s\"," +
            "\"isSurge\":%s,\"surgePercent\":%d," +
            "\"fineWarning\":\"Park at %s ONLY. Wrong slot = Rs.%.0f fine\"}",
            session.getId(), session.getSessionPin(),
            slotNumber, slot != null ? slot.getFloor() : 0, vt.name(),
            plate, phone,
            session.getEntryTime().toString(), breakdown.effectiveRate,
            breakdown.isSurge ? breakdown.timeLabel : "Standard",
            breakdown.isSurge, breakdown.getSurgePercent(),
            slotNumber, fineService.getWrongParkingFineAmount()
        );
        sendResponse(ex, 200, json);
        System.out.println("CHECK-IN: " + plate + " -> " + slotNumber + " PIN=" + session.getSessionPin());
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /api/lookup-session — find active session by plate or PIN
    //  Body: {"plate":"MH12AB1234"} or {"pin":"4729"}
    // ══════════════════════════════════════════════════════════════════
    private void handleLookupSession(HttpExchange ex) throws IOException {
        setCors(ex);
        if (handlePreflight(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) { sendResponse(ex, 405, "{\"error\":\"POST only\"}"); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseJsonFlat(body);

        String plate = data.getOrDefault("plate", "").toUpperCase().replaceAll("[\\s\\-]", "");
        String pin = data.getOrDefault("pin", "");

        ParkingSession session = null;
        if (!pin.isBlank()) {
            session = sessionDAO.findActiveBySessionPin(pin);
        }
        if (session == null && !plate.isBlank()) {
            session = sessionDAO.findActiveByPlate(plate);
        }
        if (session == null) {
            sendResponse(ex, 404, "{\"error\":\"No active session found. Check your vehicle number or PIN.\"}");
            return;
        }

        // Build bill — use final reference for lambdas
        final ParkingSession sess = session;
        Vehicle vehicle = vehicleDAO.findAll().stream().filter(v->v.getId()==sess.getVehicleId()).findFirst().orElse(null);
        ParkingSlot slot = parkingService.getAllSlots().stream().filter(s->s.getId()==sess.getSlotId()).findFirst().orElse(null);
        LocalDateTime now = LocalDateTime.now();
        Duration dur = Duration.between(session.getEntryTime(), now);

        double parkingFee = billingService.calculateFee(
            vehicle != null ? vehicle.getVehicleType() : VehicleType.CAR,
            session.getEntryTime(), now
        );
        double fineTotal = fineService.getUnpaidTotal(session.getId());
        List<Fine> fines = fineService.getUnpaidFines(session.getId());

        // Plate decode info
        var plateResult = vehicle != null ? plateDecoder.decode(vehicle.getLicensePlate()) : null;

        StringBuilder finesJson = new StringBuilder("[");
        for (int i = 0; i < fines.size(); i++) {
            Fine f = fines.get(i);
            if (i > 0) finesJson.append(",");
            finesJson.append(String.format(
                "{\"type\":\"%s\",\"label\":\"%s\",\"amount\":%.2f,\"assignedSlot\":\"%s\",\"actualSlot\":\"%s\",\"notes\":\"%s\"}",
                f.getFineType().name(), f.getFineTypeLabel(), f.getAmount(),
                f.getAssignedSlot() != null ? f.getAssignedSlot() : "",
                f.getActualSlot() != null ? f.getActualSlot() : "",
                f.getNotes() != null ? f.getNotes().replace("\"", "'") : ""
            ));
        }
        finesJson.append("]");

        // Rate breakdown
        var breakdown = surgeService.getBreakdown(
            vehicle != null ? vehicle.getVehicleType() : VehicleType.CAR,
            parkingService.getOccupiedCount(),
            parkingService.getAllSlots().size(),
            session.getEntryTime().toLocalTime()
        );

        String json = String.format(
            "{\"sessionId\":%d,\"plate\":\"%s\",\"phone\":\"%s\"," +
            "\"vehicleType\":\"%s\",\"slotNumber\":\"%s\",\"floor\":%d," +
            "\"entryTime\":\"%s\",\"currentTime\":\"%s\"," +
            "\"durationHours\":%d,\"durationMinutes\":%d," +
            "\"baseRate\":%.0f,\"effectiveRate\":%.0f,\"surgePercent\":%d,\"isSurge\":%s," +
            "\"timeLabel\":\"%s\",\"occupancyLabel\":\"%s\"," +
            "\"parkingFee\":%.2f,\"fineTotal\":%.2f,\"total\":%.2f," +
            "\"fines\":%s," +
            "\"stateName\":\"%s\",\"rtoName\":\"%s\",\"stateColor\":\"%s\"," +
            "\"sessionPin\":\"%s\",\"graceApplied\":%s}",
            session.getId(),
            vehicle != null ? vehicle.getLicensePlate() : plate,
            session.getPhoneNumber(),
            vehicle != null ? vehicle.getVehicleType().name() : "CAR",
            slot != null ? slot.getSlotNumber() : "?",
            slot != null ? slot.getFloor() : 0,
            session.getEntryTime().toString(),
            now.toString(),
            dur.toHours(), dur.toMinutesPart(),
            breakdown.baseRate, breakdown.effectiveRate, breakdown.getSurgePercent(), breakdown.isSurge,
            breakdown.timeLabel.replace("\"","'"), breakdown.occupancyLabel.replace("\"","'"),
            parkingFee, fineTotal, parkingFee + fineTotal,
            finesJson.toString(),
            plateResult != null && plateResult.valid ? plateResult.state : "",
            plateResult != null && plateResult.valid ? plateResult.rto : "",
            plateResult != null && plateResult.valid ? plateResult.stateColor : "#334155",
            session.getSessionPin() != null ? session.getSessionPin() : "",
            parkingFee == 0.0
        );
        sendResponse(ex, 200, json);
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /api/checkout — process payment, generate exit PIN
    //  Body: {"sessionId":5,"paymentMethod":"UPI"}
    // ══════════════════════════════════════════════════════════════════
    private void handleCheckout(HttpExchange ex) throws IOException {
        setCors(ex);
        if (handlePreflight(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) { sendResponse(ex, 405, "{\"error\":\"POST only\"}"); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseJsonFlat(body);

        int sessionId = Integer.parseInt(data.getOrDefault("sessionId", "0"));
        String paymentMethod = data.getOrDefault("paymentMethod", "UPI");

        ParkingSession session = sessionDAO.findById(sessionId);
        if (session == null || !session.isActive()) {
            sendResponse(ex, 404, "{\"error\":\"Session not found or already closed\"}");
            return;
        }

        Vehicle vehicle = vehicleDAO.findAll().stream().filter(v->v.getId()==session.getVehicleId()).findFirst().orElse(null);
        ParkingSlot slot = parkingService.getAllSlots().stream().filter(s->s.getId()==session.getSlotId()).findFirst().orElse(null);
        LocalDateTime now = LocalDateTime.now();

        double parkingFee = billingService.calculateFee(
            vehicle != null ? vehicle.getVehicleType() : VehicleType.CAR,
            session.getEntryTime(), now
        );
        double fineTotal = fineService.getUnpaidTotal(sessionId);
        double total = parkingFee + fineTotal;

        // Generate 6-digit exit PIN
        String exitPin = sessionDAO.generateExitPin();
        sessionDAO.setExitPin(sessionId, exitPin);

        // Mark fines as paid
        if (fineTotal > 0) fineService.markAllPaid(sessionId);

        // Create receipt
        String receipt = billingService.generateReceipt(
            vehicle != null ? vehicle.getLicensePlate() : "?",
            slot != null ? slot.getSlotNumber() : "?",
            session.getEntryTime(), now,
            parkingFee, 0, paymentMethod,
            fineTotal, session.getSessionPin(), exitPin
        );

        // Save transaction
        Transaction t = new Transaction();
        t.setSessionId(sessionId);
        t.setAmount(parkingFee);
        t.setDiscount(0);
        t.setPaymentTime(now);
        t.setReceipt(receipt);
        t.setPaymentMethod(paymentMethod);
        t.setFineAmount(fineTotal);
        t.setExitPin(exitPin);
        t.setSessionPin(session.getSessionPin());
        transactionDAO.save(t);

        String json = String.format(
            "{\"success\":true,\"exitPin\":\"%s\",\"total\":%.2f," +
            "\"parkingFee\":%.2f,\"fineTotal\":%.2f," +
            "\"paymentMethod\":\"%s\",\"receipt\":\"%s\"}",
            exitPin, total, parkingFee, fineTotal, paymentMethod,
            receipt.replace("\"", "'").replace("\n", "\\n")
        );
        sendResponse(ex, 200, json);
        System.out.println("CHECK-OUT: Session #" + sessionId + " EXIT PIN=" + exitPin + " Total=Rs." + total);
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /api/verify-pin — kiosk agent verifies exit PIN
    //  Body: {"pin":"837291"}
    // ══════════════════════════════════════════════════════════════════
    private void handleVerifyPin(HttpExchange ex) throws IOException {
        setCors(ex);
        if (handlePreflight(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) { sendResponse(ex, 405, "{\"error\":\"POST only\"}"); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseJsonFlat(body);
        String pin = data.getOrDefault("pin", "");

        if (pin.length() != 6) {
            sendResponse(ex, 400, "{\"valid\":false,\"error\":\"Enter a 6-digit exit PIN\"}");
            return;
        }

        ParkingSession session = sessionDAO.findByExitPin(pin);
        if (session == null) {
            sendResponse(ex, 404, "{\"valid\":false,\"error\":\"Invalid PIN. Please check and try again.\"}");
            return;
        }

        // End session and release slot
        sessionDAO.endSession(session.getId(), LocalDateTime.now());
        parkingService.releaseSlot(session.getSlotId());

        Vehicle vehicle = vehicleDAO.findAll().stream().filter(v->v.getId()==session.getVehicleId()).findFirst().orElse(null);
        ParkingSlot slot = parkingService.getAllSlots().stream().filter(s->s.getId()==session.getSlotId()).findFirst().orElse(null);

        String json = String.format(
            "{\"valid\":true,\"message\":\"Verified! Barrier opened.\"," +
            "\"plate\":\"%s\",\"slotNumber\":\"%s\"}",
            vehicle != null ? vehicle.getLicensePlate() : "?",
            slot != null ? slot.getSlotNumber() : "?"
        );
        sendResponse(ex, 200, json);
        System.out.println("EXIT VERIFIED: PIN=" + pin + " Plate=" + (vehicle != null ? vehicle.getLicensePlate() : "?"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /api/history?phone=9876543210 — ticket history by phone
    // ══════════════════════════════════════════════════════════════════
    private void handleHistory(HttpExchange ex) throws IOException {
        setCors(ex);
        if (handlePreflight(ex)) return;
        Map<String, String> params = parseQuery(ex.getRequestURI().getQuery());
        String phone = params.getOrDefault("phone", "").replaceAll("[^0-9]", "");

        if (phone.length() < 10) {
            sendResponse(ex, 400, "{\"error\":\"Enter a valid 10-digit phone number\"}");
            return;
        }

        List<ParkingSession> sessions = sessionDAO.findByPhone(phone);
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < sessions.size(); i++) {
            ParkingSession s = sessions.get(i);
            Vehicle vehicle = vehicleDAO.findAll().stream()
                .filter(v -> v.getId() == s.getVehicleId()).findFirst().orElse(null);
            ParkingSlot slot = parkingService.getAllSlots().stream()
                .filter(sl -> sl.getId() == s.getSlotId()).findFirst().orElse(null);

            String plate = vehicle != null ? vehicle.getLicensePlate() : "?";
            String slotNum = slot != null ? slot.getSlotNumber() : "?";
            String vType = vehicle != null ? vehicle.getVehicleType().name() : "CAR";
            String entry = s.getEntryTime() != null ? s.getEntryTime().toString() : "";
            String exit = s.getExitTime() != null ? s.getExitTime().toString() : "";
            long durMin = 0;
            if (s.getEntryTime() != null) {
                java.time.LocalDateTime end = s.getExitTime() != null ? s.getExitTime() : java.time.LocalDateTime.now();
                durMin = Duration.between(s.getEntryTime(), end).toMinutes();
            }

            if (i > 0) json.append(",");
            json.append(String.format(
                "{\"sessionId\":%d,\"plate\":\"%s\",\"vehicleType\":\"%s\"," +
                "\"slotNumber\":\"%s\",\"entryTime\":\"%s\",\"exitTime\":\"%s\"," +
                "\"durationMin\":%d,\"active\":%s,\"paymentStatus\":\"%s\"}",
                s.getId(), plate, vType, slotNum, entry, exit,
                durMin, s.isActive(), s.getPaymentStatus()
            ));
        }
        json.append("]");
        sendResponse(ex, 200, json.toString());
    }

    // ══════════════════════════════════════════════════════════════════
    //  UTILITY METHODS
    // ══════════════════════════════════════════════════════════════════
    private void sendResponse(HttpExchange ex, int code, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private void setCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, ngrok-skip-browser-warning");
    }

    private boolean handlePreflight(HttpExchange ex) throws IOException {
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try { params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8)); }
                catch (Exception e) { params.put(kv[0], kv[1]); }
            }
        }
        return params;
    }

    /** Simple flat JSON parser (handles {"key":"value","key2":"value2"} and {"key":123}) */
    private Map<String, String> parseJsonFlat(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length()-1);
        // Split by comma, but not inside quotes
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        List<String> pairs = new ArrayList<>();
        for (char c : json.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) { pairs.add(current.toString()); current = new StringBuilder(); continue; }
            current.append(c);
        }
        if (!current.isEmpty()) pairs.add(current.toString());
        for (String pair : pairs) {
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String key = pair.substring(0, colon).trim().replace("\"","");
            String val = pair.substring(colon+1).trim().replace("\"","");
            map.put(key, val);
        }
        return map;
    }
}
