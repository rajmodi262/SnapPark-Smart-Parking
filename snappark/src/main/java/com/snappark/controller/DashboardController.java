package com.snappark.controller;

import com.snappark.service.AuthService;
import com.snappark.service.ParkingService;
import com.snappark.dao.SessionDAO;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {

    @FXML private Label     clockLabel;
    @FXML private Label     activeSessionsLabel;
    @FXML private Label     userLabel;
    @FXML private StackPane contentArea;
    @FXML private Button    btnDashboard;
    @FXML private Button    btnSlots;
    @FXML private Button    btnEntry;
    @FXML private Button    btnExit;
    @FXML private Button    btnAnalytics;

    private final AuthService   authService   = AuthService.getInstance();
    private final ParkingService parkingService = ParkingService.getInstance();
    private final SessionDAO    sessionDAO    = new SessionDAO();
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
    private Button activeBtn = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        var user = authService.getCurrentUser();
        if (user != null)
            userLabel.setText(user.getRole().name() + " - " + user.getUsername());

        startClock();
        loadView("home.fxml");
        setActive(btnDashboard);
    }

    // ── LIVE CLOCK ─────────────────────────────────────────
    private void startClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            clockLabel.setText(LocalTime.now().format(timeFmt));
            int active = sessionDAO.findAllActive().size();
            activeSessionsLabel.setText(active + " active session" + (active == 1 ? "" : "s"));
        }));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
        clockLabel.setText(LocalTime.now().format(timeFmt));
    }

    // ── SCREEN TRANSITIONS ─────────────────────────────────
    private void loadView(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/snappark/fxml/" + fxml));
            Pane view = loader.load();
            view.setOpacity(0);
            contentArea.getChildren().setAll(view);
            FadeTransition ft = new FadeTransition(Duration.millis(280), view);
            ft.setFromValue(0); ft.setToValue(1); ft.play();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── NAV ACTIVE STATE ────────────────────────────────────
    private void setActive(Button btn) {
        if (activeBtn != null) activeBtn.getStyleClass().remove("nav-btn-active-v2");
        activeBtn = btn;
        if (btn != null) btn.getStyleClass().add("nav-btn-active-v2");
    }

    @FXML public void showDashboard() { loadView("home.fxml");      setActive(btnDashboard); }
    @FXML public void showSlots()     { loadView("grid3d.fxml");     setActive(btnSlots);     }
    @FXML public void showEntry()     { loadView("entry.fxml");      setActive(btnEntry);     }
    @FXML public void showExit()      { loadView("exit.fxml");       setActive(btnExit);      }
    @FXML public void showAnalytics() { loadView("analytics.fxml");  setActive(btnAnalytics); }

    @FXML
    public void handleLogout() {
        authService.logout();
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/snappark/fxml/welcome.fxml"));
            Scene scene = new Scene(loader.load(), 900, 600);
            scene.getStylesheets().add(
                getClass().getResource("/com/snappark/css/dark-theme.css").toExternalForm());
            Stage stage = (Stage) clockLabel.getScene().getWindow();
            FadeTransition ft = new FadeTransition(Duration.millis(300),
                clockLabel.getScene().getRoot());
            ft.setToValue(0);
            ft.setOnFinished(e -> { com.snappark.Main.switchScene("welcome.fxml"); });
            ft.play();
        } catch (Exception e) { e.printStackTrace(); }
    }
}