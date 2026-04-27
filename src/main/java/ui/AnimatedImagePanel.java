package ui;

import service.Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class AnimatedImagePanel extends JPanel {
    // ------------------ STELLSCHRAUBEN ------------------
//    private static final double BASE_SCALE_MULTIPLIER = 1.8;     // Basisvergrößerung über die Zielgröße hinaus
//    private static final double MAX_ZOOM_VARIATION = -0.1;       // Zoomschwankung (z. B. 0.15 = ±15%)
//    private static final double ZOOM_SPEED = 0.008;              // Geschwindigkeit des Zooms
//    private static final double PAN_SPEED_X = 0.0035;             // Geschwindigkeit horizontales Schwenken
//    private static final double PAN_SPEED_Y = 0.0035;             // Geschwindigkeit vertikales Schwenken

    // Stellschrauben
    private static final double BASE_SCALE_MULTIPLIER = 1.0; // exakte Zielgröße am Start
    private static final double MAX_ZOOM_VARIATION = 0.25;    // nur positive Variation (reinzoomen)
    private static final double ZOOM_SPEED = 0.003;
    private static final double PAN_SPEED_X = 0.005;
    private static final double PAN_SPEED_Y = 0.005;

    private static double initialZoom = 0;

    // ------------------ INSTANZVARIABLEN ------------------
    private final BufferedImage image;
    private final Timer animationTimer;
    private final int baseWidth;
    private final int baseHeight;

    private double zoomPhase = 0;
    private double panPhaseX = 0;
    private double panPhaseY = 1.7;

    private double alteZoom = 0;

    public AnimatedImagePanel(Image image, int newWidth, int newHeight) {
        initialZoom = 0;
        this.image = toBufferedImage(image);
        this.baseWidth = newWidth;
        this.baseHeight = newHeight;

        setDoubleBuffered(true);

        animationTimer = new Timer(15, e -> {
            zoomPhase += ZOOM_SPEED;
            panPhaseX += PAN_SPEED_X;
            panPhaseY += PAN_SPEED_Y;
            repaint();
        });

        if (Controller.getInstance().getControlPanel().getSlideshowManager().isMoveImages()) {
               animationTimer.start();
        }
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

//        double zoomFactor = 1.0 + Math.sin(zoomPhase) * MAX_ZOOM_VARIATION;
//        double zoom = baseScale * zoomFactor;

        double zoomFactor = 1.0 + (Math.sin(zoomPhase) * 0.5 + 0.5) * MAX_ZOOM_VARIATION;

        if (zoomFactor < alteZoom)
            zoomFactor = alteZoom;

        alteZoom = zoomFactor;


        if (initialZoom == 0)
            initialZoom = zoomFactor;

        if (zoomFactor < initialZoom)
            zoomFactor = initialZoom;

        boolean moveImages = Controller.getInstance().getControlPanel().getSlideshowManager().isMoveImages();
        if (!moveImages) {
            zoomFactor = 1.0;
        }

        int targetWidth = getWidth() > 0 ? getWidth() : baseWidth;
        int targetHeight = getHeight() > 0 ? getHeight() : baseHeight;

        double coverScale = Math.max(
                (double) targetWidth / image.getWidth(),
                (double) targetHeight / image.getHeight()
        );
        double zoom = coverScale * BASE_SCALE_MULTIPLIER * zoomFactor;

        int iw = (int) (image.getWidth() * zoom);
        int ih = (int) (image.getHeight() * zoom);

        int overflowX = Math.max(0, iw - targetWidth);
        int overflowY = Math.max(0, ih - targetHeight);
        int maxPanX = overflowX / 2;
        int maxPanY = overflowY / 6;

        int dx = moveImages ? (int) (Math.sin(panPhaseX) * maxPanX) : 0;
        int dy = moveImages ? (int) (Math.sin(panPhaseY) * maxPanY) : 0;

        int x = (targetWidth - iw) / 2 + dx;
        int y = -(overflowY / 3) + dy;

        g2.drawImage(image, x, y, iw, ih, null);
    }


    @Override
    public void addNotify() {
        super.addNotify();
        if (Controller.getInstance().getControlPanel().getSlideshowManager().isMoveImages()) {
            animationTimer.start();
        }
    }

    @Override
    public void removeNotify() {
        animationTimer.stop();
        super.removeNotify();
    }
}
