package com.snappark.controller;

import com.snappark.service.AnalyticsService;
import com.snappark.service.AuthService;
import com.snappark.service.ParkingService;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.util.Duration;
import java.net.URL;
import java.util.ResourceBundle;

public class HomeController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Label availableCount;
    @FXML private Label occupiedCount;
    @FXML private Label totalCount;
    @FXML private Label revenueCount;

    private final AuthService      authService      = AuthService.getInstance();
    private final ParkingService   parkingService   = ParkingService.getInstance();
    private final AnalyticsService analyticsService = AnalyticsService.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        var user = authService.getCurrentUser();
        if (user != null)
            welcomeLabel.setText("Welcome back, " + user.getName() + "!");

        long avail   = parkingService.getAvailableCount();
        long occ     = parkingService.getOccupiedCount();
        long total   = parkingService.getAllSlots().size();
        double rev   = analyticsService.getTodayRevenue();

        animateCount(availableCount, (int) avail,  false);
        animateCount(occupiedCount,  (int) occ,    false);
        animateCount(totalCount,     (int) total,   false);
        animateRevenue(revenueCount, rev);
    }

    private void animateCount(Label label, int target, boolean addPlus) {
        final int[] current = {0};
        int steps = 28;
        double delay = 600.0 / steps;
        Timeline tl = new Timeline();
        for (int i = 1; i <= steps; i++) {
            final int val = (int) Math.round(target * (double) i / steps);
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(delay * i),
                e -> label.setText(addPlus && val > 0 ? "+" + val : String.valueOf(val))));
        }
        tl.play();
    }

    private void animateRevenue(Label label, double target) {
        final double[] current = {0};
        int steps = 28;
        double delay = 700.0 / steps;
        Timeline tl = new Timeline();
        for (int i = 1; i <= steps; i++) {
            final double val = target * (double) i / steps;
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(delay * i),
                e -> label.setText("Rs." + (int) val)));
        }
        tl.play();
    }
}