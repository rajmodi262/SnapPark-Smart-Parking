package com.snappark.controller;

import com.snappark.model.ParkingSlot;
import com.snappark.model.enums.SlotStatus;
import com.snappark.model.enums.VehicleType;
import com.snappark.service.AuthService;
import com.snappark.service.HeatmapService;
import com.snappark.service.ParkingService;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class ParkingGrid3DController implements Initializable {

    @FXML private StackPane container;
    @FXML private Label     statusLabel;
    @FXML private Label     availableLabel;
    @FXML private Label     occupiedLabel;
    @FXML private Label     lockingLabel;
    @FXML private Label     filterLabel;
    @FXML private ComboBox<String> floorSelector;
    @FXML private Button    btnAll;
    @FXML private Button    btnTwo;
    @FXML private Button    btnFour;
    @FXML private Button    btnHeatmap;
    @FXML private Label     heatmapModeLabel;

    private final ParkingService  parkingService  = ParkingService.getInstance();
    private final AuthService     authService     = AuthService.getInstance();
    private final HeatmapService  heatmapService  = HeatmapService.getInstance();

    private Canvas   canvas;
    private int      currentFloor  = 1;
    private String   currentFilter = "ALL";
    private boolean  pulseState    = false;
    private boolean  heatmapMode   = false;
    private Timeline pulseTimeline;
    private List<ParkingSlot>      allSlots      = new ArrayList<>();
    private List<ParkingSlot>      filteredSlots = new ArrayList<>();
    private Map<Integer, Integer>  heatData      = new HashMap<>();
    private int                    maxHeat       = 1;

    private static final double TW  = 110;
    private static final double TD  = 130;
    private static final double TH  = 18;
    private static final double GAP = 16;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        floorSelector.getItems().addAll("Floor 1", "Floor 2");
        floorSelector.getSelectionModel().selectFirst();
        floorSelector.setOnAction(e -> {
            currentFloor = floorSelector.getSelectionModel().getSelectedIndex() + 1;
            refreshDraw();
        });
        canvas = new Canvas();
        canvas.widthProperty().bind(container.widthProperty());
        canvas.heightProperty().bind(container.heightProperty());
        canvas.widthProperty().addListener((o, ov, nv)  -> draw());
        canvas.heightProperty().addListener((o, ov, nv) -> draw());
        canvas.setOnMouseClicked(e -> handleCanvasClick(e.getX(), e.getY()));
        container.getChildren().add(canvas);
        startPulse();
        Platform.runLater(this::refreshDraw);
    }

    // ── FILTER ACTIONS ─────────────────────────────────────────────
    @FXML public void filterAll() {
        currentFilter = "ALL";
        setActiveButton(btnAll);
        if (filterLabel != null) filterLabel.setText("SHOWING: ALL SLOTS");
        refreshDraw();
    }
    @FXML public void filterTwoWheeler() {
        currentFilter = "TWO";
        setActiveButton(btnTwo);
        if (filterLabel != null) filterLabel.setText("SHOWING: 2-WHEELER (BIKE)");
        refreshDraw();
    }
    @FXML public void filterFourWheeler() {
        currentFilter = "FOUR";
        setActiveButton(btnFour);
        if (filterLabel != null) filterLabel.setText("SHOWING: 4-WHEELER (CAR + SUV)");
        refreshDraw();
    }

    // ── HEATMAP TOGGLE ─────────────────────────────────────────────
    @FXML public void toggleHeatmap() {
        heatmapMode = !heatmapMode;
        if (heatmapMode) {
            heatData = heatmapService.getAllTimeBookings();
            maxHeat  = heatmapService.getMaxCount(heatData);
            if (maxHeat == 0) maxHeat = 1;
            if (btnHeatmap != null)
                btnHeatmap.setStyle("-fx-background-color:#FF6600;-fx-text-fill:#0A0F1A;-fx-font-family:monospace;-fx-font-size:11px;-fx-font-weight:bold;-fx-cursor:hand;-fx-background-radius:4;-fx-padding:5 12 5 12;");
            if (heatmapModeLabel != null)
                heatmapModeLabel.setText("HEATMAP ON  //  showing all-time booking frequency");
        } else {
            heatData.clear();
            if (btnHeatmap != null)
                btnHeatmap.setStyle("-fx-background-color:#0D1525;-fx-text-fill:#FF6600;-fx-border-color:#FF6600;-fx-border-width:1;-fx-font-family:monospace;-fx-font-size:11px;-fx-cursor:hand;-fx-background-radius:4;-fx-border-radius:4;-fx-padding:5 12 5 12;");
            if (heatmapModeLabel != null)
                heatmapModeLabel.setText("");
        }
        draw();
    }

    private void setActiveButton(Button active) {
        String off = "-fx-background-color:#0D1525;-fx-background-radius:4;-fx-border-radius:4;-fx-font-family:monospace;-fx-font-size:11px;-fx-cursor:hand;-fx-padding:5 12 5 12;-fx-border-width:1;";
        if (btnAll  != null) btnAll.setStyle( off+"-fx-text-fill:#00C2FF;-fx-border-color:#00C2FF;");
        if (btnTwo  != null) btnTwo.setStyle( off+"-fx-text-fill:#00FF88;-fx-border-color:#00FF88;");
        if (btnFour != null) btnFour.setStyle(off+"-fx-text-fill:#00C2FF;-fx-border-color:#00C2FF;");
        String on = "-fx-font-family:monospace;-fx-font-size:11px;-fx-font-weight:bold;-fx-cursor:hand;-fx-background-radius:4;-fx-padding:5 12 5 12;-fx-text-fill:#0A0F1A;";
        if (active==btnAll  && btnAll  !=null) btnAll.setStyle( on+"-fx-background-color:#00C2FF;");
        if (active==btnTwo  && btnTwo  !=null) btnTwo.setStyle( on+"-fx-background-color:#00FF88;");
        if (active==btnFour && btnFour !=null) btnFour.setStyle(on+"-fx-background-color:#00C2FF;");
    }

    private List<ParkingSlot> applyFilter(List<ParkingSlot> slots) {
        return switch (currentFilter) {
            case "TWO"  -> slots.stream().filter(s -> s.getType()==VehicleType.BIKE).collect(Collectors.toList());
            case "FOUR" -> slots.stream().filter(s -> s.getType()!=VehicleType.BIKE).collect(Collectors.toList());
            default     -> new ArrayList<>(slots);
        };
    }

    private void refreshDraw() {
        allSlots      = parkingService.getSlotsByFloor(currentFloor);
        filteredSlots = applyFilter(allSlots);
        if (heatmapMode) {
            heatData = heatmapService.getAllTimeBookings();
            maxHeat  = heatmapService.getMaxCount(heatData);
            if (maxHeat == 0) maxHeat = 1;
        }
        updateStats(filteredSlots);
        draw();
    }

    // ── ISO PROJECTION ─────────────────────────────────────────────
    private double[] iso(double wx, double wz, double wy, double ox, double oy) {
        return new double[]{ ox+(wx-wz)*0.52, oy+(wx+wz)*0.27-wy*0.72 };
    }

    // ── DRAW ───────────────────────────────────────────────────────
    private void draw() {
        double W = canvas.getWidth(), H = canvas.getHeight();
        if (W<=0||H<=0) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0,0,W,H);
        gc.setFill(Color.web("#05080F")); gc.fillRect(0,0,W,H);

        if (filteredSlots.isEmpty()) {
            gc.setFill(Color.web("#00C2FF",0.5)); gc.setFont(Font.font("monospace",16));
            gc.fillText("NO SLOTS FOR SELECTED TYPE", W/2-160, H/2);
            drawScanlines(gc,W,H); drawHUD(gc,W,H); return;
        }
        int total=filteredSlots.size(), COLS=Math.min(5,total), ROWS=(int)Math.ceil((double)total/COLS);
        double stepX=TW+GAP, stepZ=TD+GAP;
        double cX=(COLS-1)*stepX/2.0, cZ=(ROWS-1)*stepZ/2.0;
        double offX=W*0.50, offY=H*0.46+(ROWS*14);

        drawFloorBase(gc,COLS,ROWS,stepX,stepZ,cX,cZ,offX,offY);
        for (int row=ROWS-1;row>=0;row--)
            for (int col=0;col<COLS;col++) {
                int idx=row*COLS+col;
                if (idx<filteredSlots.size())
                    drawSlot(gc,filteredSlots.get(idx),col*stepX-cX,row*stepZ-cZ,offX,offY);
            }
        drawScanlines(gc,W,H);
        drawHUD(gc,W,H);
        if (heatmapMode) drawHeatLegend(gc,W,H);
    }

    private void drawFloorBase(GraphicsContext gc,int cols,int rows,
                                double stepX,double stepZ,
                                double cX,double cZ,double ox,double oy) {
        gc.setStroke(Color.web("#0D2040",0.6)); gc.setLineWidth(0.8);
        for (int i=0;i<=cols;i++) {
            double wx=i*stepX-cX-stepX/2;
            double[] p1=iso(wx,-stepZ/2-cZ,0,ox,oy), p2=iso(wx,rows*stepZ-stepZ/2-cZ,0,ox,oy);
            gc.strokeLine(p1[0],p1[1],p2[0],p2[1]);
        }
        for (int i=0;i<=rows;i++) {
            double wz=i*stepZ-cZ-stepZ/2;
            double[] p1=iso(-stepX/2-cX,wz,0,ox,oy), p2=iso(cols*stepX-stepX/2-cX,wz,0,ox,oy);
            gc.strokeLine(p1[0],p1[1],p2[0],p2[1]);
        }
    }

    // ── SLOT DRAW ──────────────────────────────────────────────────
    private void drawSlot(GraphicsContext gc,ParkingSlot slot,
                          double wx,double wz,double ox,double oy) {
        double hw=TW/2, hd=TD/2;
        Color topC,leftC,rightC,neonC;
        switch (slot.getStatus()) {
            case OCCUPIED -> {topC=Color.web("#3D0010");leftC=Color.web("#220008");rightC=Color.web("#150005");neonC=Color.web("#FF2244");}
            case LOCKING  -> {topC=pulseState?Color.web("#4A3200"):Color.web("#2A1C00");leftC=Color.web("#1A1000");rightC=Color.web("#0F0900");neonC=Color.web("#FFB800");}
            default       -> {topC=Color.web("#002B18");leftC=Color.web("#001610");rightC=Color.web("#000D09");neonC=Color.web("#00FF88");}
        }

        double[] BFL=iso(wx-hw,wz+hd,0, ox,oy), BFR=iso(wx+hw,wz+hd,0, ox,oy);
        double[] BBR=iso(wx+hw,wz-hd,0, ox,oy);
        double[] TFL=iso(wx-hw,wz+hd,TH,ox,oy), TFR=iso(wx+hw,wz+hd,TH,ox,oy);
        double[] TBL=iso(wx-hw,wz-hd,TH,ox,oy), TBR=iso(wx+hw,wz-hd,TH,ox,oy);

        // ── Base slot faces ──────────────────────────────────────
        gc.setFill(leftC);
        gc.fillPolygon(new double[]{TFL[0],BFL[0],BFR[0],TFR[0]},
                       new double[]{TFL[1],BFL[1],BFR[1],TFR[1]},4);
        gc.setFill(rightC);
        gc.fillPolygon(new double[]{TFR[0],BFR[0],BBR[0],TBR[0]},
                       new double[]{TFR[1],BFR[1],BBR[1],TBR[1]},4);
        gc.setFill(topC);
        gc.fillPolygon(new double[]{TFL[0],TFR[0],TBR[0],TBL[0]},
                       new double[]{TFL[1],TFR[1],TBR[1],TBL[1]},4);

        // ── HEATMAP OVERLAY on top face ──────────────────────────
        if (heatmapMode) {
            int count   = heatData.getOrDefault(slot.getId(), 0);
            Color heat  = heatmapService.getHeatColor(count, maxHeat);
            Color glow  = heatmapService.getGlowColor(count, maxHeat);

            if (count > 0) {
                // Colored overlay on top face
                gc.setFill(heat);
                gc.fillPolygon(new double[]{TFL[0],TFR[0],TBR[0],TBL[0]},
                               new double[]{TFL[1],TFR[1],TBR[1],TBL[1]},4);
                // Side face tint (left)
                gc.setFill(heat.deriveColor(0,1,0.7,0.5));
                gc.fillPolygon(new double[]{TFL[0],BFL[0],BFR[0],TFR[0]},
                               new double[]{TFL[1],BFL[1],BFR[1],TFR[1]},4);

                // Hot glow border on top face
                double bw = heatGlowWidth(count, maxHeat);
                if (bw > 0) {
                    gc.setStroke(glow); gc.setLineWidth(bw); gc.setGlobalAlpha(0.9);
                    gc.strokePolygon(new double[]{TFL[0],TFR[0],TBR[0],TBL[0]},
                                     new double[]{TFL[1],TFR[1],TBR[1],TBL[1]},4);
                    gc.setGlobalAlpha(1.0);
                }

                // Booking count badge above slot
                double[] lp = iso(wx, wz, TH + 18, ox, oy);
                String label = heatmapService.getHeatLabel(count);
                gc.setFont(Font.font("monospace", 10));
                // Badge background
                gc.setFill(glow);
                gc.setGlobalAlpha(0.85);
                gc.fillRoundRect(lp[0]-16, lp[1]-10, 32, 14, 4, 4);
                gc.setGlobalAlpha(1.0);
                // Badge text
                gc.setFill(Color.web("#0A0F1A"));
                gc.setFont(Font.font("monospace", 9));
                gc.fillText(label, lp[0] - (label.length() > 2 ? 10 : 7), lp[1] + 1);
            }
        }

        // ── Neon border ───────────────────────────────────────────
        double na = (slot.getStatus()==SlotStatus.LOCKING && !pulseState) ? 0.4 : 1.0;
        Color borderColor = heatmapMode && heatData.getOrDefault(slot.getId(),0) > 0
            ? heatmapService.getGlowColor(heatData.get(slot.getId()), maxHeat)
            : neonC;
        gc.setStroke(borderColor); gc.setLineWidth(1.6); gc.setGlobalAlpha(na);
        gc.strokePolygon(new double[]{TFL[0],TFR[0],TBR[0],TBL[0]},
                         new double[]{TFL[1],TFR[1],TBR[1],TBL[1]},4);
        gc.setLineWidth(0.7); gc.setGlobalAlpha(na*0.5);
        gc.strokeLine(TFL[0],TFL[1],BFL[0],BFL[1]);
        gc.strokeLine(TFR[0],TFR[1],BFR[0],BFR[1]);
        gc.setGlobalAlpha(1.0);

        // ── Vehicle model ─────────────────────────────────────────
        if (slot.getStatus()!=SlotStatus.AVAILABLE) {
            switch (slot.getType()) {
                case BIKE -> drawBike(gc,wx,wz,ox,oy);
                case CAR  -> drawCar(gc,wx,wz,ox,oy);
                case SUV  -> drawSUV(gc,wx,wz,ox,oy);
            }
        }

        // ── Slot labels ───────────────────────────────────────────
        double[] lp = iso(wx,wz,TH+4,ox,oy);
        gc.setFill(heatmapMode ? Color.WHITE : neonC);
        gc.setFont(Font.font("monospace",8.5)); gc.setGlobalAlpha(0.9);
        gc.fillText(slot.getSlotNumber(),lp[0]-14,lp[1]+3);
        gc.setFill(Color.web("#4488AA")); gc.setFont(Font.font("monospace",7.5));
        gc.fillText(slot.getType()==VehicleType.BIKE?"2W":slot.getType()==VehicleType.CAR?"CAR":"SUV",
                    lp[0]+10,lp[1]+3);
        gc.setGlobalAlpha(1.0);
    }

    private double heatGlowWidth(int count, int max) {
        if (max==0||count==0) return 0;
        double t = (double) count / max;
        if (t >= 0.75) return 3.0;
        if (t >= 0.5)  return 2.0;
        if (t >= 0.25) return 1.2;
        return 0;
    }

    // ── HEATMAP LEGEND ─────────────────────────────────────────────
    private void drawHeatLegend(GraphicsContext gc, double W, double H) {
        double lx = W - 180, ly = H - 130;
        // Background card
        gc.setFill(Color.web("#0A0F1A", 0.92));
        gc.fillRoundRect(lx-10, ly-20, 170, 115, 10, 10);
        gc.setStroke(Color.web("#FF6600", 0.7)); gc.setLineWidth(1);
        gc.strokeRoundRect(lx-10, ly-20, 170, 115, 10, 10);

        gc.setFill(Color.web("#FF6600")); gc.setFont(Font.font("monospace", 10));
        gc.fillText("BOOKING HEATMAP", lx-2, ly-4);

        // Color gradient bar
        int barW = 145, barH = 12;
        for (int i = 0; i < barW; i++) {
            double t = (double) i / barW;
            Color c = heatmapService.getHeatColor((int)(t * 10), 10);
            gc.setFill(c.deriveColor(0, 1, 1, Math.min(1.0, c.getOpacity() * 2)));
            gc.fillRect(lx + i, ly + 8, 1, barH);
        }
        // Bar outline
        gc.setStroke(Color.web("#333333")); gc.setLineWidth(0.5);
        gc.strokeRect(lx, ly + 8, barW, barH);

        // Labels under bar
        gc.setFill(Color.web("#64748B")); gc.setFont(Font.font("monospace", 9));
        gc.fillText("0",         lx,         ly + 34);
        gc.fillText("low",       lx + 28,    ly + 34);
        gc.fillText("medium",    lx + 52,    ly + 34);
        gc.fillText("high",      lx + 98,    ly + 34);
        gc.fillText(maxHeat+"x", lx + barW-12, ly + 34);

        // Stats row
        long zeroSlots = filteredSlots.stream()
            .filter(s -> heatData.getOrDefault(s.getId(), 0) == 0).count();
        long hotSlots  = filteredSlots.stream()
            .filter(s -> heatData.getOrDefault(s.getId(), 0) >= Math.max(1, maxHeat/2)).count();

        gc.setFill(Color.web("#64748B")); gc.setFont(Font.font("monospace", 9));
        gc.fillText("Never booked: " + zeroSlots + " slots", lx, ly + 54);
        gc.fillText("Hot slots:    " + hotSlots  + " slots", lx, ly + 68);
        gc.fillText("Peak slot:    " + maxHeat + " bookings", lx, ly + 82);
    }

    // ── VEHICLE MODELS ─────────────────────────────────────────────
    private void isoBox(GraphicsContext gc,double wx,double wz,double base,
                        double bw,double bd,double bh,
                        Color top,Color left,Color right,double ox,double oy){
        double hw=bw/2,hd=bd/2;
        double[]BFL=iso(wx-hw,wz+hd,base,ox,oy),BFR=iso(wx+hw,wz+hd,base,ox,oy),BBR=iso(wx+hw,wz-hd,base,ox,oy);
        double[]TFL=iso(wx-hw,wz+hd,base+bh,ox,oy),TFR=iso(wx+hw,wz+hd,base+bh,ox,oy);
        double[]TBL=iso(wx-hw,wz-hd,base+bh,ox,oy),TBR=iso(wx+hw,wz-hd,base+bh,ox,oy);
        gc.setFill(left);  gc.fillPolygon(new double[]{TFL[0],BFL[0],BFR[0],TFR[0]},new double[]{TFL[1],BFL[1],BFR[1],TFR[1]},4);
        gc.setFill(right); gc.fillPolygon(new double[]{TFR[0],BFR[0],BBR[0],TBR[0]},new double[]{TFR[1],BFR[1],BBR[1],TBR[1]},4);
        gc.setFill(top);   gc.fillPolygon(new double[]{TFL[0],TFR[0],TBR[0],TBL[0]},new double[]{TFL[1],TFR[1],TBR[1],TBL[1]},4);
    }

    private void drawCar(GraphicsContext gc,double wx,double wz,double ox,double oy){
        double base=TH;
        Color body=Color.web("#1A3A6A"),bodyL=Color.web("#0F2244"),bodyR=Color.web("#0A1830");
        Color glass=Color.web("#00AADD",0.75),wheel=Color.web("#111111"),wRim=Color.web("#888899");
        isoBox(gc,wx,wz,base,52,88,12,body,bodyL,bodyR,ox,oy);
        isoBox(gc,wx,wz-4,base+12,40,46,10,body.brighter(),bodyL.brighter(),bodyR.brighter(),ox,oy);
        double[]W1=iso(wx-16,wz+14,base+12,ox,oy),W2=iso(wx+16,wz+14,base+12,ox,oy);
        double[]W3=iso(wx+18,wz+20,base+12,ox,oy),W4=iso(wx-18,wz+20,base+12,ox,oy);
        gc.setFill(glass);gc.fillPolygon(new double[]{W1[0],W2[0],W3[0],W4[0]},new double[]{W1[1],W2[1],W3[1],W4[1]},4);
        for(double[]wp:new double[][]{{-22,-34},{22,-34},{-22,34},{22,34}}){
            isoBox(gc,wx+wp[0],wz+wp[1],base-2,10,16,9,wheel,wheel,wheel,ox,oy);
            isoBox(gc,wx+wp[0],wz+wp[1],base+2, 6,10,4,wRim,wRim.darker(),wRim.darker(),ox,oy);
        }
        isoBox(gc,wx-18,wz+44,base+4,8,4,5,Color.web("#FFFFAA",0.95),Color.web("#DDDD88",0.8),Color.web("#BBBB66",0.6),ox,oy);
        isoBox(gc,wx+18,wz+44,base+4,8,4,5,Color.web("#FFFFAA",0.95),Color.web("#DDDD88",0.8),Color.web("#BBBB66",0.6),ox,oy);
        isoBox(gc,wx-18,wz-44,base+4,8,4,5,Color.web("#FF2222",0.9),Color.web("#CC1111",0.7),Color.web("#AA0000",0.5),ox,oy);
        isoBox(gc,wx+18,wz-44,base+4,8,4,5,Color.web("#FF2222",0.9),Color.web("#CC1111",0.7),Color.web("#AA0000",0.5),ox,oy);
    }

    private void drawSUV(GraphicsContext gc,double wx,double wz,double ox,double oy){
        double base=TH;
        Color body=Color.web("#2A1A0A"),bodyL=Color.web("#1A0F06"),bodyR=Color.web("#110A04");
        Color glass=Color.web("#004466",0.8),wheel=Color.web("#111111"),wRim=Color.web("#778899");
        isoBox(gc,wx,wz,base,62,96,18,body,bodyL,bodyR,ox,oy);
        isoBox(gc,wx,wz-2,base+18,58,60,4,Color.web("#333333"),Color.web("#222222"),Color.web("#1A1A1A"),ox,oy);
        double[]W1=iso(wx-22,wz+24,base+14,ox,oy),W2=iso(wx+22,wz+24,base+14,ox,oy);
        double[]W3=iso(wx+24,wz+30,base+14,ox,oy),W4=iso(wx-24,wz+30,base+14,ox,oy);
        gc.setFill(glass);gc.fillPolygon(new double[]{W1[0],W2[0],W3[0],W4[0]},new double[]{W1[1],W2[1],W3[1],W4[1]},4);
        for(double[]wp:new double[][]{{-26,-38},{26,-38},{-26,38},{26,38}}){
            isoBox(gc,wx+wp[0],wz+wp[1],base-3,14,20,12,wheel,wheel,wheel,ox,oy);
            isoBox(gc,wx+wp[0],wz+wp[1],base+2, 8,12, 5,wRim,wRim.darker(),wRim.darker(),ox,oy);
        }
        isoBox(gc,wx,wz+50,base+2,55,4,6,Color.web("#444444"),Color.web("#333333"),Color.web("#222222"),ox,oy);
    }

    private void drawBike(GraphicsContext gc,double wx,double wz,double ox,double oy){
        double base=TH;
        Color body=Color.web("#CC3300"),bodyD=Color.web("#881100");
        Color metal=Color.web("#888888"),metalD=Color.web("#555555");
        Color wheel=Color.web("#111111"),wheelC=Color.web("#666666"),seat=Color.web("#1A1A1A");
        isoBox(gc,wx,wz+30,base,  8,18,14,wheel,wheel.brighter(),wheel.brighter(),ox,oy);
        isoBox(gc,wx,wz+30,base+4,4,10, 6,wheelC,wheelC.darker(),wheelC.darker(),ox,oy);
        isoBox(gc,wx,wz-30,base,  8,18,14,wheel,wheel.brighter(),wheel.brighter(),ox,oy);
        isoBox(gc,wx,wz-30,base+4,4,10, 6,wheelC,wheelC.darker(),wheelC.darker(),ox,oy);
        isoBox(gc,wx,wz,base+6,6,44,6,metal,metalD,metalD,ox,oy);
        isoBox(gc,wx,wz+2,base+2,14,22,12,body,bodyD,bodyD.darker(),ox,oy);
        isoBox(gc,wx,wz-4,base+14,12,18,8,body.brighter(),body,bodyD,ox,oy);
        isoBox(gc,wx,wz-16,base+20,10,24,5,seat,seat.brighter(),seat.brighter(),ox,oy);
        isoBox(gc,wx,wz+22,base+28,28,4,4,metal,metalD,metalD,ox,oy);
    }

    // ── CLICK TO BOOK ──────────────────────────────────────────────
    private void handleCanvasClick(double mx,double my) {
        if (filteredSlots.isEmpty()) return;
        int total=filteredSlots.size(),COLS=Math.min(5,total),ROWS=(int)Math.ceil((double)total/COLS);
        double stepX=TW+GAP,stepZ=TD+GAP;
        double cX=(COLS-1)*stepX/2.0,cZ=(ROWS-1)*stepZ/2.0;
        double ox=canvas.getWidth()*0.50,oy=canvas.getHeight()*0.50+(ROWS*18);
        ParkingSlot best=null; double bestD=Double.MAX_VALUE;
        for (int i=0;i<filteredSlots.size();i++) {
            double[]sc=iso((i%COLS)*stepX-cX,(i/COLS)*stepZ-cZ,TH,ox,oy);
            double d=Math.hypot(mx-sc[0],my-sc[1]);
            if (d<bestD){bestD=d;best=filteredSlots.get(i);}
        }
        if (best!=null&&bestD<75&&best.getStatus()==SlotStatus.AVAILABLE) handleSlotBook(best);
    }

    private void handleSlotBook(ParkingSlot slot) {
        var user=authService.getCurrentUser(); if (user==null) return;
        int cnt = heatData.getOrDefault(slot.getId(), 0);
        String heatInfo = heatmapMode && cnt > 0
            ? "\n\nThis slot has been booked " + cnt + " times before."
            : "";
        Alert dlg=new Alert(Alert.AlertType.CONFIRMATION);
        dlg.setTitle("Book Slot");
        dlg.setHeaderText(slot.getSlotNumber()+"  ["+slot.getType()+"]");
        dlg.setContentText("Lock this slot for 5 minutes?" + heatInfo);
        dlg.showAndWait().ifPresent(r -> {
            if (r==ButtonType.OK) {
                boolean ok=parkingService.lockSlot(slot.getId(),user.getId());
                if (statusLabel!=null) statusLabel.setText(ok
                    ?">> "+slot.getSlotNumber()+" LOCKED - complete entry within 5 min."
                    :">> SLOT ALREADY TAKEN - choose another.");
                if (ok) refreshDraw();
            }
        });
    }

    // ── PULSE ──────────────────────────────────────────────────────
    private void startPulse() {
        pulseTimeline=new Timeline(
            new KeyFrame(Duration.ZERO,       e->{pulseState=false;draw();}),
            new KeyFrame(Duration.seconds(1), e->{pulseState=true; draw();}),
            new KeyFrame(Duration.seconds(2), e->{pulseState=false;draw();})
        );
        pulseTimeline.setCycleCount(Animation.INDEFINITE);
        pulseTimeline.play();
    }

    private void drawScanlines(GraphicsContext gc,double W,double H){
        gc.setFill(Color.color(0,0,0,0.032));
        for(int y=0;y<H;y+=3) gc.fillRect(0,y,W,1.5);
    }

    private void drawHUD(GraphicsContext gc,double W,double H){
        gc.setStroke(Color.web("#00C2FF",0.3)); gc.setLineWidth(1.2);
        double m=18,s=28;
        gc.strokeLine(m,m,m+s,m);       gc.strokeLine(m,m,m,m+s);
        gc.strokeLine(W-m,m,W-m-s,m);   gc.strokeLine(W-m,m,W-m,m+s);
        gc.strokeLine(m,H-m,m+s,H-m);   gc.strokeLine(m,H-m,m,H-m-s);
        gc.strokeLine(W-m,H-m,W-m-s,H-m); gc.strokeLine(W-m,H-m,W-m,H-m-s);
        gc.setFill(Color.web("#00C2FF",0.35)); gc.setFont(Font.font("monospace",10));
        gc.fillText("FLOOR "+currentFloor+"  //  "+filteredSlots.size()+" SLOTS",m+6,H-m-6);
        if (heatmapMode) {
            gc.setFill(Color.web("#FF6600",0.8));
            gc.fillText("HEATMAP MODE ACTIVE",m+6,H-m-22);
        }
    }

    private void updateStats(List<ParkingSlot> slots){
        long a=slots.stream().filter(s->s.getStatus()==SlotStatus.AVAILABLE).count();
        long o=slots.stream().filter(s->s.getStatus()==SlotStatus.OCCUPIED).count();
        long l=slots.stream().filter(s->s.getStatus()==SlotStatus.LOCKING).count();
        if(availableLabel!=null) availableLabel.setText(String.valueOf(a));
        if(occupiedLabel !=null) occupiedLabel.setText(String.valueOf(o));
        if(lockingLabel  !=null) lockingLabel.setText(String.valueOf(l));
    }

    @FXML public void handleRefresh() { refreshDraw(); }
    @FXML public void resetCamera()   { refreshDraw(); }
}