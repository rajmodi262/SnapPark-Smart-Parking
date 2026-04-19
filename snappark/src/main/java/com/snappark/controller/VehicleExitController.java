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
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class VehicleExitController implements Initializable {

    @FXML private TableView<ParkingSession> parkedTable;
    @FXML private TableColumn<ParkingSession, String> colPlate;
    @FXML private TableColumn<ParkingSession, String> colOwner;
    @FXML private TableColumn<ParkingSession, String> colSlot;
    @FXML private TableColumn<ParkingSession, String> colDuration;
    @FXML private VBox billPane;
    @FXML private Label billPlate, billOwner, billSlot, billEntry, billDuration, billTotal;
    @FXML private TextField discountField;
    @FXML private Label exitErrorLabel;
    @FXML private TextArea receiptArea;

    private final VehicleDAO vehicleDAO = new VehicleDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final BillingService billingService = BillingService.getInstance();
    private final ParkingService parkingService = ParkingService.getInstance();
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private Vehicle currentVehicle;
    private ParkingSession currentSession;
    private ParkingSlot currentSlot;
    private double currentAmount;
    private List<Vehicle> allVehicles;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        allVehicles = vehicleDAO.findAll();
        setupTable();
        loadParkedVehicles();
        parkedTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) showBill(newVal);
        });
    }

    private void setupTable() {
        colPlate.setCellValueFactory(c -> {
            Vehicle v = getVehicle(c.getValue().getVehicleId());
            return new SimpleStringProperty(v != null ? v.getLicensePlate() : "?");
        });
        colOwner.setCellValueFactory(c -> {
            Vehicle v = getVehicle(c.getValue().getVehicleId());
            return new SimpleStringProperty(v != null ? v.getOwnerName() : "?");
        });
        colSlot.setCellValueFactory(c -> {
            ParkingSlot s = getSlot(c.getValue().getSlotId());
            return new SimpleStringProperty(s != null ? s.getSlotNumber() : "?");
        });
        colDuration.setCellValueFactory(c -> {
            Duration d = Duration.between(c.getValue().getEntryTime(), LocalDateTime.now());
            return new SimpleStringProperty(d.toHours() + "h " + d.toMinutesPart() + "m");
        });
    }

    private void loadParkedVehicles() {
        List<ParkingSession> active = sessionDAO.findAllActive();
        parkedTable.setItems(FXCollections.observableArrayList(active));
    }

    private void showBill(ParkingSession session) {
        currentSession = session;
        currentVehicle = getVehicle(session.getVehicleId());
        currentSlot = getSlot(session.getSlotId());
        if (currentVehicle == null) return;
        LocalDateTime now = LocalDateTime.now();
        currentAmount = billingService.calculateFee(currentVehicle.getVehicleType(), session.getEntryTime(), now);
        Duration duration = Duration.between(session.getEntryTime(), now);
        billPlate.setText(currentVehicle.getLicensePlate());
        billOwner.setText(currentVehicle.getOwnerName());
        billSlot.setText(currentSlot != null ? currentSlot.getSlotNumber() : "?");
        billEntry.setText(session.getEntryTime().format(fmt));
        billDuration.setText(duration.toHours() + "h " + duration.toMinutesPart() + "m");
        billTotal.setText("Rs. " + currentAmount);
        discountField.clear();
        billPane.setVisible(true);
        billPane.setManaged(true);
    }

    @FXML
    public void handleExit() {
        if (currentSession == null) return;
        double discount = 0;
        try { discount = Double.parseDouble(discountField.getText().trim()); } catch (Exception ignored) {}
        LocalDateTime exitTime = LocalDateTime.now();
        double finalAmount = billingService.applyDiscount(currentAmount, discount);
        sessionDAO.endSession(currentSession.getId(), exitTime);
        parkingService.releaseSlot(currentSession.getSlotId());
        Transaction t = new Transaction();
        t.setSessionId(currentSession.getId());
        t.setAmount(currentAmount);
        t.setDiscount(discount);
        t.setPaymentTime(exitTime);
        String receipt = billingService.generateReceipt(
            currentVehicle.getLicensePlate(),
            currentSlot != null ? currentSlot.getSlotNumber() : "?",
            currentSession.getEntryTime(), exitTime, currentAmount, discount);
        t.setReceipt(receipt);
        transactionDAO.save(t);
        receiptArea.setText(receipt);
        exitErrorLabel.setText("Exit confirmed! Amount collected: Rs. " + finalAmount);
        billPane.setVisible(false);
        billPane.setManaged(false);
        currentSession = null;
        currentVehicle = null;
        loadParkedVehicles();
    }

    private Vehicle getVehicle(int id) {
        return allVehicles.stream().filter(v -> v.getId() == id).findFirst().orElse(null);
    }

    private ParkingSlot getSlot(int id) {
        return parkingService.getAllSlots().stream().filter(s -> s.getId() == id).findFirst().orElse(null);
    }
}