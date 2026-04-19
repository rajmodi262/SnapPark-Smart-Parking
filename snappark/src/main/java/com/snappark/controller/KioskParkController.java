package com.snappark.controller;

import com.snappark.dao.SessionDAO;
import com.snappark.dao.VehicleDAO;
import com.snappark.model.ParkingSession;
import com.snappark.model.ParkingSlot;
import com.snappark.model.Vehicle;
import com.snappark.model.enums.SlotStatus;
import com.snappark.model.enums.VehicleType;
import com.snappark.service.HeatmapService;
import com.snappark.service.ParkingService;
import com.snappark.service.PlateDecoderService;
import com.snappark.service.QRCodeService;
import com.snappark.service.SlotRecommendationService;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class KioskParkController implements Initializable {

    // ── FXML bindings ──────────────────────────────────────────────
    @FXML private StackPane rootPane;
    @FXML private VBox step1, step2, step3;
    @FXML private StackPane gridContainer;
    @FXML private Label step1Status, selectedSlotInfo;
    @FXML private Label rec1Label, rec2Label, rec3Label, recScoreLabel;
    @FXML private HBox step1NextBar;
    @FXML private ComboBox<String> floorBox;
    @FXML private Button btnAll, btnTwo, btnFour;
    @FXML private Label slotNumLabel, slotTypeLabel;
    @FXML private TextField nameField, plateField, phoneField;
    @FXML private Label step2Error, phoneError;
    @FXML private HBox  decoderBox;
    @FXML private Label stateDot, decoderState, decoderRTO, decoderValid, decoderError;
    @FXML private ImageView qrView;
    @FXML private Label tktId, tktName, tktPlate, tktSlot, tktType, tktTime, tktPhone, cdLabel;

    // 4 corner panel containers (from FXML)
    @FXML private VBox howItWorksBox;
    @FXML private VBox safetyTipsBox;
    @FXML private VBox ratesBox;
    @FXML private VBox legendBox;

    // ── Services ───────────────────────────────────────────────────
    private final ParkingService            parkingService = ParkingService.getInstance();
    private final SlotRecommendationService recService     = SlotRecommendationService.getInstance();
    private final HeatmapService            heatmapService = HeatmapService.getInstance();
    private final PlateDecoderService       plateDecoder   = PlateDecoderService.getInstance();
    private final VehicleDAO                vehicleDAO     = new VehicleDAO();
    private final SessionDAO                sessionDAO     = new SessionDAO();

    // ── State ──────────────────────────────────────────────────────
    private List<SlotRecommendationService.ScoredSlot> recommendations = new ArrayList<>();
    private Map<Integer, Integer> kioskHeatData = new HashMap<>();
    private int kioskMaxHeat = 1;

    private Canvas   canvas;
    private boolean  pulse  = false;
    private String   filter = "ALL";
    private int      floor  = 1;
    private Timeline pulseTL, cdTL;
    private ParkingSlot       selected;
    private List<ParkingSlot> slots = new ArrayList<>();

    private static final int    UID = 1;
    private static final double TW  = 190, TD = 210, TH = 26, GAP = 20;
    private static final double VS  = 1.35;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // ══════════════════════════════════════════════════════════════
    // INIT
    // ══════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        floorBox.getItems().addAll("Floor 1", "Floor 2");
        floorBox.getSelectionModel().selectFirst();
        floorBox.setOnAction(e -> {
            floor = floorBox.getSelectionModel().getSelectedIndex() + 1;
            refresh();
        });
        canvas = new Canvas(800, 600);
        canvas.setOnMouseClicked(e -> onClick(e.getX(), e.getY()));
        gridContainer.getChildren().add(canvas);
        gridContainer.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> {
            double w = newVal.getWidth(), h = newVal.getHeight();
            if (w > 10 && h > 10) { canvas.setWidth(w); canvas.setHeight(h); draw(); }
        });
        startPulse();
        PauseTransition wait = new PauseTransition(Duration.millis(500));
        wait.setOnFinished(e -> { populateAllPanels(); refresh(); });
        wait.play();
    }

    // ══════════════════════════════════════════════════════════════
    // 4 CORNER PANELS — populate FXML VBoxes
    // ══════════════════════════════════════════════════════════════

    private void populateAllPanels() {
        populateHowItWorks(howItWorksBox);
        populateSafetyTips(safetyTipsBox);
        populateRates(ratesBox);
        populateLegend(legendBox);
    }

    // ── TOP-LEFT: HOW IT WORKS ─────────────────────────────────────
    private void populateHowItWorks(VBox panel) {
        if (panel == null) return;
        String[] steps = {
            "1  SELECT A SLOT",
            "2  ENTER DETAILS",
            "3  GET QR TICKET",
            "4  PAY AND EXIT"
        };
        String activeStyle   = "-fx-text-fill:#00C2FF;-fx-font-family:monospace;-fx-font-size:11px;" +
                               "-fx-font-weight:bold;-fx-background-color:#0A1A2A;" +
                               "-fx-background-radius:4;-fx-padding:4 7 4 7;";
        String inactiveStyle = "-fx-text-fill:#334155;-fx-font-family:monospace;" +
                               "-fx-font-size:11px;-fx-padding:4 7 4 7;";
        Label[] lbls = new Label[4];
        for (int i = 0; i < 4; i++) {
            lbls[i] = new Label(steps[i]);
            lbls[i].setStyle(i == 0 ? activeStyle : inactiveStyle);
            lbls[i].setMaxWidth(Double.MAX_VALUE);
            panel.getChildren().add(lbls[i]);
        }
        final int[] active = {0};
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            lbls[active[0]].setStyle(inactiveStyle);
            active[0] = (active[0] + 1) % 4;
            lbls[active[0]].setStyle(activeStyle);
        }));
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
    }

    // ── BOTTOM-LEFT: SAFETY TIPS ───────────────────────────────────
    private void populateSafetyTips(VBox panel) {
        if (panel == null) return;
        String[] tips = {
            "Lock your vehicle",
            "CCTV surveillance active",
            "Note your slot number",
            "Don't leave valuables",
            "Keep your QR ticket safe"
        };
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill:#00FF88;-fx-font-size:13px;");
        Label tipLbl = new Label(tips[0]);
        tipLbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-family:monospace;" +
                        "-fx-font-size:11px;-fx-wrap-text:true;");
        tipLbl.setWrapText(true);
        tipLbl.setMaxWidth(150);
        HBox row = new HBox(7, dot, tipLbl);
        row.setAlignment(Pos.CENTER_LEFT);
        panel.getChildren().add(row);
        Label counter = new Label("1 / 5");
        counter.setStyle("-fx-text-fill:#1E3A5F;-fx-font-family:monospace;-fx-font-size:10px;");
        panel.getChildren().add(counter);
        final int[] idx = {0};
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(4), e -> {
            FadeTransition fo = new FadeTransition(Duration.millis(250), tipLbl);
            fo.setFromValue(1.0); fo.setToValue(0.0);
            fo.setOnFinished(ev -> {
                idx[0] = (idx[0] + 1) % tips.length;
                tipLbl.setText(tips[idx[0]]);
                counter.setText((idx[0] + 1) + " / 5");
                FadeTransition fi = new FadeTransition(Duration.millis(250), tipLbl);
                fi.setFromValue(0.0); fi.setToValue(1.0);
                fi.play();
            });
            fo.play();
        }));
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
    }

    // ── TOP-RIGHT: RATES + RULES ───────────────────────────────────
    private void populateRates(VBox panel) {
        if (panel == null) return;
        addRateRow(panel, "#00FF88", "2-WHEELER", "Rs.20/hr");
        addRateRow(panel, "#00C2FF", "CAR",       "Rs.50/hr");
        addRateRow(panel, "#A855F7", "SUV",       "Rs.80/hr");
        Label div = new Label("────────────────");
        div.setStyle("-fx-text-fill:#1E3A5F;-fx-font-family:monospace;-fx-font-size:9px;");
        panel.getChildren().add(div);
        String[] rules = { "Min. 1 hour charge", "Keep your QR safe", "Exit by midnight" };
        for (String rule : rules) {
            Label r = new Label("·  " + rule);
            r.setStyle("-fx-text-fill:#475569;-fx-font-family:monospace;-fx-font-size:10px;");
            panel.getChildren().add(r);
        }
    }

    private void addRateRow(VBox panel, String color, String type, String rate) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color:#080C14;-fx-background-radius:4;" +
                     "-fx-border-color:" + color + ";-fx-border-width:0 0 0 3;" +
                     "-fx-padding:4 6 4 6;-fx-spacing:6;");
        row.setMaxWidth(Double.MAX_VALUE);
        Label tl = new Label(type);
        tl.setStyle("-fx-text-fill:#64748b;-fx-font-family:monospace;-fx-font-size:10px;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label rl = new Label(rate);
        rl.setStyle("-fx-text-fill:" + color + ";-fx-font-family:monospace;" +
                    "-fx-font-size:11px;-fx-font-weight:bold;");
        row.getChildren().addAll(tl, sp, rl);
        panel.getChildren().add(row);
    }

    // ── BOTTOM-RIGHT: GRID LEGEND ──────────────────────────────────
    private void populateLegend(VBox panel) {
        if (panel == null) return;
        String[][] items = {
            {"#00FF88", "Available",  "Tap to book"},
            {"#FF2244", "Occupied",   "Already taken"},
            {"#FFB800", "Locking",    "Being booked"},
            {"#AAFF00", "Selected",   "Your choice"}
        };
        Label[] dots = new Label[4];
        for (int i = 0; i < items.length; i++) {
            Label dot = new Label("◆");
            dot.setStyle("-fx-text-fill:" + items[i][0] + ";-fx-font-size:14px;");
            dots[i] = dot;
            Label name = new Label(items[i][1]);
            name.setStyle("-fx-text-fill:#94a3b8;-fx-font-family:monospace;" +
                          "-fx-font-size:11px;-fx-font-weight:bold;");
            Label desc = new Label(items[i][2]);
            desc.setStyle("-fx-text-fill:#334155;-fx-font-family:monospace;-fx-font-size:9px;");
            VBox txt = new VBox(0, name, desc);
            HBox row = new HBox(8, dot, txt);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding:2 0 2 0;");
            panel.getChildren().add(row);
        }
        // Pulse green + amber dots
        Timeline pulse = new Timeline(
            new KeyFrame(Duration.ZERO,         e -> { dots[0].setOpacity(1.0); dots[2].setOpacity(1.0); }),
            new KeyFrame(Duration.millis(700),  e -> { dots[0].setOpacity(0.25); dots[2].setOpacity(0.25); }),
            new KeyFrame(Duration.millis(1400), e -> { dots[0].setOpacity(1.0); dots[2].setOpacity(1.0); })
        );
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();
    }

    // ══════════════════════════════════════════════════════════════
    // FILTER BUTTONS
    // ══════════════════════════════════════════════════════════════
    @FXML public void filterAll()  { filter = "ALL";  setFBtn(btnAll);  refresh(); }
    @FXML public void filterTwo()  { filter = "TWO";  setFBtn(btnTwo);  refresh(); }
    @FXML public void filterFour() { filter = "FOUR"; setFBtn(btnFour); refresh(); }

    private void setFBtn(Button a) {
        String off = "-fx-background-color:#0D1525;-fx-background-radius:4;-fx-border-radius:4;" +
                     "-fx-font-family:monospace;-fx-font-size:11px;-fx-cursor:hand;" +
                     "-fx-padding:4 10 4 10;-fx-border-width:1;";
        if (btnAll  != null) btnAll.setStyle(off  + "-fx-text-fill:#00C2FF;-fx-border-color:#00C2FF;");
        if (btnTwo  != null) btnTwo.setStyle(off  + "-fx-text-fill:#00FF88;-fx-border-color:#00FF88;");
        if (btnFour != null) btnFour.setStyle(off + "-fx-text-fill:#00C2FF;-fx-border-color:#00C2FF;");
        String on = "-fx-font-family:monospace;-fx-font-size:11px;-fx-font-weight:bold;" +
                    "-fx-cursor:hand;-fx-background-radius:4;-fx-padding:4 10 4 10;-fx-text-fill:#0A0F1A;";
        if (a == btnAll  && btnAll  != null) btnAll.setStyle(on  + "-fx-background-color:#00C2FF;");
        if (a == btnTwo  && btnTwo  != null) btnTwo.setStyle(on  + "-fx-background-color:#00FF88;");
        if (a == btnFour && btnFour != null) btnFour.setStyle(on + "-fx-background-color:#00C2FF;");
    }

    // ══════════════════════════════════════════════════════════════
    // REFRESH
    // ══════════════════════════════════════════════════════════════
    private void refresh() {
        selected = null;
        if (step1NextBar != null) { step1NextBar.setVisible(false); step1NextBar.setManaged(false); }
        if (step1Status  != null) step1Status.setText("Tap a GREEN slot to select it");
        List<ParkingSlot> all = parkingService.getSlotsByFloor(floor);
        slots = switch (filter) {
            case "TWO"  -> all.stream().filter(s -> s.getType() == VehicleType.BIKE).collect(Collectors.toList());
            case "FOUR" -> all.stream().filter(s -> s.getType() != VehicleType.BIKE).collect(Collectors.toList());
            default     -> new ArrayList<>(all);
        };
        kioskHeatData = heatmapService.getAllTimeBookings();
        kioskMaxHeat  = heatmapService.getMaxCount(kioskHeatData);
        if (kioskMaxHeat == 0) kioskMaxHeat = 1;
        VehicleType vt = filter.equals("TWO") ? VehicleType.BIKE : VehicleType.CAR;
        recommendations = recService.recommend(all, vt, floor);
        updateRecBar();
        draw();
    }

    private void updateRecBar() {
        if (rec1Label == null) return;
        rec1Label.setText(""); rec2Label.setText(""); rec3Label.setText(""); recScoreLabel.setText("");
        if (recommendations.isEmpty()) return;
        SlotRecommendationService.ScoredSlot r1 = recommendations.get(0);
        rec1Label.setText(r1.slot.getSlotNumber() + "  [" + r1.reason + "]");
        recScoreLabel.setText("Score: " + r1.totalScore + "/100");
        if (recommendations.size() > 1) rec2Label.setText("  |  " + recommendations.get(1).slot.getSlotNumber());
        if (recommendations.size() > 2) rec3Label.setText("  |  " + recommendations.get(2).slot.getSlotNumber());
    }

    // ══════════════════════════════════════════════════════════════
    // ISO PROJECTION
    // ══════════════════════════════════════════════════════════════
    private double[] iso(double wx, double wz, double wy, double ox, double oy) {
        return new double[]{ ox + (wx - wz) * 0.52, oy + (wx + wz) * 0.27 - wy * 0.72 };
    }

    // ══════════════════════════════════════════════════════════════
    // DRAW GRID
    // ══════════════════════════════════════════════════════════════
    private void draw() {
        if (canvas == null) return;
        double W = canvas.getWidth(), H = canvas.getHeight();
        if (W <= 0 || H <= 0) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, W, H);
        gc.setFill(Color.web("#05080F")); gc.fillRect(0, 0, W, H);
        if (slots.isEmpty()) {
            gc.setFill(Color.web("#00C2FF", 0.5));
            gc.setFont(Font.font("monospace", 18));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("NO SLOTS", W / 2, H / 2);
            gc.setTextAlign(TextAlignment.LEFT);
            return;
        }
        int total = slots.size(), COLS = Math.min(5, total);
        int ROWS = (int) Math.ceil((double) total / COLS);
        double sX = TW + GAP, sZ = TD + GAP;
        double cX = (COLS - 1) * sX / 2.0, cZ = (ROWS - 1) * sZ / 2.0;
        double[] tp = iso(-cX, -cZ, TH, 0, 0), bp = iso(cX, cZ, 0, 0, 0);
        double gH = bp[1] - tp[1];
        double ox = W / 2, oy = H * 0.48 - gH * 0.18;

        gc.setStroke(Color.web("#0D2040", 0.5)); gc.setLineWidth(0.7);
        for (int i = 0; i <= COLS; i++) {
            double wx = i * sX - cX - sX / 2;
            double[] p1 = iso(wx, -sZ / 2 - cZ, 0, ox, oy);
            double[] p2 = iso(wx, ROWS * sZ - sZ / 2 - cZ, 0, ox, oy);
            gc.strokeLine(p1[0], p1[1], p2[0], p2[1]);
        }
        for (int i = 0; i <= ROWS; i++) {
            double wz = i * sZ - cZ - sZ / 2;
            double[] p1 = iso(-sX / 2 - cX, wz, 0, ox, oy);
            double[] p2 = iso(COLS * sX - sX / 2 - cX, wz, 0, ox, oy);
            gc.strokeLine(p1[0], p1[1], p2[0], p2[1]);
        }

        for (int row = ROWS - 1; row >= 0; row--)
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col;
                if (idx < slots.size())
                    drawSlot(gc, slots.get(idx), col * sX - cX, row * sZ - cZ, ox, oy);
            }

        // AI badges
        if (selected == null && !recommendations.isEmpty()) {
            for (int ri = 0; ri < recommendations.size(); ri++) {
                SlotRecommendationService.ScoredSlot ss = recommendations.get(ri);
                int idx = slots.indexOf(ss.slot); if (idx < 0) continue;
                int col = idx % COLS, row = idx / COLS;
                double wx = col * sX - cX, wz = row * sZ - cZ;
                double[] lp = iso(wx, wz, TH + 36, ox, oy);
                if (ri == 0) {
                    gc.setFill(Color.web("#FFD700", 0.95));
                    gc.setFont(Font.font("monospace", FontWeight.BOLD, 12));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText("BEST", lp[0], lp[1]);
                    gc.setStroke(Color.web("#FFD700", 0.7)); gc.setLineWidth(2.5);
                    double[] tfl = iso(wx - TW / 2, wz + TD / 2, TH, ox, oy);
                    double[] tfr = iso(wx + TW / 2, wz + TD / 2, TH, ox, oy);
                    double[] tbl = iso(wx - TW / 2, wz - TD / 2, TH, ox, oy);
                    double[] tbr = iso(wx + TW / 2, wz - TD / 2, TH, ox, oy);
                    gc.strokePolygon(new double[]{tfl[0], tfr[0], tbr[0], tbl[0]},
                                     new double[]{tfl[1], tfr[1], tbr[1], tbl[1]}, 4);
                } else {
                    gc.setFill(Color.web("#00C2FF", 0.7));
                    gc.setFont(Font.font("monospace", 11));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText("#" + (ri + 1), lp[0], lp[1]);
                }
                gc.setTextAlign(TextAlignment.LEFT);
            }
        }

        // HUD brackets
        gc.setStroke(Color.web("#00C2FF", 0.3)); gc.setLineWidth(1.5);
        double m = 20, s = 30;
        gc.strokeLine(m, m, m + s, m); gc.strokeLine(m, m, m, m + s);
        gc.strokeLine(W-m, m, W-m-s, m); gc.strokeLine(W-m, m, W-m, m+s);
        gc.strokeLine(m, H-m, m+s, H-m); gc.strokeLine(m, H-m, m, H-m-s);
        gc.strokeLine(W-m, H-m, W-m-s, H-m); gc.strokeLine(W-m, H-m, W-m, H-m-s);
    }

    // ══════════════════════════════════════════════════════════════
    // DRAW SLOT
    // ══════════════════════════════════════════════════════════════
    private void drawSlot(GraphicsContext gc, ParkingSlot slot,
                          double wx, double wz, double ox, double oy) {
        boolean sel = selected != null && selected.getId() == slot.getId();
        double hw = TW / 2, hd = TD / 2;
        Color topC, leftC, rightC, neonC;
        if (sel) {
            topC = Color.web("#1A4400"); leftC = Color.web("#102800");
            rightC = Color.web("#0A1800"); neonC = Color.web("#AAFF00");
        } else switch (slot.getStatus()) {
            case OCCUPIED -> { topC = Color.web("#3D0010"); leftC = Color.web("#220008"); rightC = Color.web("#150005"); neonC = Color.web("#FF2244"); }
            case LOCKING  -> { topC = pulse ? Color.web("#4A3200") : Color.web("#2A1C00"); leftC = Color.web("#1A1000"); rightC = Color.web("#0F0900"); neonC = Color.web("#FFB800"); }
            default       -> { topC = Color.web("#002B18"); leftC = Color.web("#001610"); rightC = Color.web("#000D09"); neonC = Color.web("#00FF88"); }
        }

        double[] BFL = iso(wx-hw, wz+hd, 0,  ox, oy), BFR = iso(wx+hw, wz+hd, 0,  ox, oy);
        double[] BBR = iso(wx+hw, wz-hd, 0,  ox, oy);
        double[] TFL = iso(wx-hw, wz+hd, TH, ox, oy), TFR = iso(wx+hw, wz+hd, TH, ox, oy);
        double[] TBL = iso(wx-hw, wz-hd, TH, ox, oy), TBR = iso(wx+hw, wz-hd, TH, ox, oy);

        gc.setFill(leftC);
        gc.fillPolygon(new double[]{TFL[0],BFL[0],BFR[0],TFR[0]}, new double[]{TFL[1],BFL[1],BFR[1],TFR[1]}, 4);
        gc.setFill(rightC);
        gc.fillPolygon(new double[]{TFR[0],BFR[0],BBR[0],TBR[0]}, new double[]{TFR[1],BFR[1],BBR[1],TBR[1]}, 4);
        gc.setFill(topC);
        gc.fillPolygon(new double[]{TFL[0],TFR[0],TBR[0],TBL[0]}, new double[]{TFL[1],TFR[1],TBR[1],TBL[1]}, 4);

        // Heat overlay
        int hc = kioskHeatData.getOrDefault(slot.getId(), 0);
        if (hc > 0) {
            Color hColor = heatmapService.getHeatColor(hc, kioskMaxHeat);
            gc.setFill(hColor); gc.setGlobalAlpha(1.0);
            gc.fillPolygon(new double[]{TFL[0],TFR[0],TBR[0],TBL[0]}, new double[]{TFL[1],TFR[1],TBR[1],TBL[1]}, 4);
            Color glowC = heatmapService.getGlowColor(hc, kioskMaxHeat);
            double[] hlp = iso(wx, wz, TH + 32, ox, oy);
            String hLbl = heatmapService.getHeatLabel(hc);
            gc.setFill(glowC); gc.setGlobalAlpha(0.95);
            gc.fillRoundRect(hlp[0] - 18, hlp[1] - 11, 36, 16, 5, 5);
            gc.setFill(Color.web("#0A0F1A")); gc.setGlobalAlpha(1.0);
            gc.setFont(Font.font("monospace", FontWeight.BOLD, 11));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(hLbl, hlp[0], hlp[1] + 1);
            gc.setTextAlign(TextAlignment.LEFT);
        }

        // Neon border
        double na = (slot.getStatus() == SlotStatus.LOCKING && !pulse) ? 0.4 : 1.0;
        gc.setStroke(neonC); gc.setLineWidth(sel ? 3 : 2.0); gc.setGlobalAlpha(na);
        gc.strokePolygon(new double[]{TFL[0],TFR[0],TBR[0],TBL[0]}, new double[]{TFL[1],TFR[1],TBR[1],TBL[1]}, 4);
        gc.setGlobalAlpha(1.0);

        // Vehicle model
        if (slot.getStatus() != SlotStatus.AVAILABLE)
            drawVehicle(gc, slot.getType(), wx, wz, ox, oy);

        // Slot label pill
        double[] lp = iso(wx, wz, TH + 4, ox, oy);
        String sNum = slot.getSlotNumber();
        String sTyp = slot.getType() == VehicleType.BIKE ? "2W" : slot.getType() == VehicleType.CAR ? "CAR" : "SUV";
        gc.setFill(Color.web("#000000", 0.62));
        gc.fillRoundRect(lp[0] - 28, lp[1] - 17, 56, 24, 7, 7);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("monospace", FontWeight.BOLD, 13));
        gc.setGlobalAlpha(1.0);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(sNum, lp[0], lp[1] - 2);
        gc.setFill(neonC.deriveColor(0, 1, 1, 0.30));
        gc.fillRoundRect(lp[0] - 15, lp[1] + 3, 30, 13, 4, 4);
        gc.setFill(neonC);
        gc.setFont(Font.font("monospace", FontWeight.BOLD, 10));
        gc.fillText(sTyp, lp[0], lp[1] + 13);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setGlobalAlpha(1.0);
    }

    // ══════════════════════════════════════════════════════════════
    // ISO BOX
    // ══════════════════════════════════════════════════════════════
    private void isoBox(GraphicsContext gc, double wx, double wz, double base,
                        double bw, double bd, double bh,
                        Color top, Color left, Color right, double ox, double oy) {
        double hw = bw / 2, hd = bd / 2;
        double[] BFL = iso(wx-hw, wz+hd, base,    ox, oy), BFR = iso(wx+hw, wz+hd, base,    ox, oy);
        double[] BBR = iso(wx+hw, wz-hd, base,    ox, oy);
        double[] TFL = iso(wx-hw, wz+hd, base+bh, ox, oy), TFR = iso(wx+hw, wz+hd, base+bh, ox, oy);
        double[] TBL = iso(wx-hw, wz-hd, base+bh, ox, oy), TBR = iso(wx+hw, wz-hd, base+bh, ox, oy);
        gc.setFill(left);  gc.fillPolygon(new double[]{TFL[0],BFL[0],BFR[0],TFR[0]}, new double[]{TFL[1],BFL[1],BFR[1],TFR[1]}, 4);
        gc.setFill(right); gc.fillPolygon(new double[]{TFR[0],BFR[0],BBR[0],TBR[0]}, new double[]{TFR[1],BFR[1],BBR[1],TBR[1]}, 4);
        gc.setFill(top);   gc.fillPolygon(new double[]{TFL[0],TFR[0],TBR[0],TBL[0]}, new double[]{TFL[1],TFR[1],TBR[1],TBL[1]}, 4);
    }

    // ══════════════════════════════════════════════════════════════
    // VEHICLE MODELS (VS scaled)
    // ══════════════════════════════════════════════════════════════
    private void drawVehicle(GraphicsContext gc, VehicleType type,
                             double wx, double wz, double ox, double oy) {
        double base = TH;
        switch (type) {
            case BIKE -> {
                Color body = Color.web("#CC3300"), bodyD = Color.web("#881100");
                Color metal = Color.web("#888888"), metalD = Color.web("#555555");
                Color wheel = Color.web("#111111"), wheelC = Color.web("#666666");
                Color seat = Color.web("#1A1A1A");
                isoBox(gc, wx, wz+30*VS, base,       8*VS,18*VS,14*VS, wheel, wheel.brighter(), wheel.brighter(), ox, oy);
                isoBox(gc, wx, wz+30*VS, base+4*VS,  4*VS,10*VS, 6*VS, wheelC, wheelC.darker(), wheelC.darker(), ox, oy);
                isoBox(gc, wx, wz-30*VS, base,       8*VS,18*VS,14*VS, wheel, wheel.brighter(), wheel.brighter(), ox, oy);
                isoBox(gc, wx, wz-30*VS, base+4*VS,  4*VS,10*VS, 6*VS, wheelC, wheelC.darker(), wheelC.darker(), ox, oy);
                isoBox(gc, wx, wz,       base+6*VS,  6*VS,44*VS, 6*VS, metal, metalD, metalD, ox, oy);
                isoBox(gc, wx, wz+2*VS,  base+2*VS, 14*VS,22*VS,12*VS, body, bodyD, bodyD.darker(), ox, oy);
                isoBox(gc, wx, wz-4*VS,  base+14*VS,12*VS,18*VS, 8*VS, body.brighter(), body, bodyD, ox, oy);
                isoBox(gc, wx, wz-16*VS, base+20*VS,10*VS,24*VS, 5*VS, seat, seat.brighter(), seat.brighter(), ox, oy);
                isoBox(gc, wx, wz+22*VS, base+28*VS,28*VS, 4*VS, 4*VS, metal, metalD, metalD, ox, oy);
                isoBox(gc, wx, wz+34*VS, base+14*VS, 8*VS, 4*VS, 6*VS,
                    Color.web("#FFFF99",0.95), Color.web("#DDDD77",0.8), Color.web("#BBBB55",0.6), ox, oy);
            }
            case CAR -> {
                Color body = Color.web("#1A3A6A"), bodyL = Color.web("#0F2244"), bodyR = Color.web("#0A1830");
                Color glass = Color.web("#00AADD", 0.75), wheel = Color.web("#111111"), wRim = Color.web("#888899");
                isoBox(gc, wx, wz,      base,       52*VS,88*VS,12*VS, body, bodyL, bodyR, ox, oy);
                isoBox(gc, wx, wz-4*VS, base+12*VS, 40*VS,46*VS,10*VS, body.brighter(), bodyL.brighter(), bodyR.brighter(), ox, oy);
                double[] W1 = iso(wx-16*VS, wz+14*VS, base+12*VS, ox, oy), W2 = iso(wx+16*VS, wz+14*VS, base+12*VS, ox, oy);
                double[] W3 = iso(wx+18*VS, wz+20*VS, base+12*VS, ox, oy), W4 = iso(wx-18*VS, wz+20*VS, base+12*VS, ox, oy);
                gc.setFill(glass);
                gc.fillPolygon(new double[]{W1[0],W2[0],W3[0],W4[0]}, new double[]{W1[1],W2[1],W3[1],W4[1]}, 4);
                double[] R1 = iso(wx-14*VS, wz-18*VS, base+12*VS, ox, oy), R2 = iso(wx+14*VS, wz-18*VS, base+12*VS, ox, oy);
                double[] R3 = iso(wx+12*VS, wz-22*VS, base+12*VS, ox, oy), R4 = iso(wx-12*VS, wz-22*VS, base+12*VS, ox, oy);
                gc.setFill(glass.deriveColor(0,1,0.7,0.8));
                gc.fillPolygon(new double[]{R1[0],R2[0],R3[0],R4[0]}, new double[]{R1[1],R2[1],R3[1],R4[1]}, 4);
                for (double[] wp : new double[][]{{-22*VS,-34*VS},{22*VS,-34*VS},{-22*VS,34*VS},{22*VS,34*VS}}) {
                    isoBox(gc, wx+wp[0], wz+wp[1], base-2,  10*VS,16*VS, 9*VS, wheel, wheel, wheel, ox, oy);
                    isoBox(gc, wx+wp[0], wz+wp[1], base+2,   6*VS,10*VS, 4*VS, wRim, wRim.darker(), wRim.darker(), ox, oy);
                }
                isoBox(gc, wx-18*VS, wz+44*VS, base+4, 8*VS,4*VS,5*VS, Color.web("#FFFFAA",0.95), Color.web("#DDDD88",0.8), Color.web("#BBBB66",0.6), ox, oy);
                isoBox(gc, wx+18*VS, wz+44*VS, base+4, 8*VS,4*VS,5*VS, Color.web("#FFFFAA",0.95), Color.web("#DDDD88",0.8), Color.web("#BBBB66",0.6), ox, oy);
                isoBox(gc, wx-18*VS, wz-44*VS, base+4, 8*VS,4*VS,5*VS, Color.web("#FF2222",0.9), Color.web("#CC1111",0.7), Color.web("#AA0000",0.5), ox, oy);
                isoBox(gc, wx+18*VS, wz-44*VS, base+4, 8*VS,4*VS,5*VS, Color.web("#FF2222",0.9), Color.web("#CC1111",0.7), Color.web("#AA0000",0.5), ox, oy);
                isoBox(gc, wx, wz+45*VS, base+2, 22*VS,3*VS,4*VS, Color.web("#FFFFFF",0.9), Color.web("#EEEEEE",0.7), Color.web("#CCCCCC",0.5), ox, oy);
            }
            case SUV -> {
                Color body = Color.web("#2A1A0A"), bodyL = Color.web("#1A0F06"), bodyR = Color.web("#110A04");
                Color glass = Color.web("#004466", 0.8), wheel = Color.web("#111111"), wRim = Color.web("#778899");
                isoBox(gc, wx, wz,      base,       62*VS,96*VS,18*VS, body, bodyL, bodyR, ox, oy);
                isoBox(gc, wx, wz-2*VS, base+18*VS, 58*VS,60*VS, 4*VS, Color.web("#333333"), Color.web("#222222"), Color.web("#1A1A1A"), ox, oy);
                double[] W1 = iso(wx-22*VS, wz+24*VS, base+14*VS, ox, oy), W2 = iso(wx+22*VS, wz+24*VS, base+14*VS, ox, oy);
                double[] W3 = iso(wx+24*VS, wz+30*VS, base+14*VS, ox, oy), W4 = iso(wx-24*VS, wz+30*VS, base+14*VS, ox, oy);
                gc.setFill(glass);
                gc.fillPolygon(new double[]{W1[0],W2[0],W3[0],W4[0]}, new double[]{W1[1],W2[1],W3[1],W4[1]}, 4);
                double[] S1 = iso(wx-30*VS, wz+10*VS, base+14*VS, ox, oy), S2 = iso(wx-30*VS, wz+22*VS, base+14*VS, ox, oy);
                double[] S3 = iso(wx-30*VS, wz+20*VS, base+18*VS, ox, oy), S4 = iso(wx-30*VS, wz+12*VS, base+18*VS, ox, oy);
                gc.setFill(glass);
                gc.fillPolygon(new double[]{S1[0],S2[0],S3[0],S4[0]}, new double[]{S1[1],S2[1],S3[1],S4[1]}, 4);
                for (double[] wp : new double[][]{{-26*VS,-38*VS},{26*VS,-38*VS},{-26*VS,38*VS},{26*VS,38*VS}}) {
                    isoBox(gc, wx+wp[0], wz+wp[1], base-3,  14*VS,20*VS,12*VS, wheel, wheel, wheel, ox, oy);
                    isoBox(gc, wx+wp[0], wz+wp[1], base+2,   8*VS,12*VS, 5*VS, wRim, wRim.darker(), wRim.darker(), ox, oy);
                }
                isoBox(gc, wx-22*VS, wz+48*VS, base+8, 12*VS,5*VS,6*VS, Color.web("#FFFFFF",0.9), Color.web("#DDDDDD",0.7), Color.web("#BBBBBB",0.5), ox, oy);
                isoBox(gc, wx+22*VS, wz+48*VS, base+8, 12*VS,5*VS,6*VS, Color.web("#FFFFFF",0.9), Color.web("#DDDDDD",0.7), Color.web("#BBBBBB",0.5), ox, oy);
                isoBox(gc, wx-22*VS, wz-48*VS, base+8, 12*VS,5*VS,6*VS, Color.web("#FF3300",0.9), Color.web("#CC2200",0.7), Color.web("#AA1100",0.5), ox, oy);
                isoBox(gc, wx+22*VS, wz-48*VS, base+8, 12*VS,5*VS,6*VS, Color.web("#FF3300",0.9), Color.web("#CC2200",0.7), Color.web("#AA1100",0.5), ox, oy);
                isoBox(gc, wx, wz+50*VS, base+2, 55*VS,4*VS,6*VS, Color.web("#444444"), Color.web("#333333"), Color.web("#222222"), ox, oy);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CLICK HANDLER
    // ══════════════════════════════════════════════════════════════
    private void onClick(double mx, double my) {
        if (slots.isEmpty()) return;
        int total = slots.size(), COLS = Math.min(5, total);
        int ROWS = (int) Math.ceil((double) total / COLS);
        double sX = TW + GAP, sZ = TD + GAP;
        double cX = (COLS-1)*sX/2.0, cZ = (ROWS-1)*sZ/2.0;
        double[] tp = iso(-cX,-cZ,TH,0,0), bp = iso(cX,cZ,0,0,0);
        double gH = bp[1]-tp[1];
        double ox = canvas.getWidth()/2, oy = canvas.getHeight()*0.48 - gH*0.18;
        ParkingSlot best = null; double bestD = Double.MAX_VALUE;
        for (int i = 0; i < slots.size(); i++) {
            double[] sc = iso((i%COLS)*sX-cX, (i/COLS)*sZ-cZ, TH, ox, oy);
            double d = Math.hypot(mx-sc[0], my-sc[1]);
            if (d < bestD) { bestD = d; best = slots.get(i); }
        }
        if (best != null && bestD < 110) {
            if (best.getStatus() == SlotStatus.AVAILABLE) {
                selected = best;
                if (step1Status    != null) step1Status.setText("Selected: " + best.getSlotNumber() + " [" + best.getType() + "] — tap NEXT");
                if (selectedSlotInfo != null) selectedSlotInfo.setText("Slot " + best.getSlotNumber() + " selected");
                if (step1NextBar   != null) { step1NextBar.setVisible(true); step1NextBar.setManaged(true); }
                draw();
            } else if (step1Status != null)
                step1Status.setText("Slot is " + best.getStatus().name().toLowerCase() + " — choose a GREEN slot");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // STEP NAVIGATION
    // ══════════════════════════════════════════════════════════════
    @FXML public void handleStep1Next() {
        if (selected == null) return;
        if (slotNumLabel  != null) slotNumLabel.setText(selected.getSlotNumber());
        if (slotTypeLabel != null) slotTypeLabel.setText(
            selected.getType() == VehicleType.BIKE ? "2-Wheeler (Bike)" :
            selected.getType() == VehicleType.CAR  ? "4-Wheeler (Car)"  : "4-Wheeler (SUV)");
        if (nameField  != null) nameField.clear();
        if (plateField != null) plateField.clear();
        if (phoneField != null) phoneField.clear();
        if (step2Error != null) step2Error.setText("");
        resetDecoder();
        if (plateField != null) plateField.textProperty().addListener((obs,ov,nv) -> updateDecoder(nv));
        if (phoneField != null) phoneField.textProperty().addListener((obs,ov,nv) -> validatePhone(nv));
        show(2);
    }

    @FXML public void handleStep2Back() { resetDecoder(); show(1); }

    // ══════════════════════════════════════════════════════════════
    // PLATE DECODER
    // ══════════════════════════════════════════════════════════════
    private void resetDecoder() {
        if (decoderBox   != null) { decoderBox.setVisible(false); decoderBox.setManaged(false); }
        if (decoderError != null) { decoderError.setVisible(false); decoderError.setManaged(false); }
        if (phoneError   != null) { phoneError.setVisible(false); phoneError.setManaged(false); }
        if (plateField   != null)
            plateField.setStyle("-fx-background-color:#0D1525;-fx-text-fill:white;" +
                "-fx-border-color:#1E3A5F;-fx-border-radius:8;-fx-background-radius:8;" +
                "-fx-border-width:1;-fx-padding:11;-fx-font-size:15px;-fx-font-family:monospace;");
    }

    private void updateDecoder(String input) {
        if (input == null || input.isBlank()) { resetDecoder(); return; }
        PlateDecoderService.PlateResult r = plateDecoder.decode(input);
        if (r.valid) {
            Platform.runLater(() -> {
                if (plateField != null && !plateField.getText().equals(r.formatted) && !r.formatted.isEmpty()) {
                    plateField.setText(r.formatted);
                    plateField.positionCaret(r.formatted.length());
                }
            });
            if (decoderBox   != null) { decoderBox.setVisible(true); decoderBox.setManaged(true); }
            if (decoderError != null) { decoderError.setVisible(false); decoderError.setManaged(false); }
            if (stateDot     != null) stateDot.setStyle("-fx-background-color:" + r.stateColor + ";-fx-min-width:14;-fx-min-height:14;-fx-background-radius:7;");
            if (decoderState != null) decoderState.setText(r.state);
            if (decoderRTO   != null) decoderRTO.setText(r.rto);
            if (decoderValid != null) { decoderValid.setText("VALID"); decoderValid.setStyle("-fx-text-fill:#00FF88;-fx-font-family:monospace;-fx-font-size:12px;-fx-font-weight:bold;"); }
            if (plateField   != null)
                plateField.setStyle("-fx-background-color:#0D1525;-fx-text-fill:white;" +
                    "-fx-border-color:#00FF88;-fx-border-radius:8;-fx-background-radius:8;" +
                    "-fx-border-width:2;-fx-padding:11;-fx-font-size:15px;-fx-font-family:monospace;");
        } else {
            if (decoderBox != null) { decoderBox.setVisible(false); decoderBox.setManaged(false); }
            if (input.length() >= 2 && decoderError != null) {
                decoderError.setText(r.error);
                decoderError.setVisible(true); decoderError.setManaged(true);
            }
            if (plateField != null)
                plateField.setStyle("-fx-background-color:#0D1525;-fx-text-fill:white;" +
                    "-fx-border-color:#1E3A5F;-fx-border-radius:8;-fx-background-radius:8;" +
                    "-fx-border-width:1;-fx-padding:11;-fx-font-size:15px;-fx-font-family:monospace;");
        }
    }

    private void validatePhone(String input) {
        if (input == null || input.isBlank()) {
            if (phoneError != null) { phoneError.setVisible(false); phoneError.setManaged(false); } return;
        }
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            if (phoneField != null)
                phoneField.setStyle("-fx-background-color:#0D1525;-fx-text-fill:white;" +
                    "-fx-border-color:#00FF88;-fx-border-radius:0 8 8 0;-fx-background-radius:0 8 8 0;" +
                    "-fx-border-width:1;-fx-padding:11;-fx-font-size:15px;-fx-font-family:monospace;");
            if (phoneError != null) { phoneError.setVisible(false); phoneError.setManaged(false); }
        } else {
            if (phoneField != null)
                phoneField.setStyle("-fx-background-color:#0D1525;-fx-text-fill:white;" +
                    "-fx-border-color:#1E3A5F;-fx-border-radius:0 8 8 0;-fx-background-radius:0 8 8 0;" +
                    "-fx-border-width:1;-fx-padding:11;-fx-font-size:15px;-fx-font-family:monospace;");
            if (digits.length() > 0 && phoneError != null) {
                phoneError.setText("Enter 10 digits");
                phoneError.setVisible(true); phoneError.setManaged(true);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CONFIRM BOOKING
    // ══════════════════════════════════════════════════════════════
    @FXML public void handleConfirmBooking() {
        String name  = nameField  != null ? nameField.getText().trim() : "";
        String plate = plateField != null ? plateField.getText().trim().toUpperCase().replaceAll("[\\s\\-]", "") : "";
        String phone = phoneField != null ? phoneField.getText().trim().replaceAll("[^0-9]", "") : "";
        if (name.isEmpty())     { if (step2Error != null) step2Error.setText("Please enter your name!"); return; }
        if (plate.isEmpty())    { if (step2Error != null) step2Error.setText("Please enter license plate!"); return; }
        if (phone.length()!=10) { if (step2Error != null) step2Error.setText("Please enter a valid 10-digit mobile number!"); return; }
        PlateDecoderService.PlateResult pr = plateDecoder.decode(plate);
        if (!pr.valid) { if (step2Error != null) step2Error.setText("Invalid plate: " + pr.error); return; }
        if (!parkingService.lockSlot(selected.getId(), UID)) {
            if (step2Error != null) step2Error.setText("Slot just taken! Go back."); return;
        }
        Vehicle v = new Vehicle(0, pr.formatted.replaceAll("[\\s\\-]", ""), name, selected.getType(), phone);
        v = vehicleDAO.save(v);
        parkingService.confirmBooking(selected.getId(), UID);
        ParkingSession sess = sessionDAO.startSession(v.getId(), selected.getId(), UID);
        if (sess == null) { if (step2Error != null) step2Error.setText("Error! Please try again."); return; }
        String qrData = QRCodeService.buildTicketData(sess.getId(), pr.formatted, selected.getSlotNumber(), sess.getEntryTime().format(FMT));
        if (qrView    != null) qrView.setImage(QRCodeService.generateQR(qrData, 200));
        if (tktId     != null) tktId.setText("TKT-" + String.format("%05d", sess.getId()));
        if (tktName   != null) tktName.setText(name);
        if (tktPlate  != null) tktPlate.setText(pr.formatted);
        if (tktSlot   != null) tktSlot.setText(selected.getSlotNumber());
        if (tktType   != null) tktType.setText(
            selected.getType() == VehicleType.BIKE ? "Bike (2-Wheeler)" :
            selected.getType() == VehicleType.CAR  ? "Car (4-Wheeler)"  : "SUV (4-Wheeler)");
        if (tktTime   != null) tktTime.setText(sess.getEntryTime().format(FMT));
        if (tktPhone  != null) tktPhone.setText("+91 " + phone.substring(0,5) + " ***" + phone.substring(7));
        show(3);
        startCd();
    }

    // ══════════════════════════════════════════════════════════════
    // UTILS
    // ══════════════════════════════════════════════════════════════
    private void startCd() {
        final int[] r = {30};
        cdTL = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            r[0]--;
            if (cdLabel != null) cdLabel.setText("Returning in " + r[0] + "s");
            if (r[0] <= 0) { cdTL.stop(); handleDone(); }
        }));
        cdTL.setCycleCount(30); cdTL.play();
    }

    @FXML public void handleDone() { if (cdTL!=null) cdTL.stop(); stop(); load("welcome.fxml"); }
    @FXML public void handleBack() { stop(); load("welcome.fxml"); }

    private void show(int s) {
        step1.setVisible(s==1); step1.setManaged(s==1);
        step2.setVisible(s==2); step2.setManaged(s==2);
        step3.setVisible(s==3); step3.setManaged(s==3);
    }
    private void startPulse() {
        pulseTL = new Timeline(
            new KeyFrame(Duration.ZERO,       e -> { pulse = false; draw(); }),
            new KeyFrame(Duration.seconds(1), e -> { pulse = true;  draw(); }),
            new KeyFrame(Duration.seconds(2), e -> { pulse = false; draw(); })
        );
        pulseTL.setCycleCount(Animation.INDEFINITE); pulseTL.play();
    }
    private void stop() { if (pulseTL!=null) pulseTL.stop(); if (cdTL!=null) cdTL.stop(); }
    private void load(String f) { com.snappark.Main.switchScene(f); }
}