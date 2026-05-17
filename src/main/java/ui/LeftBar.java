package ui;

import javax.swing.*;
import java.awt.*;

public class LeftBar extends JComponent {

    private long startTime = 0;
    private long duration = 1000; // ms
    private long pausedElapsed = 0;
    private boolean paused;
    private Timer timer;

    public LeftBar() {
        setOpaque(false); // wichtig für Transparenz
        setVisible(false);

        timer = new Timer(30, e -> repaint());
    }

    public void start(long durationMs) {
        this.duration = durationMs;
        this.startTime = System.currentTimeMillis();
        this.pausedElapsed = 0;
        this.paused = false;
        setVisible(true);
        timer.start();
    }

    public void pause() {
        if (!isVisible() || paused) return;

        pausedElapsed = Math.max(0, System.currentTimeMillis() - startTime);
        paused = true;
        timer.stop();
        repaint();
    }

    public void resume(long remainingMillis) {
        if (!paused) {
            start(remainingMillis);
            return;
        }

        long safeRemaining = Math.max(1, remainingMillis);
        duration = pausedElapsed + safeRemaining;
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
        float progress = Math.min(1f, elapsed / (float) duration);
        int yStart = (int) (progress * getHeight());
        int height = getHeight() - yStart;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(255, 105, 180, 200));
        g2.fillRect(0, yStart, 10, height);
        g2.dispose();

        if (progress >= 1f) stop();
    }
}
