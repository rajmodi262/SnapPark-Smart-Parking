package com.snappark.controller;

import com.snappark.dao.SessionDAO;
import com.snappark.dao.VehicleDAO;
import com.snappark.model.ParkingSession;
import com.snappark.model.ParkingSlot;
import com.snappark.model.Vehicle;
import com.snappark.model.enums.SlotStatus;
import com.snappark.model.enums.VehicleType;
import com.snappark.service.AuthService;
import com.snappark.service.ParkingService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class VehicleEntryController implements Initializable {

    @FXML private TextField licensePlateField;
    @FXML private TextField ownerNameField;
    @FXML private ComboBox<String> vehicleTypeBox;
    @FXML private ComboBox<String> slotBox;
    @FXML private Label entryErrorLabel;
    @FXML private TableView<ParkingSession> entriesTable;
    @FXML private TableColumn<ParkingSession, String> colPlate;
    @FXML private TableColumn<ParkingSession, String> colOwner;
    @FXML private TableColumn<ParkingSession, String> colSlot;
    @FXML private TableColumn<ParkingSession, String> colType;
    @FXML private TableColumn<ParkingSession, String> colTime;

    private final ParkingService parkingService = ParkingService.getInstance();
    private final AuthService authService = AuthService.getInstance();
    private final VehicleDAO vehicleDAO = new VehicleDAO();
    private List<Vehicle> allVehicles;
    private final SessionDAO sessionDAO = new SessionDAO();
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        vehicleTypeBox.setItems(FXCollections.observableArrayList("BIKE", "CAR", "SUV"));
        vehicleTypeBox.getSelectionModel().selectFirst();
        allVehicles = vehicleDAO.findAll();
        loadAvailableSlots();
        setupTable();
        refreshTable();
    }

    private void loadAvailableSlots() {
        String selectedType = vehicleTypeBox.getValue();
        List<String> available = parkingService.getAllSlots().stream()
            .filter(s -> s.getStatus() == SlotStatus.AVAILABLE)
            .filter(s -> {
                if (selectedType == null) return true;
                if (selectedType.equals("BIKE")) return s.getType().name().equals("BIKE");
                return !s.getType().name().equals("BIKE"); // CAR or SUV for 4-wheeler
            })
            .map(s -> s.getId() + " - " + s.getSlotNumber() + " (" + s.getType() + ")")
            .collect(Collectors.toList());
        slotBox.setItems(FXCollections.observableArrayList(available));
        if (!available.isEmpty()) slotBox.getSelectionModel().selectFirst();
        else slotBox.setPromptText("No slots available for " + selectedType);
    }

    private void setupTable() {
        colPlate.setCellValueFactory(c -> {
            Vehicle v = allVehicles.stream().filter(x -> x.getId() == c.getValue().getVehicleId()).findFirst().orElse(null);
            return new SimpleStringProperty(v != null ? v.getLicensePlate() : "?");
        });
        colOwner.setCellValueFactory(c -> {
            Vehicle v = allVehicles.stream().filter(x -> x.getId() == c.getValue().getVehicleId()).findFirst().orElse(null);
            return new SimpleStringProperty(v != null ? v.getOwnerName() : "?");
        });
        colSlot.setCellValueFactory(c -> {
            ParkingSlot s = parkingService.getAllSlots().stream().filter(x -> x.getId() == c.getValue().getSlotId()).findFirst().orElse(null);
            return new SimpleStringProperty(s != null ? s.getSlotNumber() : "?");
        });
        colType.setCellValueFactory(c -> {
            Vehicle v = allVehicles.stream().filter(x -> x.getId() == c.getValue().getVehicleId()).findFirst().orElse(null);
            return new SimpleStringProperty(v != null ? v.getVehicleType().name() : "?");
        });
        colTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEntryTime().format(fmt)));
        entriesTable.setStyle("-fx-background-color: #111621; -fx-text-fill: white;");
    }

    private void refreshTable() {
        allVehicles = vehicleDAO.findAll();
        ObservableList<ParkingSession> sessions = FXCollections.observableArrayList(sessionDAO.findAllActive());
        entriesTable.setItems(sessions);
    }

    @FXML
    public void handleEntry() {
        String plate = licensePlateField.getText().trim().toUpperCase();
        String owner = ownerNameField.getText().trim();
        String typeStr = vehicleTypeBox.getValue();
        String slotStr = slotBox.getValue();

        if (plate.isEmpty() || owner.isEmpty() || typeStr == null || slotStr == null) {
            entryErrorLabel.setText("Please fill all fields!");
            return;
        }

        int slotId = Integer.parseInt(slotStr.split(" - ")[0]);
        var user = authService.getCurrentUser();

        boolean locked = parkingService.lockSlot(slotId, user.getId());
        if (!locked) {
            entryErrorLabel.setText("Slot already taken! Choose another.");
            allVehicles = vehicleDAO.findAll();
        loadAvailableSlots();
            return;
        }

        Vehicle vehicle = new Vehicle(0, plate, owner, VehicleType.valueOf(typeStr));
        vehicle = vehicleDAO.save(vehicle);

        parkingService.confirmBooking(slotId, user.getId());
        ParkingSession session = sessionDAO.startSession(vehicle.getId(), slotId, user.getId());

        if (session != null) {
            entryErrorLabel.setStyle("-fx-text-fill: #4ade80; -fx-font-size: 12px;");
            entryErrorLabel.setText("Entry confirmed! Ticket: #" + session.getId());
            licensePlateField.clear();
            ownerNameField.clear();
            allVehicles = vehicleDAO.findAll();
        loadAvailableSlots();
            refreshTable();
        } else {
            entryErrorLabel.setText("Error creating session!");
        }
    }
}