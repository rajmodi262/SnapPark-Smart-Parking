package com.snappark.controller;

import com.snappark.model.ParkingSession;
import com.snappark.model.ParkingSlot;
import com.snappark.model.User;
import com.snappark.model.Vehicle;
import com.snappark.dao.SessionDAO;
import com.snappark.dao.VehicleDAO;
import com.snappark.service.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

public class KioskWelcomeController implements Initializable {

    @FXML private Canvas    bgCanvas;
    @FXML private StackPane rootPane;
    @FXML private Label     clockLabel;
    @FXML private Label     slotsLabel;
    @FXML private Label     ratesLabel;
    @FXML private Label     greetingLabel;
    @FXML private Label     contextLabel;
    @FXML private Label     surgeLabel;
    @FXML private ImageView entryQrImage;
    @FXML private ImageView exitQrImage;
    @FXML private Label     entryUrlLabel;
    @FXML private Label     exitUrlLabel;
    @FXML private TextField exitPinField;
    @FXML private Label     verifyStatus;
    @FXML private Pane      barrierPane;
    @FXML private Rectangle barrierArm;
    @FXML private Circle    barrierPivot;
    @FXML private Circle    barrierLight;

    private final AuthService         authService    = AuthService.getInstance();
    private final ParkingService      parkingService = ParkingService.getInstance();
    private final WebServer           webServer      = WebServer.getInstance();
    private final SurgePricingService surgeService   = SurgePricingService.getInstance();
    private final SoundService        soundService   = SoundService.getInstance();
    private final SessionDAO          sessionDAO     = new SessionDAO();
    private final VehicleDAO          vehicleDAO     = new VehicleDAO();
    private final QRCodeService       qrService      = QRCodeService.getInstance();
    private Timeline gridAnim;
    private double   offset   = 0;
    private int      tapCount = 0;
    private long     lastTap  = 0;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private enum TimeMode { DAWN, MORNING, AFTERNOON, EVENING, NIGHT }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Platform.runLater(() -> {
            bgCanvas.widthProperty().bind(rootPane.widthProperty());
            bgCanvas.heightProperty().bind(rootPane.heightProperty());
            startBg();
            startClock();
            applyTimeMode();
            generateQRCodes();
            updateRates();
        });
    }

    // ─── QR Code Generation ─────────────────────────────────────
    private void generateQRCodes() {
        try {
            String entryUrl = webServer.getEntryURL();
            String exitUrl  = webServer.getExitURL();

            System.out.println("[Kiosk] Entry QR URL: " + entryUrl);
            System.out.println("[Kiosk] Exit  QR URL: " + exitUrl);

            byte[] entryBytes = qrService.generateQRBytes(entryUrl, 200);
            byte[] exitBytes  = qrService.generateQRBytes(exitUrl, 200);

            if (entryBytes != null && entryQrImage != null) {
                entryQrImage.setImage(new Image(new ByteArrayInputStream(entryBytes)));
                System.out.println("[Kiosk] Entry QR generated OK (" + entryBytes.length + " bytes)");
            }
            if (exitBytes != null && exitQrImage != null) {
                exitQrImage.setImage(new Image(new ByteArrayInputStream(exitBytes)));
                System.out.println("[Kiosk] Exit QR generated OK (" + exitBytes.length + " bytes)");
            }
            if (entryUrlLabel != null) entryUrlLabel.setText(entryUrl);
            if (exitUrlLabel  != null) exitUrlLabel.setText(exitUrl);
        } catch (Exception e) {
            System.err.println("QR generation failed: " + e.getMessage());
        }
    }

    // ─── Rates Display ──────────────────────────────────────────
    private void updateRates() {
        if (ratesLabel == null) return;
        long occ = cachedOccupied;
        long total = cachedTotal;
        var bike = surgeService.getBreakdown(com.snappark.model.enums.VehicleType.BIKE, occ, total);
        var car  = surgeService.getBreakdown(com.snappark.model.enums.VehicleType.CAR, occ, total);
        var suv  = surgeService.getBreakdown(com.snappark.model.enums.VehicleType.SUV, occ, total);
        ratesLabel.setText(String.format("🏍₹%.0f  🚗₹%.0f  🚙₹%.0f /hr",
            bike.effectiveRate, car.effectiveRate, suv.effectiveRate));
    }

    // ─── PIN Verification ───────────────────────────────────────
    @FXML
    public void handleVerifyPin() {
        if (exitPinField == null || verifyStatus == null) return;
        String pin = exitPinField.getText().trim();

        if (pin.length() != 6) {
            setVerifyStatus("Enter a 6-digit PIN", "#FF2244");
            shakeField(exitPinField);
            soundService.alert();
            return;
        }

        soundService.beep();

        ParkingSession session = sessionDAO.findByExitPin(pin);
        if (session == null) {
            setVerifyStatus("❌ Invalid PIN. Check and try again.", "#FF2244");
            shakeField(exitPinField);
            soundService.alert();
            return;
        }

        // End session and release slot
        sessionDAO.endSession(session.getId(), LocalDateTime.now());
        parkingService.releaseSlot(session.getSlotId());

        Vehicle vehicle = vehicleDAO.findAll().stream()
            .filter(v -> v.getId() == session.getVehicleId()).findFirst().orElse(null);
        ParkingSlot slot = parkingService.getAllSlots().stream()
            .filter(s -> s.getId() == session.getSlotId()).findFirst().orElse(null);

        String plate = vehicle != null ? vehicle.getLicensePlate() : "?";
        String slotNum = slot != null ? slot.getSlotNumber() : "?";

        setVerifyStatus("✅ VERIFIED! " + plate + " from " + slotNum + " — Barrier OPEN!", "#00FF88");
        exitPinField.clear();

        // Play success chime + barrier sound
        soundService.chime();

        // Animate barrier
        animateBarrierOpen();

        System.out.println("KIOSK EXIT VERIFIED: PIN=" + pin + " Plate=" + plate + " Slot=" + slotNum);
    }

    @FXML
    public void handleClearPin() {
        if (exitPinField != null) exitPinField.clear();
        if (verifyStatus != null) verifyStatus.setText("");
    }

    private void setVerifyStatus(String text, String color) {
        verifyStatus.setText(text);
        verifyStatus.setStyle("-fx-font-size:12px;-fx-font-family:monospace;-fx-font-weight:bold;-fx-text-fill:" + color + ";");
    }

    private void shakeField(TextField field) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(60), field);
        tt.setFromX(0); tt.setByX(10);
        tt.setCycleCount(6); tt.setAutoReverse(true);
        tt.play();
    }

    // ─── Barrier Lift Animation ─────────────────────────────────
    private void animateBarrierOpen() {
        if (barrierArm == null || barrierPivot == null || barrierLight == null) return;

        // Show barrier pane
        if (barrierPane != null) barrierPane.setVisible(true);

        // Flash light to green
        barrierLight.setFill(Color.web("#00FF88"));

        // Rotate arm up (-90 degrees = open)
        RotateTransition rotateUp = new RotateTransition(Duration.millis(800), barrierArm);
        rotateUp.setFromAngle(0);
        rotateUp.setToAngle(-90);
        rotateUp.setInterpolator(Interpolator.EASE_BOTH);

        // Play barrier sound slightly delayed
        PauseTransition soundDelay = new PauseTransition(Duration.millis(200));
        soundDelay.setOnFinished(e -> soundService.barrierOpen());

        // Hold open for 4 seconds
        PauseTransition hold = new PauseTransition(Duration.seconds(4));

        // Rotate arm back down
        RotateTransition rotateDown = new RotateTransition(Duration.millis(600), barrierArm);
        rotateDown.setFromAngle(-90);
        rotateDown.setToAngle(0);
        rotateDown.setInterpolator(Interpolator.EASE_IN);

        // Flash light back to red
        rotateDown.setOnFinished(e -> {
            barrierLight.setFill(Color.web("#FF2244"));
            // Hide after a brief moment
            PauseTransition hideDelay = new PauseTransition(Duration.millis(500));
            hideDelay.setOnFinished(ev -> {
                if (barrierPane != null) barrierPane.setVisible(false);
                verifyStatus.setText("");
            });
            hideDelay.play();
        });

        // Chain: sound -> rotateUp -> hold -> rotateDown
        SequentialTransition seq = new SequentialTransition(soundDelay, rotateUp, hold, rotateDown);
        seq.play();
    }

    // ─── Time-based theming ─────────────────────────────────────
    private TimeMode getTimeMode() {
        int h = LocalTime.now().getHour();
        if (h >= 5  && h < 8)  return TimeMode.DAWN;
        if (h >= 8  && h < 12) return TimeMode.MORNING;
        if (h >= 12 && h < 17) return TimeMode.AFTERNOON;
        if (h >= 17 && h < 21) return TimeMode.EVENING;
        return TimeMode.NIGHT;
    }

    private void applyTimeMode() {
        TimeMode mode = getTimeMode();
        long avail = cachedAvailable;
        long total = cachedTotal;
        if (total == 0) total = 30;
        int  pct   = (int)(((total - avail) * 100) / total);
        boolean isSurge = (mode == TimeMode.MORNING || mode == TimeMode.EVENING);
        boolean isFull  = avail <= 3;

        String greeting = switch (mode) {
            case DAWN      -> "Good morning!  The city wakes up.";
            case MORNING   -> "Good morning!  Peak hours ahead.";
            case AFTERNOON -> "Good afternoon!  Smooth parking today.";
            case EVENING   -> "Good evening!  Rush hour is here.";
            case NIGHT     -> "Good night!  Quiet hours.";
        };
        String context;
        if (isFull)       context = "HURRY!  Only " + avail + " slots left!";
        else if (isSurge) context = "Peak demand:  " + pct + "% full  //  surge pricing active";
        else              context = avail + " of " + total + " slots available  //  " + pct + "% occupied";

        String surge = "";
        if      (mode == TimeMode.MORNING) surge = "PEAK HOURS  8AM-12PM  //  +20% RATES APPLY";
        else if (mode == TimeMode.EVENING) surge = "RUSH HOUR  5PM-9PM  //  +30% RATES APPLY";
        else if (mode == TimeMode.NIGHT)   surge = "NIGHT DISCOUNT  //  -10% OFF ALL SLOTS";

        if (greetingLabel != null) {
            greetingLabel.setText(greeting);
            greetingLabel.setStyle("-fx-font-size:16px;-fx-font-family:monospace;-fx-font-weight:bold;-fx-text-fill:" + greetingColor(mode) + ";");
        }
        if (contextLabel != null) {
            String ctxColor = isFull ? "#FF2244" : isSurge ? "#FFB800" : "#64748b";
            contextLabel.setText(context);
            contextLabel.setStyle("-fx-text-fill:" + ctxColor + ";-fx-font-family:monospace;-fx-font-size:11px;");
        }
        if (surgeLabel != null) {
            surgeLabel.setText(surge);
            surgeLabel.setVisible(!surge.isEmpty());
            surgeLabel.setManaged(!surge.isEmpty());
            String sc = (mode == TimeMode.NIGHT) ? "#00FF88" : "#FF2244";
            String sb = (mode == TimeMode.NIGHT) ? "#001A0A" : "#1A0000";
            surgeLabel.setStyle("-fx-text-fill:" + sc + ";-fx-font-family:monospace;-fx-font-size:10px;-fx-font-weight:bold;-fx-background-color:" + sb + ";-fx-background-radius:4;-fx-padding:4 14 4 14;-fx-border-color:" + sc + ";-fx-border-radius:4;-fx-border-width:1;");
        }
    }

    private String greetingColor(TimeMode m) {
        return switch (m) {
            case DAWN      -> "#FFD700";
            case MORNING   -> "#FFB800";
            case AFTERNOON -> "#00C2FF";
            case EVENING   -> "#FF6B35";
            case NIGHT     -> "#A855F7";
        };
    }

    // ─── Animated Background ────────────────────────────────────
    private void startBg() {
        gridAnim = new Timeline(new KeyFrame(Duration.millis(30), e -> drawBg()));
        gridAnim.setCycleCount(Animation.INDEFINITE);
        gridAnim.play();
    }

    private void drawBg() {
        double W = bgCanvas.getWidth(), H = bgCanvas.getHeight();
        if (W <= 0 || H <= 0) return;
        GraphicsContext gc = bgCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, W, H);
        gc.setFill(Color.web("#05080F")); gc.fillRect(0, 0, W, H);

        TimeMode mode = getTimeMode();
        Color tint = switch (mode) {
            case DAWN      -> Color.web("#1A1200", 0.18);
            case MORNING   -> Color.web("#0A1400", 0.15);
            case AFTERNOON -> Color.web("#000A1A", 0.12);
            case EVENING   -> Color.web("#1A0800", 0.20);
            case NIGHT     -> Color.web("#08001A", 0.22);
        };
        gc.setFill(tint); gc.fillRect(0, 0, W, H);

        offset = (offset + 0.25) % 80;
        Color gridColor = switch (mode) {
            case DAWN, MORNING -> Color.web("#0A1830", 0.5);
            case AFTERNOON     -> Color.web("#001828", 0.4);
            case EVENING       -> Color.web("#1A0C00", 0.5);
            case NIGHT         -> Color.web("#0A0018", 0.5);
        };
        gc.setStroke(gridColor); gc.setLineWidth(0.5);
        for (double x = -80+offset; x < W+80; x += 80) gc.strokeLine(x, 0, x, H);
        for (double y = -80+offset; y < H+80; y += 80) gc.strokeLine(0, y, W, y);

        long t = System.currentTimeMillis();
        Color p1 = switch (mode) {
            case DAWN      -> Color.web("#FFD700", 0.4);
            case MORNING   -> Color.web("#00C2FF", 0.35);
            case AFTERNOON -> Color.web("#00C2FF", 0.35);
            case EVENING   -> Color.web("#FF6B35", 0.4);
            case NIGHT     -> Color.web("#A855F7", 0.35);
        };
        Color p2 = switch (mode) {
            case DAWN      -> Color.web("#FF6B35", 0.3);
            case MORNING   -> Color.web("#00FF88", 0.25);
            case AFTERNOON -> Color.web("#00FF88", 0.25);
            case EVENING   -> Color.web("#FF2244", 0.3);
            case NIGHT     -> Color.web("#00C2FF", 0.25);
        };
        gc.setFill(p1);
        for (int i = 0; i < 8; i++) {
            double px = ((t/10.0 + i*160) % (W+120)) - 60;
            double py = ((i%4)+1) * H / 5.0;
            gc.fillOval(px-3, py-3, 6, 6);
        }
        gc.setFill(p2);
        for (int i = 0; i < 5; i++) {
            double px = ((i%4)+1) * W / 5.0;
            double py = ((t/13.0 + i*200) % (H+120)) - 60;
            gc.fillOval(px-2, py-2, 4, 4);
        }
        Color glow = switch (mode) {
            case DAWN, MORNING -> Color.web("#FFB800", 0.06);
            case AFTERNOON     -> Color.web("#00C2FF", 0.04);
            case EVENING       -> Color.web("#FF4400", 0.08);
            case NIGHT         -> Color.web("#4400AA", 0.07);
        };
        gc.setFill(glow); gc.fillRect(0, H*0.72, W, H*0.28);
    }

    // ─── Clock + Live Refresh ───────────────────────────────────
    // ─── Clock + Live Refresh ───────────────────────────────────
    private long cachedAvailable = 30;
    private long cachedOccupied = 0;
    private long cachedTotal = 30;

    private void startClock() {
        // UI Clock update - runs instantly every 1 second
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (clockLabel != null) clockLabel.setText(LocalTime.now().format(TIME_FMT));
        }));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();

        // Background Database Polling - prevents JavaFX from freezing when Cloud DB is slow
        Thread poller = new Thread(() -> {
            while (true) {
                try {
                    // Fetch data from Cloud
                    long avail = parkingService.getAvailableCount();
                    long occ = parkingService.getOccupiedCount();
                    long total = parkingService.getAllSlots().size();

                    Platform.runLater(() -> {
                        cachedAvailable = avail;
                        cachedOccupied = occ;
                        cachedTotal = total == 0 ? 30 : total;
                        
                        if (slotsLabel != null) slotsLabel.setText(cachedAvailable + " slots available");
                        applyTimeMode();
                        updateRates();
                    });

                    Thread.sleep(3000); // Poll every 3 seconds
                } catch (Exception ex) {
                    try { Thread.sleep(5000); } catch (Exception ignored) {}
                }
            }
        });
        poller.setDaemon(true);
        poller.start();
        
        if (clockLabel != null) clockLabel.setText(LocalTime.now().format(TIME_FMT));
    }

    // ─── Navigation ─────────────────────────────────────────────
    @FXML public void handlePark() { stopAndLoad("kiosk_park.fxml"); }
    @FXML public void handleExit() { stopAndLoad("kiosk_exit.fxml"); }

    private void stopAndLoad(String fxml) {
        if (gridAnim != null) gridAnim.stop();
        com.snappark.Main.switchScene(fxml);
    }

    // ─── Admin Login ────────────────────────────────────────────
    @FXML
    public void handleLogoClick() {
        long now = System.currentTimeMillis();
        if (now - lastTap > 3000) tapCount = 0;
        lastTap = now; tapCount++;
        if (tapCount >= 3) { tapCount = 0; showAdminLogin(); }
    }

    private void showAdminLogin() {
        soundService.beep();
        Dialog<String[]> dlg = new Dialog<>();
        dlg.setTitle("Admin Access");
        dlg.setHeaderText("Enter Admin Credentials");
        ButtonType loginBtn = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(loginBtn, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(12); grid.setPadding(new Insets(20));
        TextField userF = new TextField("admin");
        PasswordField passF = new PasswordField();
        grid.add(new Label("Username:"), 0, 0); grid.add(userF, 1, 0);
        grid.add(new Label("Password:"), 0, 1); grid.add(passF, 1, 1);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(b -> b == loginBtn ? new String[]{userF.getText(), passF.getText()} : null);
        Optional<String[]> res = dlg.showAndWait();
        res.ifPresent(creds -> {
            User user = authService.login(creds[0], creds[1]);
            if (user != null) {
                soundService.chime();
                stopAndLoad("dashboard.fxml");
            } else {
                soundService.alert();
                new Alert(Alert.AlertType.ERROR, "Invalid credentials!", ButtonType.OK).showAndWait();
            }
        });
    }
}
