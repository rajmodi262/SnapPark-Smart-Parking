package com.snappark.service;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;

/**
 * Sound effects service for kiosk — generates tones programmatically.
 * No external sound files needed.
 */
public class SoundService {
    private static SoundService instance;
    private boolean enabled = true;

    private SoundService() {}

    public static synchronized SoundService getInstance() {
        if (instance == null) instance = new SoundService();
        return instance;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Short beep — used when QR scanned or button pressed */
    public void beep() {
        playTone(880, 120, 0.5);
    }

    /** Success chime — used when PIN verified / check-in confirmed */
    public void chime() {
        new Thread(() -> {
            playTone(523, 120, 0.4);  // C5
            sleep(100);
            playTone(659, 120, 0.4);  // E5
            sleep(100);
            playTone(784, 200, 0.5);  // G5
        }).start();
    }

    /** Error alert — used when invalid PIN / error */
    public void alert() {
        new Thread(() -> {
            playTone(330, 200, 0.6);  // E4
            sleep(80);
            playTone(262, 300, 0.6);  // C4
        }).start();
    }

    /** Barrier open sound — ascending sweep */
    public void barrierOpen() {
        new Thread(() -> {
            for (int freq = 400; freq <= 900; freq += 50) {
                playTone(freq, 40, 0.35);
                sleep(20);
            }
            playTone(1046, 250, 0.5); // High C
        }).start();
    }

    /** Payment success — pleasant confirmation */
    public void paymentSuccess() {
        new Thread(() -> {
            playTone(587, 100, 0.4);  // D5
            sleep(80);
            playTone(740, 100, 0.4);  // F#5
            sleep(80);
            playTone(880, 150, 0.45); // A5
            sleep(100);
            playTone(1175, 250, 0.5); // D6
        }).start();
    }

    // ─── Core tone generation ───────────────────────────────────
    private void playTone(int frequency, int durationMs, double volume) {
        if (!enabled) return;
        try {
            float sampleRate = 44100;
            int numSamples = (int) (sampleRate * durationMs / 1000);
            byte[] buffer = new byte[numSamples * 2]; // 16-bit mono

            // Fade in/out to avoid clicks
            int fadeLen = Math.min(numSamples / 10, 400);

            for (int i = 0; i < numSamples; i++) {
                double angle = 2.0 * Math.PI * frequency * i / sampleRate;
                // Sine wave + slight harmonic richness
                double sample = Math.sin(angle) * 0.8 + Math.sin(angle * 2) * 0.15 + Math.sin(angle * 3) * 0.05;
                sample *= volume;

                // Apply fade envelope
                if (i < fadeLen) {
                    sample *= (double) i / fadeLen;
                } else if (i > numSamples - fadeLen) {
                    sample *= (double) (numSamples - i) / fadeLen;
                }

                short s = (short) (sample * Short.MAX_VALUE);
                buffer[i * 2]     = (byte) (s & 0xFF);
                buffer[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
            }

            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) return;

            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            line.write(buffer, 0, buffer.length);
            line.drain();
            line.close();
        } catch (Exception e) {
            // Silently fail — sound is a nice-to-have, not critical
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
