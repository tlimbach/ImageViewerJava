package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class AnimatedImagePanel extends JPanel {
    // ------------------ STELLSCHRAUBEN ------------------
    private static final double BASE_SCALE_MULTIPLIER = 2.0;     // Basisvergrößerung über die Zielgröße hinaus
    private static final double MAX_ZOOM_VARIATION = 0.15;       // Zoomschwankung (z. B. 0.15 = ±15%)
    private static final double ZOOM_SPEED = 0.01;              // Geschwindigkeit des Zooms
    private static final double PAN_SPEED_X = 0.002;             // Geschwindigkeit horizontales Schwenken
    private static final double PAN_SPEED_Y = 0.002;             // Geschwindigkeit vertikales Schwenken

    // ------------------ INSTANZVARIABLEN ------------------
    private final BufferedImage image;
    private final Timer animationTimer;
    private final int baseWidth;
    private final int baseHeight;
    private final double baseScale;

    private double zoomPhase = 0;
    private double panPhaseX = 0;
    private double panPhaseY = 1.7;

    public AnimatedImagePanel(Image image, int newWidth, int newHeight) {
        this.image = toBufferedImage(image);
        this.baseWidth = newWidth;
        this.baseHeight = newHeight;

        double scaleX = (double) newWidth / image.getWidth(null);
        double scaleY = (double) newHeight / image.getHeight(null);
        this.baseScale = Math.min(scaleX, scaleY) * BASE_SCALE_MULTIPLIER;

        setDoubleBuffered(true);

        animationTimer = new Timer(15, e -> {
            zoomPhase += ZOOM_SPEED;
            panPhaseX += PAN_SPEED_X;
            panPhaseY += PAN_SPEED_Y;
            repaint();
        });
        animationTimer.start();
    }

    private BufferedImage toBufferedImage(Image img) {
        BufferedImage buffered = new BufferedImage(
                img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buffered.createGraphics();
        g2.drawImage(img, 0, 0, null);
        g2.dispose();
        return buffered;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double zoomFactor = 1.0 + Math.sin(zoomPhase) * MAX_ZOOM_VARIATION;
        double zoom = baseScale * zoomFactor;

        int iw = (int)(image.getWidth() * zoom);
        int ih = (int)(image.getHeight() * zoom);

        int maxPanX = Math.max(0, (iw - getWidth()) / 2);
        int maxPanY = Math.max(0, (ih - getHeight()) / 2);

        int dx = (int)(Math.sin(panPhaseX) * maxPanX);
        int dy = (int)(Math.sin(panPhaseY) * maxPanY);

        int x = (getWidth() - iw) / 2 + dx;
        int y = (getHeight() - ih) / 2 + dy;

        g2.drawImage(image, x, y, iw, ih, null);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        animationTimer.start();
    }

    @Override
    public void removeNotify() {
        animationTimer.stop();
        super.removeNotify();
    }
}