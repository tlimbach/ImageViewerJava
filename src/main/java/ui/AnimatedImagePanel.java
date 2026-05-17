package ui;

import event.TagsChangedEvent;
import event.UserCommand;
import event.UserKeyboardEvent;
import model.AppState;
import service.Controller;
import service.EventBus;
import service.ImageEnhancementUtils;
import service.ImageSaturationHandler;
import service.ImageTemperatureHandler;
import service.ImageZoomHandler;
import service.TagHandler;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

public class AnimatedImagePanel extends JPanel {
    private static final double BASE_SCALE_MULTIPLIER = 1.0; // exakte Zielgröße am Start
    private static final int OVERLAY_BUTTON_WIDTH = 150;
    private static final int MENU_HEIGHT = 34;
    private static final int MENU_MARGIN = 16;
    private static final int MENU_GAP = 8;
    private static final int MIN_SELECTION_SIZE = 8;
    private static final int ZOOM_MARKER_SIZE = 18;
    private static final String SPECIAL_TAG = "Ach Du Scheisse";
    private static final String OK_TAG = "ok";
    private static final String BEAUTIFULL_TAG = "beautifull";
    private static final String ART_TAG = "art";

    private final BufferedImage image;
    private final int baseWidth;
    private final int baseHeight;
    private final File file;
    private final Runnable onInteractionStarted;
    private final Runnable onInteractionFinished;
    private final OverlayButton displayModeButton = new OverlayButton("Fullsize");
    private final OverlayButton resetButton = new OverlayButton("Reset");
    private final OverlayButton specialTagButton = new OverlayButton(SPECIAL_TAG);
    private final OverlayButton okTagButton = new OverlayButton(OK_TAG);
    private final OverlayButton beautifullTagButton = new OverlayButton(BEAUTIFULL_TAG);
    private final OverlayButton artTagButton = new OverlayButton(ART_TAG);
    private final JComboBox<ImageSaturationHandler.SaturationLevel> saturationCombo =
            new JComboBox<>(ImageSaturationHandler.SaturationLevel.values());
    private final JSlider temperatureSlider = new JSlider(
            ImageTemperatureHandler.MIN_TEMPERATURE,
            ImageTemperatureHandler.MAX_TEMPERATURE,
            ImageTemperatureHandler.NEUTRAL_TEMPERATURE
    );
    private final JCheckBox nextImageAfterTaggingCheckbox = new JCheckBox("Nächstes Bild nach Tagging");
    private final OverlayButton cropButton = new OverlayButton("Neues Bild");
    private final OverlayButton deleteButton = new OverlayButton("Bild löschen");
    private final OverlayButton closeButton = new OverlayButton("Schließen");
    private final DeleteConfirmationOverlay deleteConfirmationOverlay = new DeleteConfirmationOverlay();
    private final Timer overlayHideTimer;
    private ImageZoomHandler.ZoomSelection zoomSelection;
    private BufferedImage displayImage;
    private boolean updatingSaturationCombo;
    private boolean updatingTemperatureSlider;

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
        this.image = toBufferedImage(image);
        this.displayImage = this.image;
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
        applyImageEnhancements(
                ImageSaturationHandler.getInstance().getLevelForFile(file),
                ImageTemperatureHandler.getInstance().getTemperatureForFile(file)
        );
        installMouseSelection();
        overlayHideTimer = new Timer(1000, e -> hideOverlayIfPointerIsAway());
        overlayHideTimer.setRepeats(false);
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
        installTagButton(specialTagButton, SPECIAL_TAG);
        installTagButton(okTagButton, OK_TAG);
        installTagButton(beautifullTagButton, BEAUTIFULL_TAG);
        installTagButton(artTagButton, ART_TAG);
        installSaturationCombo();
        installTemperatureSlider();
        installNextImageAfterTaggingCheckbox();
        cropButton.setFocusable(false);
        cropButton.setVisible(false);
        cropButton.setToolTipText("Neues Bild aus Auswahl");
        cropButton.addActionListener(e -> saveZoomSelectionAsNewImage());
        deleteButton.setFocusable(false);
        deleteButton.setVisible(false);
        deleteButton.setToolTipText("Bild löschen");
        deleteButton.addActionListener(e -> deleteCurrentImage());
        closeButton.setFocusable(false);
        closeButton.setVisible(false);
        closeButton.setToolTipText("Media View schließen");
        closeButton.addActionListener(e -> closeMediaView());
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
        specialTagButton.addMouseListener(buttonMouseHandler);
        specialTagButton.addMouseMotionListener(buttonMouseHandler);
        okTagButton.addMouseListener(buttonMouseHandler);
        okTagButton.addMouseMotionListener(buttonMouseHandler);
        beautifullTagButton.addMouseListener(buttonMouseHandler);
        beautifullTagButton.addMouseMotionListener(buttonMouseHandler);
        artTagButton.addMouseListener(buttonMouseHandler);
        artTagButton.addMouseMotionListener(buttonMouseHandler);
        saturationCombo.addMouseListener(buttonMouseHandler);
        saturationCombo.addMouseMotionListener(buttonMouseHandler);
        temperatureSlider.addMouseListener(buttonMouseHandler);
        temperatureSlider.addMouseMotionListener(buttonMouseHandler);
        nextImageAfterTaggingCheckbox.addMouseListener(buttonMouseHandler);
        nextImageAfterTaggingCheckbox.addMouseMotionListener(buttonMouseHandler);
        cropButton.addMouseListener(buttonMouseHandler);
        cropButton.addMouseMotionListener(buttonMouseHandler);
        deleteButton.addMouseListener(buttonMouseHandler);
        deleteButton.addMouseMotionListener(buttonMouseHandler);
        closeButton.addMouseListener(buttonMouseHandler);
        closeButton.addMouseMotionListener(buttonMouseHandler);
        add(displayModeButton);
        add(resetButton);
        add(specialTagButton);
        add(okTagButton);
        add(beautifullTagButton);
        add(artTagButton);
        add(saturationCombo);
        add(temperatureSlider);
        add(nextImageAfterTaggingCheckbox);
        add(cropButton);
        add(deleteButton);
        add(closeButton);
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
        if (MediaDeleteSupport.moveFileToTrash(file)) {
            finishInteraction();
        } else {
            finishInteraction();
            JOptionPane.showMessageDialog(this, "Datei konnte nicht in den Papierkorb verschoben werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
        }
        setOverlayVisible(false);
        repaint();
    }

