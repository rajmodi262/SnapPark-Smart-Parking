package com.snappark.controller;

import com.snappark.service.AnalyticsService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;

public class AnalyticsController implements Initializable {

    @FXML private Label todayRevenue;
    @FXML private Label totalRevenue;
    @FXML private Label totalVehicles;
    @FXML private Label avgRevenue;
    @FXML private BarChart<String, Number> revenueByTypeChart;
    @FXML private PieChart utilizationChart;

    private final AnalyticsService analytics = AnalyticsService.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadStats();
        loadBarChart();
        loadPieChart();
    }

    private void loadStats() {
        double today = analytics.getTodayRevenue();
        double total = analytics.getTotalRevenue();
        int vehicles = analytics.getTotalVehicles();
        double avg = vehicles > 0 ? total / vehicles : 0;
        todayRevenue.setText("Rs. " + String.format("%.0f", today));
        totalRevenue.setText("Rs. " + String.format("%.0f", total));
        totalVehicles.setText(String.valueOf(vehicles));
        avgRevenue.setText("Rs. " + String.format("%.0f", avg));
    }

    private void loadBarChart() {
        revenueByTypeChart.getData().clear();
        revenueByTypeChart.setLegendVisible(false);
        revenueByTypeChart.setAnimated(true);
        Map<String, Double> data = analytics.getRevenueByVehicleType();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Revenue");
        series.getData().add(new XYChart.Data<>("BIKE", data.getOrDefault("BIKE", 0.0)));
        series.getData().add(new XYChart.Data<>("CAR", data.getOrDefault("CAR", 0.0)));
        series.getData().add(new XYChart.Data<>("SUV", data.getOrDefault("SUV", 0.0)));
        revenueByTypeChart.getData().add(series);
        Platform.runLater(() -> {
            revenueByTypeChart.lookupAll(".chart-bar").forEach(n -> {
                String style = n.getStyleClass().toString();
                if (style.contains("data0")) n.setStyle("-fx-bar-fill: #06b6d4;");
                else if (style.contains("data1")) n.setStyle("-fx-bar-fill: #3b82f6;");
                else if (style.contains("data2")) n.setStyle("-fx-bar-fill: #a855f7;");
            });
        });
    }

    private void loadPieChart() {
        utilizationChart.getData().clear();
        utilizationChart.setAnimated(true);
        Map<String, Integer> data = analytics.getSlotUtilization();
        String[] colors = {"#22c55e", "#f43f5e", "#f59e0b", "#00c2ff"};
        int i = 0;
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            if (entry.getValue() > 0) {
                PieChart.Data slice = new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue());
                utilizationChart.getData().add(slice);
            }
        }
        Platform.runLater(() -> {
            int idx = 0;
            for (PieChart.Data d : utilizationChart.getData()) {
                d.getNode().setStyle("-fx-pie-color: " + colors[idx % colors.length] + ";");
                idx++;
            }
        });
    }
}