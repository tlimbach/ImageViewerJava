package ui;

import event.ImageZoomPreviewEvent;
import event.UserCommand;
import event.UserKeyboardEvent;
import service.EventBus;
import service.ImageZoomHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

public class ZoomableImagePanel extends JPanel {

    private static final int OVERLAY_BUTTON_WIDTH = 132;
    private static final int MENU_HEIGHT = 34;
    private static final int MENU_MARGIN = 16;
    private static final int MENU_GAP = 8;
    private static final int MIN_SELECTION_SIZE = 8;
    private static final int ZOOM_MARKER_SIZE = 18;
    private static final double WHEEL_ZOOM_STEP = 1.12;
    private static final double MIN_WHEEL_ZOOM_SIZE = 0.02;
    private static final int WHEEL_ZOOM_SAVE_DELAY_MS = 250;

    private final JButton displayModeButton = new JButton("Fullsize");
    private final JButton previousButton = new JButton("Vorheriges");
    private final JButton nextButton = new JButton("Nächstes");
    private final JButton resetButton = new JButton("Reset");
    private final JButton fullSizeButton = new JButton("Full size Zoom");
    private final JButton deleteButton = new JButton("Bild löschen");
    private final DeleteConfirmationOverlay deleteConfirmationOverlay = new DeleteConfirmationOverlay();
    private final Timer overlayHideTimer;
    private final Timer wheelZoomSaveTimer;

    private BufferedImage image;
    private File file;
    private ImageZoomHandler.ZoomSelection previewZoom;
    private Point dragStart;
    private Rectangle selection;
    private Point panStart;
    private ImageZoomHandler.ZoomSelection panStartZoom;
    private Rectangle2D panStartImageBounds;
    private File pendingWheelZoomFile;
    private ImageZoomHandler.ZoomSelection pendingWheelZoom;
    private boolean overlayVisible;
    private boolean displayFullSize;

