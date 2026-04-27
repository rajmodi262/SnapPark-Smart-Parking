package com.snappark.service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;

public class SMSService {

    private static SMSService instance;
    private SMSService() {}
    public static synchronized SMSService getInstance() {
        if (instance == null) instance = new SMSService();
        return instance;
    }

    // ── TWILIO CONFIG ──────────────────────────────────────────────
    // Fill these in to enable real SMS.
    // Leave empty for DEMO MODE (PIN shown on screen).
    private static final String ACCOUNT_SID = "";   // e.g. "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    private static final String AUTH_TOKEN   = "";   // e.g. "your_auth_token"
    private static final String FROM_NUMBER  = "";   // e.g. "+12025551234"

    // ── GENERATE PIN ───────────────────────────────────────────────
    public String generatePIN() {
        // 4-digit PIN, never starts with 0
        int pin = 1000 + new Random().nextInt(9000);
        return String.valueOf(pin);
    }

    // ── SEND SMS ───────────────────────────────────────────────────
    // Returns true if sent (or demo mode). False only on real send failure.
    public boolean sendPIN(String phoneNumber, String pin, int ticketId) {
        String message = "SnapPark: Your payment PIN for TKT-"
            + String.format("%05d", ticketId)
            + " is " + pin
            + ". Do not share this PIN with anyone.";

        System.out.println("=== SMS PIN ===");
        System.out.println("To: " + phoneNumber);
        System.out.println("Message: " + message);
        System.out.println("==============");

        if (ACCOUNT_SID.isBlank() || AUTH_TOKEN.isBlank() || FROM_NUMBER.isBlank()) {
            System.out.println("DEMO MODE: SMS not sent. PIN = " + pin);
            return true; // demo mode — always succeeds
        }

        return sendViaTwilio(phoneNumber, message);
    }

    private boolean sendViaTwilio(String toNumber, String message) {
        try {
            String urlStr = "https://api.twilio.com/2010-04-01/Accounts/"
                + ACCOUNT_SID + "/Messages.json";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            // Basic Auth
            String auth = ACCOUNT_SID + ":" + AUTH_TOKEN;
            String encoded = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + encoded);
            conn.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded");

            // Ensure +91 prefix for Indian numbers
            String to = toNumber.startsWith("+") ? toNumber : "+91" + toNumber;

            String body = "To=" + java.net.URLEncoder.encode(to, StandardCharsets.UTF_8)
                + "&From=" + java.net.URLEncoder.encode(FROM_NUMBER, StandardCharsets.UTF_8)
                + "&Body=" + java.net.URLEncoder.encode(message, StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            System.out.println("Twilio response: " + code);
            return code == 200 || code == 201;
        } catch (Exception e) {
            System.err.println("SMS send failed: " + e.getMessage());
            return false;
        }
    }

    public boolean isDemoMode() {
        return ACCOUNT_SID.isBlank() || AUTH_TOKEN.isBlank() || FROM_NUMBER.isBlank();
    }

    // Mask phone for display: 9876543210 -> +91 98765 **210
    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return phone;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
            return "+91 " + digits.substring(0,5) + " **" + digits.substring(digits.length()-3);
        }
        return "+91 **" + digits.substring(Math.max(0, digits.length()-3));
    }
}