package ui.transition;

import service.ImageZoomHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;

public class SlideshowTransitionPanel extends JPanel {
    private static final int FRAME_DELAY_MS = 16;

    private final SlideshowTransitionImage previous;
    private final SlideshowTransitionImage next;
    private final SlideshowTransitionType type;
    private final int durationMs;
    private final Runnable onFinished;
    private final Timer timer;
    private long startTime;
    private double progress;

    public SlideshowTransitionPanel(SlideshowTransitionImage previous,
                                    SlideshowTransitionImage next,
                                    SlideshowTransitionType type,
                                    int durationMs,
                                    Runnable onFinished) {
        this.previous = previous;
        this.next = next;
        this.type = type;
        this.durationMs = Math.max(1, durationMs);
        this.onFinished = onFinished;
        this.timer = new Timer(FRAME_DELAY_MS, e -> updateProgress());

        setBackground(Color.BLACK);
        setOpaque(true);
        setDoubleBuffered(true);
    }

    public void start() {
        startTime = System.currentTimeMillis();
        progress = 0;
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    private void updateProgress() {
        long elapsed = System.currentTimeMillis() - startTime;
        progress = Math.min(1.0, (double) elapsed / durationMs);
        repaint();

        if (progress >= 1.0) {
            timer.stop();
            if (onFinished != null) {
                onFinished.run();
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (previous == null || next == null || previous.image() == null || next.image() == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            double easedProgress = easeInOut(progress);
            switch (type) {
                case CROSSFADE -> paintCrossfade(g2, easedProgress);
                case FADE_TO_BLACK -> paintFadeToBlack(g2, easedProgress);
                case WIPE -> paintWipe(g2, easedProgress);
                case SLIDE -> paintSlide(g2, easedProgress);
                case ZOOM_FADE -> paintZoomFade(g2, easedProgress);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintCrossfade(Graphics2D g2, double progress) {
        drawImage(g2, previous, 1.0, 0, 0, 1.0);
        drawImage(g2, next, progress, 0, 0, 1.0);
    }

    private void paintFadeToBlack(Graphics2D g2, double progress) {
        if (progress < 0.5) {
            drawImage(g2, previous, 1.0, 0, 0, 1.0);
            paintBlackOverlay(g2, progress * 2.0);
        } else {
            drawImage(g2, next, 1.0, 0, 0, 1.0);
            paintBlackOverlay(g2, (1.0 - progress) * 2.0);
        }
    }

    private void paintWipe(Graphics2D g2, double progress) {
        drawImage(g2, previous, 1.0, 0, 0, 1.0);

        Shape oldClip = g2.getClip();
        g2.setClip(0, 0, (int) Math.round(getWidth() * progress), getHeight());
        drawImage(g2, next, 1.0, 0, 0, 1.0);
        g2.setClip(oldClip);
    }

    private void paintSlide(Graphics2D g2, double progress) {
        int width = getWidth();
        int previousOffset = (int) Math.round(-progress * width);
        int nextOffset = (int) Math.round((1.0 - progress) * width);

        drawImage(g2, previous, 1.0, previousOffset, 0, 1.0);
        drawImage(g2, next, 1.0, nextOffset, 0, 1.0);
    }

    private void paintZoomFade(Graphics2D g2, double progress) {
        drawImage(g2, previous, 1.0, 0, 0, 1.0);
        double scale = 1.04 - progress * 0.04;
        drawImage(g2, next, progress, 0, 0, scale);
    }

    private void drawImage(Graphics2D g2,
                           SlideshowTransitionImage transitionImage,
                           double alpha,
                           int offsetX,
                           int offsetY,
                           double extraScale) {
        if (alpha <= 0) return;

        Rectangle2D bounds = getRenderedImageBounds(transitionImage, extraScale);
        Graphics2D copy = (Graphics2D) g2.create();
        try {
            copy.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp(alpha, 0, 1)));
            copy.drawImage(
                    transitionImage.image(),
                    (int) Math.round(bounds.getX()) + offsetX,
                    (int) Math.round(bounds.getY()) + offsetY,
                    (int) Math.round(bounds.getWidth()),
                    (int) Math.round(bounds.getHeight()),
                    null
            );
        } finally {
            copy.dispose();
        }
    }

    private Rectangle2D getRenderedImageBounds(SlideshowTransitionImage transitionImage, double extraScale) {
        int targetWidth = Math.max(1, getWidth());
        int targetHeight = Math.max(1, getHeight());
        Rectangle2D viewRect = getBaseViewRect(transitionImage, targetWidth, targetHeight);
        double zoom = Math.max(
                targetWidth / viewRect.getWidth(),
                targetHeight / viewRect.getHeight()
        );

        double baseWidth = transitionImage.image().getWidth() * zoom;
        double baseHeight = transitionImage.image().getHeight() * zoom;
        double baseX = -viewRect.getX() * zoom;
        double baseY = -viewRect.getY() * zoom;

        double width = baseWidth * extraScale;
        double height = baseHeight * extraScale;
        double centerX = targetWidth / 2.0;
        double centerY = targetHeight / 2.0;
        double x = centerX + (baseX - centerX) * extraScale;
        double y = centerY + (baseY - centerY) * extraScale;

        return new Rectangle2D.Double(x, y, width, height);
    }

    private Rectangle2D getBaseViewRect(SlideshowTransitionImage transitionImage, int targetWidth, int targetHeight) {
        ImageZoomHandler.ZoomSelection zoomSelection = transitionImage.zoomSelection();
        int imageWidth = transitionImage.image().getWidth();
        int imageHeight = transitionImage.image().getHeight();

        if (zoomSelection == null) {
            return new Rectangle2D.Double(0, 0, imageWidth, imageHeight);
        }

        double selectedX = clamp(zoomSelection.x(), 0, 1) * imageWidth;
        double selectedY = clamp(zoomSelection.y(), 0, 1) * imageHeight;
        double selectedWidth = clamp(zoomSelection.width(), 0, 1) * imageWidth;
        double selectedHeight = clamp(zoomSelection.height(), 0, 1) * imageHeight;

        if (selectedWidth <= 0 || selectedHeight <= 0) {
            return new Rectangle2D.Double(0, 0, imageWidth, imageHeight);
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

        viewWidth = Math.min(viewWidth, imageWidth);
        viewHeight = Math.min(viewHeight, imageHeight);

        double centerX = selectedX + selectedWidth / 2.0;
        double centerY = selectedY + selectedHeight / 2.0;
        double x = clamp(centerX - viewWidth / 2.0, 0, imageWidth - viewWidth);
        double y = clamp(centerY - viewHeight / 2.0, 0, imageHeight - viewHeight);

        return new Rectangle2D.Double(x, y, viewWidth, viewHeight);
    }

    private void paintBlackOverlay(Graphics2D g2, double alpha) {
        Graphics2D copy = (Graphics2D) g2.create();
        try {
            copy.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp(alpha, 0, 1)));
            copy.setColor(Color.BLACK);
            copy.fillRect(0, 0, getWidth(), getHeight());
        } finally {
            copy.dispose();
        }
    }

    private double easeInOut(double value) {
        double clamped = clamp(value, 0, 1);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
