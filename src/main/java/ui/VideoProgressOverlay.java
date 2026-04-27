package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.DoubleConsumer;

public class VideoProgressOverlay extends JComponent {

    private double progress;
    private long currentMillis;
    private long totalMillis;
    private DoubleConsumer seekHandler;
    private boolean videoActive;
    private boolean mouseOverVideo;

    public VideoProgressOverlay() {
        setOpaque(false);
        setVisible(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                seek(e);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setMouseOverVideo(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setMouseOverVideo(false);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                seek(e);
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    public void setSeekHandler(DoubleConsumer seekHandler) {
        this.seekHandler = seekHandler;
    }

    public void setVideoActive(boolean videoActive) {
        this.videoActive = videoActive;
        updateVisibility();
    }

    public void setMouseOverVideo(boolean mouseOverVideo) {
        this.mouseOverVideo = mouseOverVideo;
        updateVisibility();
    }

    public void setProgress(long currentMillis, long totalMillis) {
        this.currentMillis = Math.max(0, currentMillis);
        this.totalMillis = Math.max(0, totalMillis);
        this.progress = this.totalMillis > 0
                ? Math.max(0.0, Math.min(1.0, this.currentMillis / (double) this.totalMillis))
                : 0.0;
        repaint();
    }

    private void updateVisibility() {
        setVisible(videoActive && mouseOverVideo);
    }

    private void seek(MouseEvent e) {
        if (seekHandler == null || getWidth() <= 0) return;

        double value = Math.max(0.0, Math.min(1.0, e.getX() / (double) getWidth()));
        progress = value;
        seekHandler.accept(value);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isVisible()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int barHeight = 7;
        int y = getHeight() - barHeight - 8;
        int filledWidth = (int) Math.round(getWidth() * progress);

        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRoundRect(0, getHeight() - 32, getWidth(), 32, 8, 8);

        g2.setColor(new Color(220, 220, 220, 180));
        g2.fillRoundRect(10, y, getWidth() - 20, barHeight, barHeight, barHeight);

        g2.setColor(new Color(255, 255, 255, 230));
        int innerWidth = Math.max(0, getWidth() - 20);
        g2.fillRoundRect(10, y, (int) Math.round(innerWidth * progress), barHeight, barHeight, barHeight);

        int handleX = 10 + Math.max(0, Math.min(innerWidth, filledWidth - 10));
        g2.setColor(new Color(255, 255, 255, 240));
        g2.fillOval(handleX - 5, y - 5, 17, 17);

        if (totalMillis > 0) {
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g2.setColor(new Color(255, 255, 255, 230));
            g2.drawString(formatTime(currentMillis) + " / " + formatTime(totalMillis), 12, getHeight() - 14);
        }

        g2.dispose();
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
