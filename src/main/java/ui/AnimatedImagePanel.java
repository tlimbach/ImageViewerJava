package ui;

import event.ImageZoomPreviewEvent;
import service.Controller;
import service.EventBus;
import service.ImageZoomHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

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
    private static final int RESET_BUTTON_WIDTH = 96;
    private static final int FULL_SIZE_BUTTON_WIDTH = 126;
    private static final int DISPLAY_MODE_BUTTON_WIDTH = 92;
    private static final int MENU_HEIGHT = 34;
    private static final int MENU_MARGIN = 16;
    private static final int MENU_GAP = 8;
    private static final int DELETE_BUTTON_WIDTH = 116;
    private static final int MIN_SELECTION_SIZE = 8;
    private static final int ZOOM_MARKER_SIZE = 18;

    private static double initialZoom = 0;

    // ------------------ INSTANZVARIABLEN ------------------
    private final BufferedImage image;
    private final Timer animationTimer;
    private final int baseWidth;
    private final int baseHeight;
    private final File file;
    private final Runnable onInteractionStarted;
    private final Runnable onInteractionFinished;
    private final JButton displayModeButton = new JButton("Fullsize");
    private final JButton resetButton = new JButton("Reset");
    private final JButton fullSizeButton = new JButton("Full size Zoom");
    private final JButton deleteButton = new JButton("Bild löschen");
    private final DeleteConfirmationOverlay deleteConfirmationOverlay = new DeleteConfirmationOverlay();
    private final Timer overlayHideTimer;
    private ImageZoomHandler.ZoomSelection zoomSelection;

    private double zoomPhase = 0;
    private double panPhaseX = 0;
    private double panPhaseY = 1.7;

    private double alteZoom = 0;
    private Point dragStart;
    private Rectangle selection;
    private Rectangle2D renderedImageBounds;
    private boolean overlayVisible;
    private boolean displayFullSize;

    public AnimatedImagePanel(Image image, int newWidth, int newHeight) {
        this(image, newWidth, newHeight, null, null, null, null);
    }

    public AnimatedImagePanel(Image image, int newWidth, int newHeight, ImageZoomHandler.ZoomSelection zoomSelection) {
        this(image, newWidth, newHeight, null, zoomSelection, null, null);
    }

    public AnimatedImagePanel(Image image, int newWidth, int newHeight, File file,
                              ImageZoomHandler.ZoomSelection zoomSelection,
                              Runnable onInteractionStarted,
                              Runnable onInteractionFinished) {
        initialZoom = 0;
        this.image = toBufferedImage(image);
        this.baseWidth = newWidth;
        this.baseHeight = newHeight;
        this.file = file;
        this.zoomSelection = zoomSelection;
        this.onInteractionStarted = onInteractionStarted;
        this.onInteractionFinished = onInteractionFinished;

        setLayout(null);
        setBackground(Color.BLACK);
        setOpaque(true);
        setDoubleBuffered(true);
        installResetButton();
        installMouseSelection();
        overlayHideTimer = new Timer(1000, e -> setOverlayVisible(false));
        overlayHideTimer.setRepeats(false);

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

    private void installResetButton() {
        displayModeButton.setFocusable(false);
        displayModeButton.setVisible(false);
        displayModeButton.addActionListener(e -> setDisplayFullSize(!displayFullSize));

        resetButton.setFocusable(false);
        resetButton.setVisible(false);
        resetButton.addActionListener(e -> {
            beginInteraction();
            ImageZoomHandler.getInstance().resetZoomForFile(file);
            zoomSelection = null;
            displayFullSize = false;
            setOverlayVisible(false);
            repaint();
            finishInteraction();
        });
        fullSizeButton.setFocusable(false);
        fullSizeButton.setVisible(false);
        fullSizeButton.addActionListener(e -> {
            beginInteraction();
            zoomSelection = new ImageZoomHandler.ZoomSelection(0, 0, 1, 1);
            displayFullSize = false;
            EventBus.get().publish(new ImageZoomPreviewEvent(file, zoomSelection));
            ImageZoomHandler.getInstance().setZoomForFile(file, zoomSelection);
            setOverlayVisible(false);
            repaint();
            finishInteraction();
        });
        deleteButton.setFocusable(false);
        deleteButton.setVisible(false);
        deleteButton.setToolTipText("Bild löschen");
        deleteButton.addActionListener(e -> deleteCurrentImage());
        MouseAdapter buttonMouseHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                showOverlayTemporarily();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                showOverlayTemporarily();
            }
        };
        displayModeButton.addMouseListener(buttonMouseHandler);
        displayModeButton.addMouseMotionListener(buttonMouseHandler);
        resetButton.addMouseListener(buttonMouseHandler);
        resetButton.addMouseMotionListener(buttonMouseHandler);
        fullSizeButton.addMouseListener(buttonMouseHandler);
        fullSizeButton.addMouseMotionListener(buttonMouseHandler);
        deleteButton.addMouseListener(buttonMouseHandler);
        deleteButton.addMouseMotionListener(buttonMouseHandler);
        add(displayModeButton);
        add(resetButton);
        add(fullSizeButton);
        add(deleteButton);
        add(deleteConfirmationOverlay);
        setComponentZOrder(deleteConfirmationOverlay, 0);
    }

    private void installMouseSelection() {
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showDeletePopup(e);
                    return;
                }
                if (file == null || !SwingUtilities.isLeftMouseButton(e)) return;
                beginInteraction();
                showOverlayTemporarily();
                dragStart = e.getPoint();
                selection = new Rectangle(dragStart);
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) return;
                showOverlayTemporarily();
                selection = createRectangle(dragStart, e.getPoint());
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragStart == null) return;
                showOverlayTemporarily();
                selection = createRectangle(dragStart, e.getPoint());
                applySelection();
                dragStart = null;
                selection = null;
                repaint();
                finishInteraction();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                showOverlayTemporarily();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                showOverlayTemporarily();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!contains(e.getPoint())) {
                    setOverlayVisible(false);
                }
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    private void deleteCurrentImage() {
        if (file == null) return;
        beginInteraction();
        deleteConfirmationOverlay.showForFile(file.getName(), () -> {
            if (!MediaDeleteSupport.deleteFile(file)) {
                deleteConfirmationOverlay.showError();
                return;
            }
            deleteConfirmationOverlay.setVisible(false);
            finishInteraction();
        }, this::finishInteraction);
        setOverlayVisible(false);
        repaint();
    }

    private void showDeletePopup(MouseEvent e) {
        if (file == null) return;
        JPopupMenu popup = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Bild löschen");
        deleteItem.addActionListener(a -> deleteCurrentImage());
        popup.add(deleteItem);
        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    private void beginInteraction() {
        if (Controller.getInstance().getControlPanel().getSlideshowManager().isMoveImages()) {
            animationTimer.stop();
        }
        if (onInteractionStarted != null) {
            onInteractionStarted.run();
        }
    }

    private void finishInteraction() {
        if (Controller.getInstance().getControlPanel().getSlideshowManager().isMoveImages()) {
            animationTimer.start();
        }
        if (onInteractionFinished != null) {
            onInteractionFinished.run();
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

    private void showOverlayTemporarily() {
        setOverlayVisible(file != null);
        overlayHideTimer.restart();
    }

    private void setOverlayVisible(boolean visible) {
        overlayVisible = visible;
        boolean hasZoom = zoomSelection != null;
        displayModeButton.setVisible(visible && hasZoom);
        updateDisplayModeButton();
        resetButton.setVisible(visible && file != null);
        resetButton.setEnabled(hasZoom);
        fullSizeButton.setVisible(visible && file != null);
        deleteButton.setVisible(visible && file != null);
        repaint();
    }

    private void setDisplayFullSize(boolean displayFullSize) {
        this.displayFullSize = displayFullSize;
        updateDisplayModeButton();
        showOverlayTemporarily();
        repaint();
    }

    private void updateDisplayModeButton() {
        displayModeButton.setText(displayFullSize ? "cropped" : "Fullsize");
    }

    private Rectangle createRectangle(Point a, Point b) {
        int x = Math.min(a.x, b.x);
        int y = Math.min(a.y, b.y);
        int width = Math.abs(a.x - b.x);
        int height = Math.abs(a.y - b.y);
        return new Rectangle(x, y, width, height);
    }

    private void applySelection() {
        if (file == null || selection == null) return;
        if (selection.width < MIN_SELECTION_SIZE || selection.height < MIN_SELECTION_SIZE) return;

        Rectangle2D imageBounds = renderedImageBounds != null
                ? renderedImageBounds
                : getRenderedImageBounds(getWidth(), getHeight());
        if (imageBounds.getWidth() <= 0 || imageBounds.getHeight() <= 0) return;

        Rectangle selectedImageRect = toImageRectangle(selection, imageBounds);
        if (selectedImageRect.width <= 0 || selectedImageRect.height <= 0) return;

        zoomSelection = new ImageZoomHandler.ZoomSelection(
                selectedImageRect.getX() / image.getWidth(),
                selectedImageRect.getY() / image.getHeight(),
                selectedImageRect.getWidth() / image.getWidth(),
                selectedImageRect.getHeight() / image.getHeight()
        );
        ImageZoomHandler.getInstance().setZoomForFile(file, zoomSelection);
        displayFullSize = false;
        setOverlayVisible(true);
    }

    private Rectangle toImageRectangle(Rectangle panelRect, Rectangle2D imageBounds) {
        double scaleX = image.getWidth() / imageBounds.getWidth();
        double scaleY = image.getHeight() / imageBounds.getHeight();

        int x1 = clamp((int) Math.floor((panelRect.x - imageBounds.getX()) * scaleX), 0, image.getWidth());
        int y1 = clamp((int) Math.floor((panelRect.y - imageBounds.getY()) * scaleY), 0, image.getHeight());
        int x2 = clamp((int) Math.ceil((panelRect.x + panelRect.width - imageBounds.getX()) * scaleX), 0, image.getWidth());
        int y2 = clamp((int) Math.ceil((panelRect.y + panelRect.height - imageBounds.getY()) * scaleY), 0, image.getHeight());

        int x = Math.min(x1, x2);
        int y = Math.min(y1, y2);
        return new Rectangle(x, y, Math.abs(x2 - x1), Math.abs(y2 - y1));
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
        renderedImageBounds = new Rectangle2D.Double(x, y, iw, ih);

        g2.drawImage(image, x, y, iw, ih, null);

        if (selection != null && selection.width > 0 && selection.height > 0) {
            paintSelection(g2);
        }

        paintZoomMarker(g2);

        if (overlayVisible) {
            paintOverlayBackground(g2);
        }
    }

    private Rectangle2D getRenderedImageBounds(int targetWidth, int targetHeight) {
        Rectangle2D viewRect = getBaseViewRect(targetWidth, targetHeight);
        double zoom = Math.max(
                targetWidth / viewRect.getWidth(),
                targetHeight / viewRect.getHeight()
        ) * BASE_SCALE_MULTIPLIER;

        double width = image.getWidth() * zoom;
        double height = image.getHeight() * zoom;
        double x = -viewRect.getX() * zoom;
        double y = -viewRect.getY() * zoom;
        return new Rectangle2D.Double(x, y, width, height);
    }

    private void paintSelection(Graphics2D g2) {
        Area outer = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
        outer.subtract(new Area(selection));
        g2.setColor(new Color(0, 0, 0, 90));
        g2.fill(outer);

        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(selection);
    }

    private void paintOverlayBackground(Graphics2D g2) {
        if (!displayModeButton.isVisible() && !resetButton.isVisible() && !fullSizeButton.isVisible() && !deleteButton.isVisible()) return;
        int x = displayModeButton.isVisible() ? displayModeButton.getX() : resetButton.isVisible() ? resetButton.getX() : deleteButton.getX();
        int y = displayModeButton.isVisible() ? displayModeButton.getY() : resetButton.isVisible() ? resetButton.getY() : deleteButton.getY();
        int right = Math.max(
                Math.max(
                        displayModeButton.getX() + displayModeButton.getWidth(),
                        Math.max(resetButton.getX() + resetButton.getWidth(), fullSizeButton.getX() + fullSizeButton.getWidth())
                ),
                deleteButton.getX() + deleteButton.getWidth()
        );
        int bottom = Math.max(
                Math.max(
                        displayModeButton.getY() + displayModeButton.getHeight(),
                        Math.max(resetButton.getY() + resetButton.getHeight(), fullSizeButton.getY() + fullSizeButton.getHeight())
                ),
                deleteButton.getY() + deleteButton.getHeight()
        );
        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRoundRect(x - 6, y - 6, right - x + 12, bottom - y + 12, 8, 8);
    }

    private void paintZoomMarker(Graphics2D g2) {
        if (zoomSelection == null) return;

        int w = getWidth();
        int h = getHeight();
        Polygon marker = new Polygon(
                new int[]{w, w, w - ZOOM_MARKER_SIZE},
                new int[]{h, h - ZOOM_MARKER_SIZE, h},
                3
        );
        g2.setColor(new Color(255, 221, 0, 230));
        g2.fillPolygon(marker);
    }

    private Rectangle2D getBaseViewRect(int targetWidth, int targetHeight) {
        if (zoomSelection == null || displayFullSize) {
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
    public void doLayout() {
        super.doLayout();
        displayModeButton.setBounds(
                Math.max(MENU_MARGIN, getWidth() - DISPLAY_MODE_BUTTON_WIDTH - RESET_BUTTON_WIDTH - FULL_SIZE_BUTTON_WIDTH - DELETE_BUTTON_WIDTH - MENU_GAP * 3 - MENU_MARGIN),
                MENU_MARGIN,
                DISPLAY_MODE_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        resetButton.setBounds(
                Math.max(MENU_MARGIN, getWidth() - RESET_BUTTON_WIDTH - FULL_SIZE_BUTTON_WIDTH - DELETE_BUTTON_WIDTH - MENU_GAP * 2 - MENU_MARGIN),
                MENU_MARGIN,
                RESET_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        fullSizeButton.setBounds(
                Math.max(MENU_MARGIN, getWidth() - FULL_SIZE_BUTTON_WIDTH - DELETE_BUTTON_WIDTH - MENU_GAP - MENU_MARGIN),
                MENU_MARGIN,
                FULL_SIZE_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        deleteButton.setBounds(
                Math.max(MENU_MARGIN, getWidth() - DELETE_BUTTON_WIDTH - MENU_MARGIN),
                MENU_MARGIN,
                DELETE_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        int confirmWidth = Math.min(360, Math.max(260, getWidth() - 80));
        int confirmHeight = 118;
        deleteConfirmationOverlay.setBounds(
                Math.max(20, (getWidth() - confirmWidth) / 2),
                Math.max(20, (getHeight() - confirmHeight) / 2),
                confirmWidth,
                confirmHeight
        );
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
