package com.snappark;
import com.snappark.concurrent.LockSweeper;
import com.snappark.dao.DatabaseManager;
import com.snappark.service.WebServer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
public class Main extends Application {
    public static Stage mainStage;
    public static double W, H;
    public static void switchScene(String fxml) {
        try {
            FXMLLoader l = new FXMLLoader(Main.class.getResource("/com/snappark/fxml/" + fxml));
            Scene sc = new Scene(l.load(), W, H);
            sc.getStylesheets().add(Main.class.getResource("/com/snappark/css/dark-theme.css").toExternalForm());
            mainStage.setScene(sc);
        } catch (Exception e) { e.printStackTrace(); }
    }
    @Override
    public void start(Stage stage) throws Exception {
        mainStage = stage;
        javafx.geometry.Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        W = bounds.getWidth();
        H = bounds.getHeight();
        DatabaseManager.getInstance();
        LockSweeper.getInstance().start();

        // Start embedded web server for mobile QR check-in/check-out
        WebServer ws = WebServer.getInstance();
        ws.start();

        // If launched with ngrok public URL, use that for QR codes
        String publicUrl = System.getProperty("snappark.publicUrl");
        if (publicUrl != null && !publicUrl.isBlank() && !publicUrl.contains("${")) {
            ws.setPublicUrl(publicUrl.trim());
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/snappark/fxml/welcome.fxml"));
        Scene scene = new Scene(loader.load(), W, H);
        scene.getStylesheets().add(getClass().getResource("/com/snappark/css/dark-theme.css").toExternalForm());
        stage.setTitle("SnapPark");
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(W);
        stage.setHeight(H);
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }
    @Override
    public void stop() {
        LockSweeper.getInstance().stop();
        WebServer.getInstance().stop();
    }
    public static void main(String[] args) { launch(args); }
}