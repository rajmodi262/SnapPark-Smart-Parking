package com.snappark.service;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class QRCodeService {

    private static QRCodeService instance;

    private QRCodeService() {}

    public static QRCodeService getInstance() {
        if (instance == null) instance = new QRCodeService();
        return instance;
    }

    public static Image generateQR(String data, int size, Color dark, Color light) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            BitMatrix matrix = new MultiFormatWriter()
                .encode(data, BarcodeFormat.QR_CODE, size, size, hints);
            WritableImage img = new WritableImage(size, size);
            PixelWriter pw = img.getPixelWriter();
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    pw.setColor(x, y, matrix.get(x, y) ? dark : light);
            return img;
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    public static Image generateQR(String data, int size) {
        return generateQR(data, size, Color.web("#00FF88"), Color.web("#0A0F1A"));
    }

    /**
     * Generate QR code as PNG byte array for kiosk display.
     * Uses HIGH CONTRAST black-on-white for reliable phone camera scanning.
     */
    public byte[] generateQRBytes(String data, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // High error correction
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = new MultiFormatWriter()
                .encode(data, BarcodeFormat.QR_CODE, size, size, hints);

            // BLACK on WHITE — the ONLY color scheme phones reliably scan
            BufferedImage bImg = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            int darkRgb  = java.awt.Color.BLACK.getRGB();
            int lightRgb = java.awt.Color.WHITE.getRGB();
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    bImg.setRGB(x, y, matrix.get(x, y) ? darkRgb : lightRgb);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bImg, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String buildTicketData(int sessionId, String plate, String slot, String time) {
        return "SnapPark:TKT-" + sessionId + "|" + plate + "|" + slot + "|" + time;
    }
}