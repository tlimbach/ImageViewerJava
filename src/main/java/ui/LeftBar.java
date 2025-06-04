package ui;

import javax.swing.*;
import java.awt.*;

public class LeftBar extends JComponent {

    private long startTime = 0;
    private long duration = 1000; // ms
    private Timer timer;

    public LeftBar() {
        setOpaque(false); // wichtig für Transparenz
        setVisible(false);

        timer = new Timer(30, e -> repaint());
    }

    public void start(long durationMs) {
        this.duration = durationMs;
        this.startTime = System.currentTimeMillis();
        setVisible(true);
        timer.start();
    }

    public void stop() {
        timer.stop();
        setVisible(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isVisible()) return;

        long now = System.currentTimeMillis();
        float progress = Math.min(1f, (now - startTime) / (float) duration);
        int yStart = (int) (progress * getHeight());
        int height = getHeight() - yStart;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(255, 105, 180, 200));
        g2.fillRect(0, yStart, 10, height);
        g2.dispose();

        if (progress >= 1f) stop();
    }
}