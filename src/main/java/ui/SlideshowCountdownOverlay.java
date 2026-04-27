package ui;

import javax.swing.*;
import java.awt.*;

public class SlideshowCountdownOverlay extends JComponent {

    private long slideshowEndTime;
    private final Timer timer;

    public SlideshowCountdownOverlay() {
        setOpaque(false);
        setVisible(false);
        timer = new Timer(200, e -> repaint());
    }

    public void start(long durationMs) {
        slideshowEndTime = System.currentTimeMillis() + Math.max(1, durationMs);
        setVisible(true);
        timer.start();
        repaint();
    }

    public void stop() {
        timer.stop();
        setVisible(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isVisible()) return;

        long remainingMillis = slideshowEndTime - System.currentTimeMillis();
        if (remainingMillis > 60_000) return;
        if (remainingMillis <= 0) {
            stop();
            return;
        }

        long seconds = Math.max(0, (remainingMillis + 999) / 1000);
        String text = String.valueOf(seconds);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int boxWidth = Math.min(220, Math.max(120, getWidth() / 5));
        int boxHeight = Math.min(130, Math.max(90, getHeight() / 7));
        int x = getWidth() - boxWidth - 28;
        int y = getHeight() - boxHeight - 28;

        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, y, boxWidth, boxHeight, 12, 12);

        g2.setFont(getFont().deriveFont(Font.BOLD, Math.min(72f, boxHeight * 0.55f)));
        FontMetrics fm = g2.getFontMetrics();
        int textX = x + (boxWidth - fm.stringWidth(text)) / 2;
        int textY = y + (boxHeight - fm.getHeight()) / 2 + fm.getAscent();

        g2.setColor(new Color(255, 255, 255, 235));
        g2.drawString(text, textX, textY);
        g2.dispose();
    }
}