    private void installTagButton(OverlayButton button, String tag) {
        button.setFocusable(false);
        button.setVisible(false);
        button.setCheckboxVisible(true);
        button.addActionListener(e -> {
            beginInteraction();
            boolean tagAdded = toggleTagForCurrentImage(tag);
            if (tagAdded && AppState.get().isNextImageAfterTagging()) {
                EventBus.get().publish(new UserKeyboardEvent(UserCommand.RIGHT));
            }
            finishInteraction();
        });
    }

    private void installNextImageAfterTaggingCheckbox() {
        nextImageAfterTaggingCheckbox.setFocusable(false);
        nextImageAfterTaggingCheckbox.setOpaque(true);
        nextImageAfterTaggingCheckbox.setBackground(new Color(245, 245, 245, 185));
        nextImageAfterTaggingCheckbox.setVisible(false);
        nextImageAfterTaggingCheckbox.addActionListener(e ->
                AppState.get().setNextImageAfterTagging(nextImageAfterTaggingCheckbox.isSelected()));
    }

    private void installSaturationCombo() {
        saturationCombo.setFocusable(false);
        saturationCombo.setVisible(false);
        saturationCombo.setToolTipText("Sättigung");
        saturationCombo.addActionListener(e -> {
            if (updatingSaturationCombo || file == null) return;
            Object selected = saturationCombo.getSelectedItem();
            if (!(selected instanceof ImageSaturationHandler.SaturationLevel level)) return;

            setSaturationLevel(level);
        });
        SaturationComboHoverSupport.install(
                saturationCombo,
                this::previewSaturationLevel,
                this::setSaturationLevel,
                this::restoreSaturationPreview
        );
    }

