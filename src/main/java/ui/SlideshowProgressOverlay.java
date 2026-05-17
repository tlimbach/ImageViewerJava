package ui;

import javax.swing.*;
import java.awt.*;

public class SlideshowProgressOverlay extends JComponent {

    private long startTime;
    private long durationMs = 1;
    private long pausedElapsed;
    private boolean paused;
    private final Timer timer;

    public SlideshowProgressOverlay() {
        setOpaque(false);
        setVisible(false);
        timer = new Timer(250, e -> repaint());
    }

    public void start(long durationMs) {
        this.durationMs = Math.max(1, durationMs);
        this.startTime = System.currentTimeMillis();
        this.pausedElapsed = 0;
        this.paused = false;
        setVisible(true);
        timer.start();
        repaint();
    }

    public void pause() {
        if (!isVisible() || paused) return;

        pausedElapsed = Math.max(0, System.currentTimeMillis() - startTime);
        paused = true;
        timer.stop();
        repaint();
    }

    public void resume(long remainingMs) {
        if (!paused) {
            start(remainingMs);
            return;
        }

        long safeRemaining = Math.max(1, remainingMs);
        durationMs = pausedElapsed + safeRemaining;
        startTime = System.currentTimeMillis() - pausedElapsed;
        paused = false;
        setVisible(true);
        timer.start();
        repaint();
    }

    public void stop() {
        timer.stop();
        paused = false;
        pausedElapsed = 0;
        setVisible(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isVisible()) return;

        long elapsed = paused ? pausedElapsed : System.currentTimeMillis() - startTime;
        float progress = Math.min(1f, elapsed / (float) durationMs);
        int yStart = (int) (progress * getHeight());
        int height = getHeight() - yStart;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(255, 105, 180, 200));
        g2.fillRect(0, yStart, getWidth(), height);
        g2.dispose();

        if (progress >= 1f) stop();
    }
}
