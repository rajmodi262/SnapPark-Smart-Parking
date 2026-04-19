package com.snappark.controller;

import com.snappark.dao.SessionDAO;
import com.snappark.dao.TransactionDAO;
import com.snappark.dao.VehicleDAO;
import com.snappark.model.ParkingSession;
import com.snappark.model.ParkingSlot;
import com.snappark.model.Transaction;
import com.snappark.model.Vehicle;
import com.snappark.service.BillingService;
import com.snappark.service.ParkingService;
import com.snappark.service.QRCodeService;
import com.snappark.service.SMSService;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class KioskExitController implements Initializable {

    @FXML private StackPane rootPane;
    @FXML private VBox step1, step2, step3, step3cash, step3upi, step3pin, step3success, step4;

    // Step 1
    @FXML private Label ticketDisplay, exitStatus;

    // Step 2
    @FXML private Label billPlate, billOwner, billSlot, billEntry, billDuration, billTotal;

    // Step 3 choose
    @FXML private Label payAmountHeader, cashAmountLabel, upiAmountLabel;

    // Step 3A cash
    @FXML private Label cashPayLabel;

    // Step 3B UPI
    @FXML private ImageView upiQRView;
    @FXML private Label     upiPayLabel, upiTimerLabel;

    // Step 3D PIN
    @FXML private Label pinSentLabel, demoPinLabel, pinError;
    @FXML private Label pinBox1, pinBox2, pinBox3, pinBox4;

    // Step 3C success
    @FXML private Canvas checkCanvas;
    @FXML private Label  successTitle, successSub;

    // Step 4
    @FXML private TextArea  receiptArea;
    @FXML private ImageView receiptQR;
    @FXML private Label     cdLabel;

    private final SessionDAO     sessionDAO     = new SessionDAO();
    private final VehicleDAO     vehicleDAO     = new VehicleDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final BillingService billingService = BillingService.getInstance();
    private final ParkingService parkingService = ParkingService.getInstance();
    private final SMSService     smsService     = SMSService.getInstance();

    private final StringBuilder typed    = new StringBuilder();
    private final StringBuilder pinTyped = new StringBuilder();

    private ParkingSession currentSession;
    private Vehicle        currentVehicle;
    private ParkingSlot    currentSlot;
    private double         currentAmount;
    private String         currentPIN;
    private String         currentPayMethod;
    private Timeline       cdTL, upiTimerTL;
    private LocalDateTime  exitTime;

    private static final DateTimeFormatter FMT  = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter FMT2 = DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle rb) { updateDisplay(); }

    // ── TICKET NUMPAD ──────────────────────────────────────────────
    @FXML public void num0(){digit("0");} @FXML public void num1(){digit("1");}
    @FXML public void num2(){digit("2");} @FXML public void num3(){digit("3");}
    @FXML public void num4(){digit("4");} @FXML public void num5(){digit("5");}
    @FXML public void num6(){digit("6");} @FXML public void num7(){digit("7");}
    @FXML public void num8(){digit("8");} @FXML public void num9(){digit("9");}
    @FXML public void numDel()   { if(typed.length()>0){typed.deleteCharAt(typed.length()-1);updateDisplay();} }
    @FXML public void numClear() { typed.setLength(0); updateDisplay(); if(exitStatus!=null)exitStatus.setText(""); }
    private void digit(String d) { if(typed.length()<8){typed.append(d);updateDisplay();} }
    private void updateDisplay()  { if(ticketDisplay!=null)ticketDisplay.setText(typed.length()==0?"_ _ _ _ _":typed.toString()); }

    // ── PIN NUMPAD ─────────────────────────────────────────────────
    @FXML public void pinNum0(){pinDigit("0");} @FXML public void pinNum1(){pinDigit("1");}
    @FXML public void pinNum2(){pinDigit("2");} @FXML public void pinNum3(){pinDigit("3");}
    @FXML public void pinNum4(){pinDigit("4");} @FXML public void pinNum5(){pinDigit("5");}
    @FXML public void pinNum6(){pinDigit("6");} @FXML public void pinNum7(){pinDigit("7");}
    @FXML public void pinNum8(){pinDigit("8");} @FXML public void pinNum9(){pinDigit("9");}
    @FXML public void pinDel()   { if(pinTyped.length()>0){pinTyped.deleteCharAt(pinTyped.length()-1);updatePinBoxes();} }
    @FXML public void pinClear() { pinTyped.setLength(0); updatePinBoxes(); if(pinError!=null)pinError.setText(""); }

    private void pinDigit(String d) {
        if(pinTyped.length()<4){ pinTyped.append(d); updatePinBoxes(); }
    }

    private void updatePinBoxes() {
        Label[] boxes = {pinBox1, pinBox2, pinBox3, pinBox4};
        for(int i=0; i<4; i++){
            if(boxes[i]==null) continue;
            if(i < pinTyped.length()){
                boxes[i].setText("●");
                boxes[i].setStyle(
                    "-fx-text-fill:#00FF88;-fx-font-size:36px;-fx-font-weight:bold;" +
                    "-fx-font-family:monospace;-fx-background-color:#111828;" +
                    "-fx-background-radius:8;-fx-min-width:60;-fx-min-height:60;" +
                    "-fx-alignment:center;-fx-border-color:#00FF88;-fx-border-width:1;-fx-border-radius:8;");
            } else {
                boxes[i].setText("_");
                boxes[i].setStyle(
                    "-fx-text-fill:#7C3AED;-fx-font-size:36px;-fx-font-weight:bold;" +
                    "-fx-font-family:monospace;-fx-background-color:#111828;" +
                    "-fx-background-radius:8;-fx-min-width:60;-fx-min-height:60;" +
                    "-fx-alignment:center;-fx-border-color:#7C3AED;-fx-border-width:1;-fx-border-radius:8;");
            }
        }
    }

    // ── FIND TICKET ────────────────────────────────────────────────
    @FXML public void handleFind() {
        String s = typed.toString().trim();
        if(s.isEmpty()){ exitStatus.setText("Please enter your Ticket ID!"); return; }
        int id;
        try { id = Integer.parseInt(s); }
        catch(Exception e){ exitStatus.setText("Invalid Ticket ID!"); return; }

        List<ParkingSession> active = sessionDAO.findAllActive();
        currentSession = active.stream().filter(sess->sess.getId()==id).findFirst().orElse(null);
        if(currentSession==null){ exitStatus.setText("Ticket #"+id+" not found or already checked out!"); return; }

        currentVehicle = vehicleDAO.findAll().stream()
            .filter(v->v.getId()==currentSession.getVehicleId()).findFirst().orElse(null);
        currentSlot = parkingService.getAllSlots().stream()
            .filter(sl->sl.getId()==currentSession.getSlotId()).findFirst().orElse(null);

        LocalDateTime now = LocalDateTime.now();
        currentAmount = billingService.calculateFee(
            currentVehicle.getVehicleType(), currentSession.getEntryTime(), now);
        java.time.Duration dur = java.time.Duration.between(currentSession.getEntryTime(), now);

        if(billPlate   !=null) billPlate.setText(currentVehicle.getLicensePlate());
        if(billOwner   !=null) billOwner.setText(currentVehicle.getOwnerName());
        if(billSlot    !=null) billSlot.setText(currentSlot!=null?currentSlot.getSlotNumber():"?");
        if(billEntry   !=null) billEntry.setText(currentSession.getEntryTime().format(FMT));
        if(billDuration!=null) billDuration.setText(dur.toHours()+"h "+dur.toMinutesPart()+"m");
        if(billTotal   !=null) billTotal.setText("Rs. "+String.format("%.0f",currentAmount));
        show("step2");
    }

    // ── STEP 2 → 3 ─────────────────────────────────────────────────
    @FXML public void handleProceedToPayment() {
        String amtStr = "Rs. "+String.format("%.0f",currentAmount);
        if(payAmountHeader!=null) payAmountHeader.setText(amtStr);
        if(cashAmountLabel!=null) cashAmountLabel.setText(amtStr);
        if(upiAmountLabel !=null) upiAmountLabel.setText(amtStr);
        show("step3");
    }

    // ── PAYMENT METHOD ─────────────────────────────────────────────
    @FXML public void selectCash() { currentPayMethod="CASH"; showCash(); }
    @FXML public void selectUPI()  { currentPayMethod="UPI";  showUPI();  }

    private void showCash() {
        if(cashPayLabel!=null) cashPayLabel.setText("Rs. "+String.format("%.0f",currentAmount));
        show("step3cash");
    }

    private void showUPI() {
        if(upiPayLabel!=null) upiPayLabel.setText("Rs. "+String.format("%.0f",currentAmount));
        String upiData = "upi://pay?pa=SnapPark@upi&pn=SnapPark&am="
            +String.format("%.0f",currentAmount)
            +"&cu=INR&tn=TKT-"+String.format("%05d",currentSession.getId());
        if(upiQRView!=null)
            upiQRView.setImage(QRCodeService.generateQR(
                upiData, 220, Color.web("#7C3AED"), Color.web("#0D1525")));
        final int[] secs = {180};
        if(upiTimerTL!=null) upiTimerTL.stop();
        upiTimerTL = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secs[0]--;
            int m=secs[0]/60, s2=secs[0]%60;
            if(upiTimerLabel!=null)
                upiTimerLabel.setText("Expires in "+m+":"+String.format("%02d",s2));
            if(secs[0]<=0){ upiTimerTL.stop(); show("step3"); }
        }));
        upiTimerTL.setCycleCount(180);
        upiTimerTL.play();
        show("step3upi");
    }

    // ── CASH / UPI CONFIRM → PIN ───────────────────────────────────
    @FXML public void handleCashConfirm() { sendPINAndShowScreen(); }
    @FXML public void handleUPIConfirm()  { if(upiTimerTL!=null)upiTimerTL.stop(); sendPINAndShowScreen(); }

    private void sendPINAndShowScreen() {
        currentPIN = smsService.generatePIN();
        pinTyped.setLength(0);
        updatePinBoxes();
        if(pinError!=null) pinError.setText("");

        String phone  = currentVehicle!=null ? currentVehicle.getPhoneNumber() : "";
        String masked = smsService.maskPhone(phone);

        boolean sent = false;
        if(!phone.isBlank()) sent = smsService.sendPIN(phone, currentPIN, currentSession.getId());

        if(pinSentLabel!=null)
            pinSentLabel.setText(phone.isBlank()
                ? "No mobile on file. Use demo PIN below."
                : "Enter the 4-digit PIN sent to "+masked);

        if(demoPinLabel!=null)
            demoPinLabel.setText(
                smsService.isDemoMode() || phone.isBlank()
                    ? "[ DEMO: Your PIN is  "+currentPIN+"  ]"
                    : sent ? "SMS sent to "+masked : "SMS failed. Demo PIN: "+currentPIN);

        show("step3pin");
    }

    // ── VERIFY PIN ─────────────────────────────────────────────────
    @FXML public void handleVerifyPin() {
        if(pinTyped.length()<4){
            if(pinError!=null) pinError.setText("Please enter all 4 digits");
            return;
        }
        if(pinTyped.toString().equals(currentPIN)){
            finalisePayment(currentPayMethod);
        } else {
            if(pinError!=null) pinError.setText("Incorrect PIN. Please try again.");
            // Shake animation — no anonymous subclasses
            Label[] boxes = {pinBox1,pinBox2,pinBox3,pinBox4};
            for(Label b : boxes){
                if(b==null) continue;
                TranslateTransition shake = new TranslateTransition(Duration.millis(60), b);
                shake.setFromX(0); shake.setByX(8);
                shake.setCycleCount(4); shake.setAutoReverse(true);
                shake.play();
            }
            PauseTransition clear = new PauseTransition(Duration.millis(600));
            clear.setOnFinished(e -> { pinTyped.setLength(0); updatePinBoxes(); });
            clear.play();
        }
    }

    // ── FINALISE PAYMENT ──────────────────────────────────────────
    private void finalisePayment(String method) {
        exitTime = LocalDateTime.now();
        sessionDAO.endSession(currentSession.getId(), exitTime);
        parkingService.releaseSlot(currentSession.getSlotId());

        String receipt = billingService.generateReceipt(
            currentVehicle.getLicensePlate(),
            currentSlot!=null ? currentSlot.getSlotNumber() : "?",
            currentSession.getEntryTime(), exitTime, currentAmount, 0, method);

        Transaction t = new Transaction();
        t.setSessionId(currentSession.getId());
        t.setAmount(currentAmount);
        t.setDiscount(0);
        t.setPaymentTime(exitTime);
        t.setReceipt(receipt);
        t.setPaymentMethod(method);
        transactionDAO.save(t);

        if(receiptArea!=null) receiptArea.setText(receipt);
        if(receiptQR  !=null)
            receiptQR.setImage(QRCodeService.generateQR(
                "RECEIPT:TKT-"+currentSession.getId()+"|"
                    +currentVehicle.getLicensePlate()+"|Rs."+currentAmount,
                200, Color.web("#00FF88"), Color.web("#05080F")));

        if(successSub!=null)
            successSub.setText("Paid via "+method+"  ·  Rs. "+String.format("%.0f",currentAmount));
        show("step3success");
        playSuccessAnimation(method);
    }

    // ── GREEN CHECKMARK ANIMATION (no anonymous subclasses) ────────
    private void playSuccessAnimation(String method) {
        if(checkCanvas==null) return;
        GraphicsContext gc = checkCanvas.getGraphicsContext2D();
        double W=checkCanvas.getWidth(), H=checkCanvas.getHeight();
        double cx=W/2, cy=H/2, r=72;

        final double[] angle = {0};
        Timeline circleAnim = new Timeline(new KeyFrame(Duration.millis(12), e -> {
            gc.clearRect(0,0,W,H);
            angle[0] += 8;
            gc.setStroke(Color.web("#00FF88",0.12)); gc.setLineWidth(18);
            gc.strokeOval(cx-r-9,cy-r-9,(r+9)*2,(r+9)*2);
            gc.setStroke(Color.web("#00FF88",0.9)); gc.setLineWidth(5);
            gc.strokeArc(cx-r,cy-r,r*2,r*2, 90,-Math.min(360,angle[0]), ArcType.OPEN);
        }));
        circleAnim.setCycleCount(46);

        final double[] prog = {0};
        Timeline checkAnim = new Timeline(new KeyFrame(Duration.millis(14), e -> {
            prog[0] += 0.07;
            double t = Math.min(1.0, prog[0]);
            gc.clearRect(0,0,W,H);
            gc.setFill(Color.web("#00FF88",0.12)); gc.fillOval(cx-r-9,cy-r-9,(r+9)*2,(r+9)*2);
            gc.setStroke(Color.web("#00FF88",0.9)); gc.setLineWidth(5); gc.strokeOval(cx-r,cy-r,r*2,r*2);
            double x1=-22,y1=4,xm=-4,ym=22,x2=26,y2=-18;
            gc.setStroke(Color.web("#00FF88")); gc.setLineWidth(7);
            gc.setLineCap(StrokeLineCap.ROUND); gc.setLineJoin(StrokeLineJoin.ROUND);
            if(t<=0.5){
                double s=t/0.5;
                gc.strokeLine(cx+x1,cy+y1,cx+x1+(xm-x1)*s,cy+y1+(ym-y1)*s);
            } else {
                double s=(t-0.5)/0.5;
                gc.strokeLine(cx+x1,cy+y1,cx+xm,cy+ym);
                gc.strokeLine(cx+xm,cy+ym,cx+xm+(x2-xm)*s,cy+ym+(y2-ym)*s);
            }
        }));
        checkAnim.setCycleCount(17);

        circleAnim.setOnFinished(e -> checkAnim.play());

        checkAnim.setOnFinished(e -> {
            // Fade in title
            if(successTitle!=null){
                successTitle.setOpacity(0);
                FadeTransition ft1 = new FadeTransition(Duration.millis(350), successTitle);
                ft1.setToValue(1);
                ft1.play();
            }
            // Fade in sub
            if(successSub!=null){
                successSub.setOpacity(0);
                FadeTransition ft2 = new FadeTransition(Duration.millis(350), successSub);
                ft2.setDelay(Duration.millis(250));
                ft2.setToValue(1);
                ft2.play();
            }
            // 5-second countdown then go home
            final int[] secs = {5};
            Timeline homeTL = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
                secs[0]--;
                if(successSub!=null){
                    String base = successSub.getText().split("  Returning")[0];
                    successSub.setText(base+"  Returning in "+secs[0]+"s");
                }
            }));
            homeTL.setCycleCount(5);
            homeTL.setOnFinished(ev -> load("welcome.fxml"));
            homeTL.play();
        });

        circleAnim.play();
    }

    // ── COUNTDOWN FOR RECEIPT ──────────────────────────────────────
    private void startCd() {
        final int[] r = {20};
        cdTL = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            r[0]--;
            if(cdLabel!=null) cdLabel.setText("Going home in "+r[0]+"s");
            if(r[0]<=0){ cdTL.stop(); handleDone(); }
        }));
        cdTL.setCycleCount(20);
        cdTL.play();
    }

    // ── NAV ────────────────────────────────────────────────────────
    @FXML public void handleDone()          { stopTimers(); load("welcome.fxml"); }
    @FXML public void handleBack()          { stopTimers(); load("welcome.fxml"); }
    @FXML public void handleStep2Back()     { show("step1"); typed.setLength(0); updateDisplay(); if(exitStatus!=null)exitStatus.setText(""); }
    @FXML public void handleStep3Back()     { show("step2"); }
    @FXML public void handleStep3CashBack() { show("step3"); }
    @FXML public void handleStep3UPIBack()  { if(upiTimerTL!=null)upiTimerTL.stop(); show("step3"); }

    private void stopTimers(){
        if(cdTL!=null)       cdTL.stop();
        if(upiTimerTL!=null) upiTimerTL.stop();
    }

    private void show(String which){
        VBox[] all = {step1,step2,step3,step3cash,step3upi,step3pin,step3success,step4};
        VBox target = switch(which){
            case "step2"        -> step2;
            case "step3"        -> step3;
            case "step3cash"    -> step3cash;
            case "step3upi"     -> step3upi;
            case "step3pin"     -> step3pin;
            case "step3success" -> step3success;
            case "step4"        -> step4;
            default             -> step1;
        };
        for(VBox v : all) if(v!=null){ v.setVisible(v==target); v.setManaged(v==target); }
    }

    private void load(String f){ com.snappark.Main.switchScene(f); }
}