    private void installTemperatureSlider() {
        temperatureSlider.setFocusable(false);
        temperatureSlider.setVisible(false);
        temperatureSlider.setToolTipText("Farbtemperatur: links kühler, rechts wärmer");
        temperatureSlider.setMajorTickSpacing(5);
        temperatureSlider.setMinorTickSpacing(1);
        temperatureSlider.setPaintTicks(true);
        temperatureSlider.setSnapToTicks(true);
        temperatureSlider.addChangeListener(e -> {
            if (updatingTemperatureSlider || file == null) return;

            int temperature = temperatureSlider.getValue();
            previewTemperature(temperature);
            if (!temperatureSlider.getValueIsAdjusting()) {
                setTemperature(temperature);
            }
        });
    }

    private void applyImageEnhancements(ImageSaturationHandler.SaturationLevel saturationLevel, int temperature) {
        displayImage = ImageEnhancementUtils.applyEnhancements(image, saturationLevel, temperature);
    }

    private void previewSaturationLevel(ImageSaturationHandler.SaturationLevel level) {
        if (file == null) return;

        applyImageEnhancements(level, ImageTemperatureHandler.getInstance().getTemperatureForFile(file));
        showOverlayTemporarily();
        repaint();
    }

    private void restoreSaturationPreview(ImageSaturationHandler.SaturationLevel level) {
        if (file == null) return;

        updatingSaturationCombo = true;
        saturationCombo.setSelectedItem(level);
        updatingSaturationCombo = false;
        applyImageEnhancements(level, ImageTemperatureHandler.getInstance().getTemperatureForFile(file));
        repaint();
    }

    private void setSaturationLevel(ImageSaturationHandler.SaturationLevel level) {
        if (file == null) return;

        updatingSaturationCombo = true;
        saturationCombo.setSelectedItem(level);
        updatingSaturationCombo = false;
        ImageSaturationHandler.getInstance().setLevelForFile(file, level);
        applyImageEnhancements(level, ImageTemperatureHandler.getInstance().getTemperatureForFile(file));
        showOverlayTemporarily();
        repaint();
    }

    private void previewTemperature(int temperature) {
        if (file == null) return;

        applyImageEnhancements(ImageSaturationHandler.getInstance().getLevelForFile(file), temperature);
        keepOverlayVisibleForPreview();
        repaint();
    }

    private void setTemperature(int temperature) {
        if (file == null) return;

        updatingTemperatureSlider = true;
        temperatureSlider.setValue(temperature);
        updatingTemperatureSlider = false;
        ImageTemperatureHandler.getInstance().setTemperatureForFile(file, temperature);
        applyImageEnhancements(ImageSaturationHandler.getInstance().getLevelForFile(file), temperature);
        showOverlayTemporarily();
        repaint();
    }

