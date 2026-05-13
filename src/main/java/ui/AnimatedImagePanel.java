package ui;

import service.Controller;
import service.ImageZoomHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
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
    private final ImageZoomHandler.ZoomSelection zoomSelection;

    private double zoomPhase = 0;
    private double panPhaseX = 0;
    private double panPhaseY = 1.7;

    private double alteZoom = 0;

    public AnimatedImagePanel(Image image, int newWidth, int newHeight) {
        this(image, newWidth, newHeight, null);
    }

    public AnimatedImagePanel(Image image, int newWidth, int newHeight, ImageZoomHandler.ZoomSelection zoomSelection) {
        initialZoom = 0;
        this.image = toBufferedImage(image);
        this.baseWidth = newWidth;
        this.baseHeight = newHeight;
        this.zoomSelection = zoomSelection;

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

        Rectangle2D viewRect = getBaseViewRect(targetWidth, targetHeight);
        double zoom = Math.max(
                targetWidth / viewRect.getWidth(),
                targetHeight / viewRect.getHeight()
        ) * BASE_SCALE_MULTIPLIER * zoomFactor;

        int iw = (int) (image.getWidth() * zoom);
        int ih = (int) (image.getHeight() * zoom);

        int baseX = (int) Math.round(-viewRect.getX() * zoom);
        int baseY = (int) Math.round(-viewRect.getY() * zoom);

        int overflowX = Math.max(0, iw - targetWidth);
        int overflowY = Math.max(0, ih - targetHeight);
        int maxPanX = overflowX / 2;
        int maxPanY = overflowY / 6;

        int dx = moveImages ? (int) (Math.sin(panPhaseX) * maxPanX) : 0;
        int dy = moveImages ? (int) (Math.sin(panPhaseY) * maxPanY) : 0;

        int x = clamp(baseX + dx, targetWidth - iw, 0);
        int y = clamp(baseY + dy, targetHeight - ih, 0);

        g2.drawImage(image, x, y, iw, ih, null);
    }

    private Rectangle2D getBaseViewRect(int targetWidth, int targetHeight) {
        if (zoomSelection == null) {
            return new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight());
        }

        double selectedX = clamp(zoomSelection.x(), 0, 1) * image.getWidth();
        double selectedY = clamp(zoomSelection.y(), 0, 1) * image.getHeight();
        double selectedWidth = clamp(zoomSelection.width(), 0, 1) * image.getWidth();
        double selectedHeight = clamp(zoomSelection.height(), 0, 1) * image.getHeight();

        if (selectedWidth <= 0 || selectedHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight());
        }

        double viewportAspect = (double) targetWidth / targetHeight;
        double selectedAspect = selectedWidth / selectedHeight;
        double viewWidth = selectedWidth;
        double viewHeight = selectedHeight;

        if (selectedAspect < viewportAspect) {
            viewWidth = selectedHeight * viewportAspect;
        } else {
            viewHeight = selectedWidth / viewportAspect;
        }

        viewWidth = Math.min(viewWidth, image.getWidth());
        viewHeight = Math.min(viewHeight, image.getHeight());

        double centerX = selectedX + selectedWidth / 2.0;
        double centerY = selectedY + selectedHeight / 2.0;
        double x = clamp(centerX - viewWidth / 2.0, 0, image.getWidth() - viewWidth);
        double y = clamp(centerY - viewHeight / 2.0, 0, image.getHeight() - viewHeight);

        return new Rectangle2D.Double(x, y, viewWidth, viewHeight);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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
