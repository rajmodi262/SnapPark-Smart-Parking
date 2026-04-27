package com.snappark.controller;

import com.snappark.service.AnalyticsService;
import com.snappark.service.PdfReportService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class AnalyticsController implements Initializable {

    @FXML private Label todayRevenue;
    @FXML private Label totalRevenue;
    @FXML private Label totalVehicles;
    @FXML private Label avgRevenue;
    @FXML private BarChart<String, Number> revenueByTypeChart;
    @FXML private PieChart utilizationChart;

    // Report controls (optional — gracefully handled if not in FXML)
    @FXML private ComboBox<String> reportMonth;
    @FXML private ComboBox<String> reportYear;
    @FXML private Label reportStatus;

    private final AnalyticsService analytics = AnalyticsService.getInstance();
    private final PdfReportService pdfService = PdfReportService.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadStats();
        loadBarChart();
        loadPieChart();
        initReportControls();
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

    // ── REPORT CONTROLS ──────────────────────────────────────────
    private void initReportControls() {
        if (reportMonth == null || reportYear == null) return; // FXML not updated yet

        // Populate months
        for (int m = 1; m <= 12; m++) {
            reportMonth.getItems().add(
                Month.of(m).getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }
        reportMonth.getSelectionModel().select(LocalDate.now().getMonthValue() - 1);

        // Populate years (last 3 + current)
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 2; y <= currentYear; y++) {
            reportYear.getItems().add(String.valueOf(y));
        }
        reportYear.getSelectionModel().selectLast();
    }

    @FXML
    public void handleDownloadReport() {
        if (reportMonth == null || reportYear == null) return;
        int month = reportMonth.getSelectionModel().getSelectedIndex() + 1;
        int year;
        try {
            year = Integer.parseInt(reportYear.getSelectionModel().getSelectedItem());
        } catch (Exception e) {
            if (reportStatus != null) reportStatus.setText("Select a valid year");
            return;
        }
        if (month < 1 || month > 12) {
            if (reportStatus != null) reportStatus.setText("Select a valid month");
            return;
        }

        if (reportStatus != null) reportStatus.setText("Generating PDF...");

        // Run in background thread to avoid blocking UI
        new Thread(() -> {
            byte[] pdf = pdfService.generateMonthlyFrequencyReport(year, month);
            Platform.runLater(() -> {
                if (pdf == null) {
                    if (reportStatus != null) reportStatus.setText("Failed to generate report!");
                    return;
                }

                // Show save dialog
                FileChooser fc = new FileChooser();
                fc.setTitle("Save Frequency Report");
                fc.setInitialFileName(String.format("vehicle-frequency-report-%04d-%02d.pdf", year, month));
                fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
                Stage stage = (Stage) reportMonth.getScene().getWindow();
                File file = fc.showSaveDialog(stage);
                if (file != null) {
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(pdf);
                        if (reportStatus != null)
                            reportStatus.setText("Saved: " + file.getName());
                    } catch (Exception e) {
                        if (reportStatus != null)
                            reportStatus.setText("Error saving: " + e.getMessage());
                    }
                } else {
                    if (reportStatus != null) reportStatus.setText("Cancelled");
                }
            });
        }).start();
    }
}