    public ZoomableImagePanel() {
        setBackground(Color.BLACK);
        setOpaque(true);
        setLayout(null);
        setDoubleBuffered(true);

        displayModeButton.setFocusable(false);
        displayModeButton.setVisible(false);
        displayModeButton.addActionListener(e -> setDisplayFullSize(!displayFullSize));
        previousButton.setFocusable(false);
        previousButton.setVisible(false);
        previousButton.addActionListener(e -> {
            EventBus.get().publish(new UserKeyboardEvent(UserCommand.LEFT));
            showOverlayTemporarily();
        });
        nextButton.setFocusable(false);
        nextButton.setVisible(false);
        nextButton.addActionListener(e -> {
            EventBus.get().publish(new UserKeyboardEvent(UserCommand.RIGHT));
            showOverlayTemporarily();
        });

        resetButton.setFocusable(false);
        resetButton.setVisible(false);
        resetButton.addActionListener(e -> resetZoom());
        fullSizeButton.setFocusable(false);
        fullSizeButton.setVisible(false);
        fullSizeButton.addActionListener(e -> setFullSizeZoom());
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
        previousButton.addMouseListener(buttonMouseHandler);
        previousButton.addMouseMotionListener(buttonMouseHandler);
        nextButton.addMouseListener(buttonMouseHandler);
        nextButton.addMouseMotionListener(buttonMouseHandler);
        displayModeButton.addMouseListener(buttonMouseHandler);
        displayModeButton.addMouseMotionListener(buttonMouseHandler);
        resetButton.addMouseListener(buttonMouseHandler);
        resetButton.addMouseMotionListener(buttonMouseHandler);
        fullSizeButton.addMouseListener(buttonMouseHandler);
        fullSizeButton.addMouseMotionListener(buttonMouseHandler);
        deleteButton.addMouseListener(buttonMouseHandler);
        deleteButton.addMouseMotionListener(buttonMouseHandler);
        add(previousButton);
        add(nextButton);
        add(displayModeButton);
        add(resetButton);
        add(fullSizeButton);
        add(deleteButton);
        add(deleteConfirmationOverlay);
        setComponentZOrder(deleteConfirmationOverlay, 0);

        overlayHideTimer = new Timer(1000, e -> setOverlayVisible(false));
        overlayHideTimer.setRepeats(false);
        wheelZoomSaveTimer = new Timer(WHEEL_ZOOM_SAVE_DELAY_MS, e -> persistPendingWheelZoom());
        wheelZoomSaveTimer.setRepeats(false);

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showDeletePopup(e);
                    return;
                }
                if (image == null || !SwingUtilities.isLeftMouseButton(e)) return;
                if (tryStartPan(e)) {
                    return;
                }
                showOverlayTemporarily();
                dragStart = e.getPoint();
                selection = new Rectangle(dragStart);
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panStart != null) {
                    updatePan(e.getPoint(), false);
                    return;
                }
                if (dragStart == null) return;
                showOverlayTemporarily();
                selection = createRectangle(dragStart, e.getPoint());
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (panStart != null) {
                    updatePan(e.getPoint(), true);
                    clearPan();
                    return;
                }
                if (dragStart == null) return;
                showOverlayTemporarily();
                selection = createRectangle(dragStart, e.getPoint());
                applySelection();
                dragStart = null;
                selection = null;
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                showOverlayTemporarily();
                updatePanCursor();
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
        addMouseWheelListener(this::handleWheelZoom);
    }

    public void setImage(File file, BufferedImage image) {
        persistPendingWheelZoom();
        this.file = file;
        this.image = image;
        this.previewZoom = null;
        this.displayFullSize = false;
        this.selection = null;
        this.dragStart = null;
        this.panStart = null;
        repaint();
    }

    public void setPreviewZoom(File file, ImageZoomHandler.ZoomSelection zoom) {
        if (this.file == null || file == null || !this.file.equals(file)) return;
        this.previewZoom = zoom;
        repaint();
    }

    public void clearPreviewZoom(File file) {
        if (this.file == null || file == null || !this.file.equals(file)) return;
        this.previewZoom = null;
        repaint();
    }

    private void resetZoom() {
        clearPendingWheelZoom();
        previewZoom = null;
        displayFullSize = false;
        ImageZoomHandler.getInstance().resetZoomForFile(file);
        setOverlayVisible(false);
        repaint();
    }

    private void setFullSizeZoom() {
        if (file == null) return;
        clearPendingWheelZoom();
        previewZoom = null;
        displayFullSize = false;
        ImageZoomHandler.ZoomSelection fullSizeZoom = new ImageZoomHandler.ZoomSelection(0, 0, 1, 1);
        EventBus.get().publish(new ImageZoomPreviewEvent(file, fullSizeZoom));
        ImageZoomHandler.getInstance().setZoomForFile(file, fullSizeZoom);
        setOverlayVisible(false);
        repaint();
    }

    private void setDisplayFullSize(boolean displayFullSize) {
        this.displayFullSize = displayFullSize;
        updateDisplayModeButton();
        showOverlayTemporarily();
        updatePanCursor();
        repaint();
    }

    private void deleteCurrentImage() {
        if (file == null) return;
        deleteConfirmationOverlay.showForFile(file.getName(), () -> {
            if (!MediaDeleteSupport.deleteFile(file)) {
                deleteConfirmationOverlay.showError();
                return;
            }
            deleteConfirmationOverlay.setVisible(false);
            this.file = null;
            this.image = null;
            repaint();
        }, null);
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

    private void showOverlayTemporarily() {
        setOverlayVisible(image != null);
        overlayHideTimer.restart();
    }

    private void setOverlayVisible(boolean visible) {
        overlayVisible = visible;
        boolean hasZoom = getActiveZoom() != null;
        previousButton.setVisible(visible && file != null);
        nextButton.setVisible(visible && file != null);
        displayModeButton.setVisible(visible && hasZoom);
        updateDisplayModeButton();
        resetButton.setVisible(visible && file != null);
        resetButton.setEnabled(hasZoom);
        fullSizeButton.setVisible(visible && file != null);
        deleteButton.setVisible(visible && file != null);
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
        if (image == null || file == null || selection == null) return;
        if (selection.width < MIN_SELECTION_SIZE || selection.height < MIN_SELECTION_SIZE) return;

        Rectangle2D imageBounds = getRenderedImageBounds();
        if (imageBounds.getWidth() <= 0 || imageBounds.getHeight() <= 0) return;

        Rectangle selectedImageRect = toImageRectangle(selection, imageBounds);
        if (selectedImageRect.width <= 0 || selectedImageRect.height <= 0) return;

        ImageZoomHandler.ZoomSelection zoom = new ImageZoomHandler.ZoomSelection(
                selectedImageRect.getX() / image.getWidth(),
                selectedImageRect.getY() / image.getHeight(),
                selectedImageRect.getWidth() / image.getWidth(),
                selectedImageRect.getHeight() / image.getHeight()
        );
        clearPendingWheelZoom();
        ImageZoomHandler.getInstance().setZoomForFile(file, zoom);
        displayFullSize = false;
    }

    private boolean tryStartPan(MouseEvent e) {
        ImageZoomHandler.ZoomSelection zoom = getActiveZoom();
        if (zoom == null || displayFullSize || e.isShiftDown()) return false;

        panStart = e.getPoint();
        panStartZoom = previewZoom != null ? previewZoom : zoom;
        panStartImageBounds = getRenderedImageBounds();
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        showOverlayTemporarily();
        return true;
    }

    private void updatePan(Point point, boolean persist) {
        if (panStart == null || panStartZoom == null || panStartImageBounds == null) return;

        double dxImage = -(point.x - panStart.x) * image.getWidth() / panStartImageBounds.getWidth();
        double dyImage = -(point.y - panStart.y) * image.getHeight() / panStartImageBounds.getHeight();

        double newX = clamp(panStartZoom.x() + dxImage / image.getWidth(), 0, 1 - panStartZoom.width());
        double newY = clamp(panStartZoom.y() + dyImage / image.getHeight(), 0, 1 - panStartZoom.height());

        ImageZoomHandler.ZoomSelection updated = new ImageZoomHandler.ZoomSelection(
                newX,
                newY,
                panStartZoom.width(),
                panStartZoom.height()
        );

        previewZoom = updated;
        EventBus.get().publish(new ImageZoomPreviewEvent(file, updated));
        repaint();

        if (persist) {
            clearPendingWheelZoom();
            ImageZoomHandler.getInstance().setZoomForFile(file, updated);
        }
    }

    private void clearPan() {
        panStart = null;
        panStartZoom = null;
        panStartImageBounds = null;
        updatePanCursor();
    }

    private void updatePanCursor() {
        boolean canPan = file != null && !displayFullSize && getActiveZoom() != null;
        setCursor(canPan ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }

    private ImageZoomHandler.ZoomSelection getActiveZoom() {
        if (file == null) return null;
        return previewZoom != null ? previewZoom : ImageZoomHandler.getInstance().getZoomForFile(file);
    }

    private void handleWheelZoom(MouseWheelEvent e) {
        if (image == null || file == null) return;

        double wheelRotation = e.getPreciseWheelRotation();
        if (wheelRotation == 0) return;

        e.consume();
        showOverlayTemporarily();

        ImageZoomHandler.ZoomSelection currentZoom = previewZoom != null
                ? previewZoom
                : ImageZoomHandler.getInstance().getZoomForFile(file);

        if (currentZoom == null && wheelRotation > 0) {
            return;
        }

        Rectangle2D imageBounds = getRenderedImageBounds();
        if (imageBounds.getWidth() <= 0 || imageBounds.getHeight() <= 0) return;

        double focusX = clamp(
                (e.getX() - imageBounds.getX()) * image.getWidth() / imageBounds.getWidth(),
                0,
                image.getWidth()
        );
        double focusY = clamp(
                (e.getY() - imageBounds.getY()) * image.getHeight() / imageBounds.getHeight(),
                0,
                image.getHeight()
        );

        if (currentZoom == null) {
            currentZoom = new ImageZoomHandler.ZoomSelection(0, 0, 1, 1);
        }

        double factor = Math.pow(WHEEL_ZOOM_STEP, wheelRotation);
        double newWidth = clamp(currentZoom.width() * factor, MIN_WHEEL_ZOOM_SIZE, 1);
        double newHeight = clamp(currentZoom.height() * factor, MIN_WHEEL_ZOOM_SIZE, 1);

        double focusNormalizedX = focusX / image.getWidth();
        double focusNormalizedY = focusY / image.getHeight();
        double oldWidth = Math.max(currentZoom.width(), MIN_WHEEL_ZOOM_SIZE);
        double oldHeight = Math.max(currentZoom.height(), MIN_WHEEL_ZOOM_SIZE);
        double ratioX = clamp((focusNormalizedX - currentZoom.x()) / oldWidth, 0, 1);
        double ratioY = clamp((focusNormalizedY - currentZoom.y()) / oldHeight, 0, 1);

        double newX = clamp(focusNormalizedX - ratioX * newWidth, 0, 1 - newWidth);
        double newY = clamp(focusNormalizedY - ratioY * newHeight, 0, 1 - newHeight);

        ImageZoomHandler.ZoomSelection updated = new ImageZoomHandler.ZoomSelection(
                newX,
                newY,
                newWidth,
                newHeight
        );

        displayFullSize = false;
        previewZoom = updated;
        pendingWheelZoomFile = file;
        pendingWheelZoom = updated;
        EventBus.get().publish(new ImageZoomPreviewEvent(file, updated));
        wheelZoomSaveTimer.restart();
        updatePanCursor();
        repaint();
    }

    private void persistPendingWheelZoom() {
        if (pendingWheelZoomFile == null || pendingWheelZoom == null) return;
        wheelZoomSaveTimer.stop();
        ImageZoomHandler.getInstance().setZoomForFile(pendingWheelZoomFile, pendingWheelZoom);
        pendingWheelZoomFile = null;
        pendingWheelZoom = null;
    }

    private void clearPendingWheelZoom() {
        wheelZoomSaveTimer.stop();
        pendingWheelZoomFile = null;
        pendingWheelZoom = null;
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
    public void doLayout() {
        super.doLayout();
        Dimension menuSize = getOverlayMenuSize();
        int x = Math.max(MENU_MARGIN, getWidth() - menuSize.width - MENU_MARGIN);
        int y = MENU_MARGIN;
        previousButton.setBounds(
                x,
                y + (MENU_HEIGHT + MENU_GAP) * 2,
                OVERLAY_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        nextButton.setBounds(
                x + OVERLAY_BUTTON_WIDTH + MENU_GAP,
                y + (MENU_HEIGHT + MENU_GAP) * 2,
                OVERLAY_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        displayModeButton.setBounds(x, y, OVERLAY_BUTTON_WIDTH, MENU_HEIGHT);
        resetButton.setBounds(
                x + OVERLAY_BUTTON_WIDTH + MENU_GAP,
                y,
                OVERLAY_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        fullSizeButton.setBounds(
                x,
                y + MENU_HEIGHT + MENU_GAP,
                OVERLAY_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        deleteButton.setBounds(
                x + OVERLAY_BUTTON_WIDTH + MENU_GAP,
                y + MENU_HEIGHT + MENU_GAP,
                OVERLAY_BUTTON_WIDTH,
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

    private Dimension getOverlayMenuSize() {
        int width = OVERLAY_BUTTON_WIDTH * 2 + MENU_GAP;
        int height = MENU_HEIGHT * 3 + MENU_GAP * 2;
        return new Dimension(width, height);
    }

    private Rectangle getVisibleMenuBounds() {
        JButton[] buttons = {
                previousButton,
                nextButton,
                displayModeButton,
                resetButton,
                fullSizeButton,
                deleteButton
        };
        Rectangle result = null;
        for (JButton button : buttons) {
            if (!button.isVisible()) continue;
            result = result == null ? button.getBounds() : result.union(button.getBounds());
        }
        return result;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle2D imageBounds = getRenderedImageBounds();
        g2.drawImage(
                image,
                (int) Math.round(imageBounds.getX()),
                (int) Math.round(imageBounds.getY()),
                (int) Math.round(imageBounds.getWidth()),
                (int) Math.round(imageBounds.getHeight()),
                null
        );

        if (selection != null && selection.width > 0 && selection.height > 0) {
            paintSelection(g2);
        }

        paintZoomMarker(g2);

        if (overlayVisible) {
            paintOverlayBackground(g2);
        }

        g2.dispose();
    }

    private void paintSelection(Graphics2D g2) {
        Color shade = new Color(0, 0, 0, 90);
        Area outer = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
        outer.subtract(new Area(selection));
        g2.setColor(shade);
        g2.fill(outer);

        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(selection);
    }

    private void paintOverlayBackground(Graphics2D g2) {
        Rectangle bounds = getVisibleMenuBounds();
        if (bounds == null) return;
        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRoundRect(bounds.x - 6, bounds.y - 6, bounds.width + 12, bounds.height + 12, 8, 8);
    }

    private void paintZoomMarker(Graphics2D g2) {
        if (getActiveZoom() == null) return;

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

    private Rectangle2D getRenderedImageBounds() {
        ImageZoomHandler.ZoomSelection zoom = displayFullSize ? null : previewZoom != null ? previewZoom : ImageZoomHandler.getInstance().getZoomForFile(file);
        Rectangle2D viewRect = zoom == null ? null : getZoomViewRect(zoom);

        if (viewRect != null) {
            double scale = Math.max(getWidth() / viewRect.getWidth(), getHeight() / viewRect.getHeight());
            double x = -viewRect.getX() * scale;
            double y = -viewRect.getY() * scale;
            return new Rectangle2D.Double(x, y, image.getWidth() * scale, image.getHeight() * scale);
        }

        double scale = Math.max((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
        double width = image.getWidth() * scale;
        double height = image.getHeight() * scale;
        double x = (getWidth() - width) / 2.0;
        double overflowY = Math.max(0, height - getHeight());
        double y = -(overflowY / 3.0);
        return new Rectangle2D.Double(x, y, width, height);
    }

    private Rectangle2D getZoomViewRect(ImageZoomHandler.ZoomSelection zoom) {
        double selectedX = clamp(zoom.x(), 0, 1) * image.getWidth();
        double selectedY = clamp(zoom.y(), 0, 1) * image.getHeight();
        double selectedWidth = clamp(zoom.width(), 0, 1) * image.getWidth();
        double selectedHeight = clamp(zoom.height(), 0, 1) * image.getHeight();

        if (selectedWidth <= 0 || selectedHeight <= 0 || getWidth() <= 0 || getHeight() <= 0) return null;

        double viewportAspect = (double) getWidth() / getHeight();
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
}
