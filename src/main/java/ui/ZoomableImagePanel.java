package ui;

import service.ImageZoomHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

public class ZoomableImagePanel extends JPanel {

    private static final int MENU_WIDTH = 96;
    private static final int MENU_HEIGHT = 34;
    private static final int MENU_MARGIN = 16;
    private static final int MENU_GAP = 8;
    private static final int DELETE_BUTTON_SIZE = 34;
    private static final int MIN_SELECTION_SIZE = 8;
    private static final int ZOOM_MARKER_SIZE = 18;

    private final JButton resetButton = new JButton("Reset");
    private final JButton deleteButton = new JButton("🗑");
    private final DeleteConfirmationOverlay deleteConfirmationOverlay = new DeleteConfirmationOverlay();
    private final Timer overlayHideTimer;

    private BufferedImage image;
    private File file;
    private Point dragStart;
    private Rectangle selection;
    private boolean overlayVisible;

    public ZoomableImagePanel() {
        setBackground(Color.BLACK);
        setOpaque(true);
        setLayout(null);
        setDoubleBuffered(true);

        resetButton.setFocusable(false);
        resetButton.setVisible(false);
        resetButton.addActionListener(e -> resetZoom());
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
        resetButton.addMouseListener(buttonMouseHandler);
        resetButton.addMouseMotionListener(buttonMouseHandler);
        deleteButton.addMouseListener(buttonMouseHandler);
        deleteButton.addMouseMotionListener(buttonMouseHandler);
        add(resetButton);
        add(deleteButton);
        add(deleteConfirmationOverlay);
        setComponentZOrder(deleteConfirmationOverlay, 0);

        overlayHideTimer = new Timer(1000, e -> setOverlayVisible(false));
        overlayHideTimer.setRepeats(false);

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showDeletePopup(e);
                    return;
                }
                if (image == null || !SwingUtilities.isLeftMouseButton(e)) return;
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

    public void setImage(File file, BufferedImage image) {
        this.file = file;
        this.image = image;
        this.selection = null;
        this.dragStart = null;
        repaint();
    }

    private void resetZoom() {
        ImageZoomHandler.getInstance().resetZoomForFile(file);
        setOverlayVisible(false);
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
        boolean hasZoom = file != null && ImageZoomHandler.getInstance().getZoomForFile(file) != null;
        resetButton.setVisible(visible && file != null);
        resetButton.setEnabled(hasZoom);
        deleteButton.setVisible(visible && file != null);
        repaint();
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
        ImageZoomHandler.getInstance().setZoomForFile(file, zoom);
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
        resetButton.setBounds(
                Math.max(MENU_MARGIN, getWidth() - MENU_WIDTH - DELETE_BUTTON_SIZE - MENU_GAP - MENU_MARGIN),
                MENU_MARGIN,
                MENU_WIDTH,
                MENU_HEIGHT
        );
        deleteButton.setBounds(
                Math.max(MENU_MARGIN, getWidth() - DELETE_BUTTON_SIZE - MENU_MARGIN),
                MENU_MARGIN,
                DELETE_BUTTON_SIZE,
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
        if (!resetButton.isVisible() && !deleteButton.isVisible()) return;
        int x = resetButton.isVisible() ? resetButton.getX() : deleteButton.getX();
        int y = resetButton.isVisible() ? resetButton.getY() : deleteButton.getY();
        int right = Math.max(resetButton.getX() + resetButton.getWidth(), deleteButton.getX() + deleteButton.getWidth());
        int bottom = Math.max(resetButton.getY() + resetButton.getHeight(), deleteButton.getY() + deleteButton.getHeight());
        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRoundRect(x - 6, y - 6, right - x + 12, bottom - y + 12, 8, 8);
    }

    private void paintZoomMarker(Graphics2D g2) {
        if (file == null || ImageZoomHandler.getInstance().getZoomForFile(file) == null) return;

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
        ImageZoomHandler.ZoomSelection zoom = ImageZoomHandler.getInstance().getZoomForFile(file);
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