    private void showDeletePopup(MouseEvent e) {
        if (file == null) return;
        JPopupMenu popup = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Bild löschen");
        deleteItem.addActionListener(a -> deleteCurrentImage());
        popup.add(deleteItem);
        JMenuItem cropItem = new JMenuItem("Neues Bild aus Auswahl");
        cropItem.setEnabled(zoomSelection != null);
        cropItem.addActionListener(a -> saveZoomSelectionAsNewImage());
        popup.add(cropItem);
        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    private void beginInteraction() {
        if (onInteractionStarted != null) {
            onInteractionStarted.run();
        }
    }

    private void finishInteraction() {
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

    private void keepOverlayVisibleForPreview() {
        if (!overlayVisible) {
            setOverlayVisible(file != null);
        }
        overlayHideTimer.restart();
    }

    private void hideOverlayIfPointerIsAway() {
        if (isPointerOverOverlayMenu()) {
            overlayHideTimer.restart();
            return;
        }
        setOverlayVisible(false);
    }

    private boolean isPointerOverOverlayMenu() {
        if (!overlayVisible || !isShowing()) return false;

        Rectangle bounds = getVisibleMenuBounds();
        if (bounds == null) return false;

        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo == null) return false;

        Point point = pointerInfo.getLocation();
        SwingUtilities.convertPointFromScreen(point, this);
        bounds.grow(6, 6);
        return bounds.contains(point);
    }

    private boolean toggleTagForCurrentImage(String tag) {
        if (file == null) return false;

        List<String> tags = new ArrayList<>(TagHandler.getInstance().getTagsForFile(file.getName()));
        boolean tagAdded;
        if (tags.contains(tag)) {
            tags.removeIf(tag::equals);
            tagAdded = false;
        } else {
            tags.add(tag);
            tagAdded = true;
        }
        TagHandler.getInstance().setTagsToFile(tags, file.getName());
        EventBus.get().publish(new TagsChangedEvent());
        showOverlayTemporarily();
        return tagAdded;
    }

    private boolean hasTag(String tag) {
        return file != null && TagHandler.getInstance().getTagsForFile(file.getName()).contains(tag);
    }

    private void updateTagButton(OverlayButton button, String tag, boolean visible) {
        button.setVisible(visible && file != null);
        button.setTextColor(Color.BLACK);
        button.setChecked(hasTag(tag));
    }

    private void updateSaturationCombo(boolean visible) {
        saturationCombo.setVisible(visible && file != null);
        updatingSaturationCombo = true;
        saturationCombo.setSelectedItem(ImageSaturationHandler.getInstance().getLevelForFile(file));
        updatingSaturationCombo = false;
    }

    private void updateTemperatureSlider(boolean visible) {
        temperatureSlider.setVisible(visible && file != null);
        updatingTemperatureSlider = true;
        temperatureSlider.setValue(ImageTemperatureHandler.getInstance().getTemperatureForFile(file));
        updatingTemperatureSlider = false;
    }

    private void updateNextImageAfterTaggingCheckbox(boolean visible) {
        nextImageAfterTaggingCheckbox.setVisible(visible && file != null);
        nextImageAfterTaggingCheckbox.setSelected(AppState.get().isNextImageAfterTagging());
    }

    private void setOverlayVisible(boolean visible) {
        overlayVisible = visible;
        boolean hasZoom = zoomSelection != null;
        displayModeButton.setVisible(visible && file != null);
        displayModeButton.setEnabled(hasZoom);
        updateDisplayModeButton();
        resetButton.setVisible(visible && file != null);
        resetButton.setEnabled(hasZoom);
        updateTagButton(specialTagButton, SPECIAL_TAG, visible);
        updateTagButton(okTagButton, OK_TAG, visible);
        updateTagButton(beautifullTagButton, BEAUTIFULL_TAG, visible);
        updateTagButton(artTagButton, ART_TAG, visible);
        updateSaturationCombo(visible);
        updateTemperatureSlider(visible);
        updateNextImageAfterTaggingCheckbox(visible);
        cropButton.setVisible(visible && file != null);
        cropButton.setEnabled(hasZoom);
        deleteButton.setVisible(visible && file != null);
        closeButton.setVisible(visible && file != null);
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

    private void closeMediaView() {
        setOverlayVisible(false);
        Controller.getInstance().getControlPanel().getSlideshowManager().stop();
        MediaView.getInstance().stopAndHide();
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

    private void saveZoomSelectionAsNewImage() {
        if (file == null || zoomSelection == null) return;

        Rectangle2D viewRect = getZoomViewRect(zoomSelection);
        Rectangle cropRect = new Rectangle(
                clamp((int) Math.floor(viewRect.getX()), 0, image.getWidth()),
                clamp((int) Math.floor(viewRect.getY()), 0, image.getHeight()),
                clamp((int) Math.ceil(viewRect.getWidth()), 1, image.getWidth()),
                clamp((int) Math.ceil(viewRect.getHeight()), 1, image.getHeight())
        );
        if (cropRect.x + cropRect.width > image.getWidth()) {
            cropRect.width = image.getWidth() - cropRect.x;
        }
        if (cropRect.y + cropRect.height > image.getHeight()) {
            cropRect.height = image.getHeight() - cropRect.y;
        }
        if (cropRect.width <= 0 || cropRect.height <= 0) return;

        beginInteraction();
        cropButton.setEnabled(false);
        File sourceFile = file;
        BufferedImage sourceImage = image;
        Controller.getInstance().getExecutorService().submit(() -> {
            try {
                writeCroppedImage(sourceFile, sourceImage, cropRect);
                ImageZoomHandler.getInstance().resetZoomForFile(sourceFile);
                AppState.get().setCurrentFile(sourceFile);
                Controller.getInstance().getThumbnailPanel().reloadDirectory();
                SwingUtilities.invokeLater(() -> Controller.getInstance().handleMedia(sourceFile, false));
            } catch (IOException ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this,
                        "Bild konnte nicht gespeichert werden:\n" + ex.getMessage(),
                        "Neues Bild aus Auswahl",
                        JOptionPane.ERROR_MESSAGE
                ));
            } finally {
                SwingUtilities.invokeLater(() -> {
                    cropButton.setEnabled(zoomSelection != null);
                    showOverlayTemporarily();
                    finishInteraction();
                });
            }
        });
    }

    private File writeCroppedImage(File sourceFile, BufferedImage sourceImage, Rectangle cropRect) throws IOException {
        BufferedImage crop = sourceImage.getSubimage(cropRect.x, cropRect.y, cropRect.width, cropRect.height);
        String format = crop.getColorModel().hasAlpha() ? "png" : "jpg";
        Path targetPath = uniqueCropPath(sourceFile.toPath(), format);
        BufferedImage output = crop;
        if ("jpg".equals(format)) {
            output = new BufferedImage(crop.getWidth(), crop.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = output.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, output.getWidth(), output.getHeight());
            g.drawImage(crop, 0, 0, null);
            g.dispose();
        }

        if (!ImageIO.write(output, format, targetPath.toFile())) {
            throw new IOException("Kein ImageIO-Writer fuer " + format + " gefunden.");
        }
        placeCropNextToOriginal(sourceFile.toPath(), targetPath);
        return targetPath.toFile();
    }

    private Path uniqueCropPath(Path sourcePath, String extension) {
        Path parent = sourcePath.getParent();
        String sourceName = sourcePath.getFileName().toString();
        int dotIndex = sourceName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? sourceName.substring(0, dotIndex) : sourceName;

        int counter = 0;
        Path candidate;
        do {
            String suffix = counter == 0 ? "_ausschnitt" : "_ausschnitt_" + counter;
            candidate = parent.resolve(baseName + suffix + "." + extension);
            counter++;
        } while (Files.exists(candidate));
        return candidate;
    }

    private void placeCropNextToOriginal(Path sourcePath, Path cropPath) {
        try {
            BasicFileAttributes sourceAttributes = Files.readAttributes(sourcePath, BasicFileAttributes.class);
            FileTime creationTime = sourceAttributes.creationTime();
            FileTime modifiedTime = sourceAttributes.lastModifiedTime();
            BasicFileAttributeView view = Files.getFileAttributeView(cropPath, BasicFileAttributeView.class);
            if (view != null) {
                view.setTimes(modifiedTime, sourceAttributes.lastAccessTime(), creationTime);
            } else {
                Files.setLastModifiedTime(cropPath, modifiedTime);
            }
        } catch (IOException e) {
            try {
                Files.setLastModifiedTime(cropPath, Files.getLastModifiedTime(sourcePath));
            } catch (IOException ignored) {
                // Die Sortierung gruppiert _ausschnitt-Dateien trotzdem neben dem Original.
            }
        }
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

        int targetWidth = getWidth() > 0 ? getWidth() : baseWidth;
        int targetHeight = getHeight() > 0 ? getHeight() : baseHeight;

        Rectangle2D viewRect = getBaseViewRect(targetWidth, targetHeight);
        double zoom = Math.max(
                targetWidth / viewRect.getWidth(),
                targetHeight / viewRect.getHeight()
        ) * BASE_SCALE_MULTIPLIER;

        int iw = (int) (image.getWidth() * zoom);
        int ih = (int) (image.getHeight() * zoom);

        int baseX = (int) Math.round(-viewRect.getX() * zoom);
        int baseY = (int) Math.round(-viewRect.getY() * zoom);

        int x = clamp(baseX, targetWidth - iw, 0);
        int y = clamp(baseY, targetHeight - ih, 0);
        renderedImageBounds = new Rectangle2D.Double(x, y, iw, ih);

        g2.drawImage(displayImage, x, y, iw, ih, null);

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
        Rectangle bounds = getVisibleMenuBounds();
        if (bounds == null) return;
        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRoundRect(bounds.x - 6, bounds.y - 6, bounds.width + 12, bounds.height + 12, 8, 8);
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

    private Rectangle2D getZoomViewRect(ImageZoomHandler.ZoomSelection zoomSelection) {
        double selectedX = clamp(zoomSelection.x(), 0, 1) * image.getWidth();
        double selectedY = clamp(zoomSelection.y(), 0, 1) * image.getHeight();
        double selectedWidth = clamp(zoomSelection.width(), 0, 1) * image.getWidth();
        double selectedHeight = clamp(zoomSelection.height(), 0, 1) * image.getHeight();

        if (selectedWidth <= 0 || selectedHeight <= 0 || getWidth() <= 0 || getHeight() <= 0) {
            return new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight());
        }

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

    @Override
    public void doLayout() {
        super.doLayout();
        Dimension menuSize = getOverlayMenuSize();
        int x = Math.max(MENU_MARGIN, getWidth() - menuSize.width - MENU_MARGIN);
        int y = MENU_MARGIN;
        displayModeButton.setBounds(x, y, OVERLAY_BUTTON_WIDTH, MENU_HEIGHT);
        resetButton.setBounds(x + OVERLAY_BUTTON_WIDTH + MENU_GAP, y, OVERLAY_BUTTON_WIDTH, MENU_HEIGHT);
        specialTagButton.setBounds(
                x,
                y + MENU_HEIGHT + MENU_GAP,
                OVERLAY_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        okTagButton.setBounds(
                x + OVERLAY_BUTTON_WIDTH + MENU_GAP,
                y + MENU_HEIGHT + MENU_GAP,
                OVERLAY_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        beautifullTagButton.setBounds(
                x,
                y + (MENU_HEIGHT + MENU_GAP) * 2,
                OVERLAY_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        artTagButton.setBounds(
                x + OVERLAY_BUTTON_WIDTH + MENU_GAP,
                y + (MENU_HEIGHT + MENU_GAP) * 2,
                OVERLAY_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        nextImageAfterTaggingCheckbox.setBounds(
                x,
                y + (MENU_HEIGHT + MENU_GAP) * 3,
                OVERLAY_BUTTON_WIDTH * 2 + MENU_GAP,
                MENU_HEIGHT
        );
        saturationCombo.setBounds(
                x,
                y + (MENU_HEIGHT + MENU_GAP) * 4,
                OVERLAY_BUTTON_WIDTH * 2 + MENU_GAP,
                MENU_HEIGHT
        );
        temperatureSlider.setBounds(
                x,
                y + (MENU_HEIGHT + MENU_GAP) * 5,
                OVERLAY_BUTTON_WIDTH * 2 + MENU_GAP,
                MENU_HEIGHT
        );
        cropButton.setBounds(
                x,
                y + (MENU_HEIGHT + MENU_GAP) * 6,
                OVERLAY_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        deleteButton.setBounds(
                x + OVERLAY_BUTTON_WIDTH + MENU_GAP,
                y + (MENU_HEIGHT + MENU_GAP) * 6,
                OVERLAY_BUTTON_WIDTH,
                MENU_HEIGHT
        );
        closeButton.setBounds(
                x,
                y + (MENU_HEIGHT + MENU_GAP) * 7,
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
        int height = MENU_HEIGHT * 8 + MENU_GAP * 7;
        return new Dimension(width, height);
    }

    private Rectangle getVisibleMenuBounds() {
        JComponent[] buttons = {
                displayModeButton,
                resetButton,
                specialTagButton,
                okTagButton,
                beautifullTagButton,
                artTagButton,
                saturationCombo,
                temperatureSlider,
                nextImageAfterTaggingCheckbox,
                cropButton,
                deleteButton,
                closeButton
        };
        Rectangle result = null;
        for (JComponent component : buttons) {
            if (!component.isVisible()) continue;
            result = result == null ? component.getBounds() : result.union(component.getBounds());
        }
        return result;
    }

}
