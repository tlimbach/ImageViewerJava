package ui;

import event.*;
import model.AppState;
import service.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThumbnailPanel extends JPanel {

    private final static int THUMBNAIL_WIDTH = 420;
    private final static int THUMBNAIL_HEIGHT = (int) (THUMBNAIL_WIDTH * 9.0 / 16);  // ≈ 265
    private final static int PREVIEW_IMAGE_WIDTH = THUMBNAIL_WIDTH;
    private final static int PREVIEW_IMAGE_HEIGHT = THUMBNAIL_HEIGHT;

    private final static int ANIMATION_FRAMES_PER_THUMBNAIL = 50;
    public final static int ANIMATION_DELAY_PLAYBACK = (int) (33 * 2.5);
    private final static int ANIMATION_DELAY_RECORD = 33;
    public static final int N_THREADS = 4;
    private JLabel selectedLabel = null;
    private boolean pendingScrollToSelectedThumbnail;

    private final JScrollPane scrollPane;
    private final InitialLoadOverlay initialLoadOverlay = new InitialLoadOverlay();

    private final MouseListener mouseListener;

    private volatile File currentHoverFile;

    private JLabel myLabel;
    private static final int INITIAL_VIDEO_THUMBNAIL_FRAMES = 1;
    private static final int PRELOAD_FRAMES_FOR_NEW_VIDEO_THUMBNAIL = 3;
    private static final int INITIAL_VISIBLE_PRIORITY_COUNT = 60;
    private static final int RESTORED_SELECTION_PRIORITY_RADIUS = 45;
    private static final int THUMBNAIL_LOAD_BATCH_SIZE = 8;
    private static final int MAX_BACKGROUND_THUMBNAIL_LOADS = 8;
    private JPanel pendingRefreshPanel;
    private final Timer thumbnailUiRefreshTimer = new Timer(80, e -> flushThumbnailUiRefresh());
    private final Timer thumbnailLoadQueueTimer = new Timer(80, e -> drainThumbnailLoadQueue());
    private final TransferHandler fileDropTransferHandler = createFileDropTransferHandler();
    private static final Pattern HTML_IMAGE_SRC_PATTERN = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final String ZOOM_VISIBLE_RECT_PROPERTY = "zoomVisibleRect";
    private static final String ZOOM_IMAGE_BOUNDS_PROPERTY = "zoomImageBounds";
    private static final String ZOOM_IMAGE_SIZE_PROPERTY = "zoomImageSize";
    private static final String ZOOM_RENDER_IMAGE_PROPERTY = "zoomRenderImage";
    private static final int THUMBNAIL_PAN_START_DISTANCE = 5;
    private static final double THUMBNAIL_ZOOM_TOGGLE_WIDTH_RATIO = 0.20;
    private static final int THUMBNAIL_ZOOM_TOGGLE_ANIMATION_DELAY_MS = 16;
    private static final float THUMBNAIL_ZOOM_TOGGLE_ANIMATION_STEP = 0.18f;
    private static final float[] MARCHING_ANTS_DASH = {7f, 7f};

    private JLabel zoomDragLabel;
    private File zoomDragFile;
    private Point zoomDragStartPoint;
    private ImageZoomHandler.ZoomSelection zoomDragStartSelection;
    private Dimension zoomDragImageSize;
    private Rectangle2D zoomDragImageBounds;
    private boolean zoomDragStarted;
    private JLabel thumbnailPressLabel;
    private File thumbnailPressFile;
    private Point thumbnailPressPoint;
    private int thumbnailPressClickCount;
    private float marchingAntsPhase;
    private final Timer marchingAntsTimer = new Timer(220, e -> repaintMarchingAntsThumbnails());
    private final Timer zoomToggleAnimationTimer = new Timer(
            THUMBNAIL_ZOOM_TOGGLE_ANIMATION_DELAY_MS,
            e -> animateThumbnailZoomToggles()
    );

    public ThumbnailPanel() {


        mouseListener = createMouseListener();
        thumbnailUiRefreshTimer.setRepeats(false);
        marchingAntsTimer.start();
        zoomToggleAnimationTimer.start();

        setLayout(new BorderLayout());
        scrollPane = new JScrollPane();
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.getViewport().addChangeListener(e -> updateVisibleThumbnails());
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);
        installFileDropHandler(this);
        installFileDropHandler(scrollPane);
        installFileDropHandler(scrollPane.getViewport());

        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> {
            Controller.getInstance().getExecutorService().submit(() -> {
                List<File> files = MediaService.getInstance().loadFilesFromDirectory();
                populate(files);
            });
        });

        EventBus.get().register(RangeChangedEvent.class, e -> {
            invalidateThumbnails(e.file());

            SwingUtilities.invokeLater(() -> {
                AnimatedThumbnail match = animatedThumbnails.stream().filter(a -> a.filename.equals(e.file().getName())).findFirst().orElse(null);

                if (match != null) {
                    CompletableFuture.supplyAsync(() -> loadThumbnails(e.file(), ANIMATION_FRAMES_PER_THUMBNAIL), Controller.getInstance().getExecutorService()).thenAccept(thumbFiles -> {
                        if (thumbFiles != null && !thumbFiles.isEmpty()) {
                            SwingUtilities.invokeLater(() -> {
                                if (!animatedThumbnails.contains(match)) return;

                                boolean wasRunning = match.isRunning;
                                if (wasRunning) {
                                    match.stop();
                                }

                                match.imageFiles = thumbFiles;
                                match.fullFrameListRequested = true;
                                match.fullFrameListLoaded = true;

                                if (wasRunning) {
                                    match.start();
                                    match.preload(PRELOAD_FRAMES_FOR_NEW_VIDEO_THUMBNAIL);
                                }
                            });
                        }
                    });
                }
            });
        });

        EventBus.get().register(ImageZoomChangedEvent.class, e -> refreshImageThumbnail(e.file()));
        EventBus.get().register(ImageZoomPreviewEvent.class, e -> previewImageThumbnail(e.file(), e.zoom()));
        EventBus.get().register(MediaFileDeletedEvent.class, e -> removeThumbnailForFile(e.file()));

        EventBus.get().register(RotationChangedEvent.class, e -> {
            CompletableFuture.supplyAsync(() -> {
                try {
                    BufferedImage original = ImageIO.read(e.file());
                    if (original == null) return null;
                    BufferedImage rotated = H.rotate(original, e.degrees());
                    return getScaledImagePreserveRatio(rotated, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    return null;
                }
            }, Controller.getInstance().getExecutorService()).thenAccept(scaled -> {
                if (scaled == null) return;
                SwingUtilities.invokeLater(() -> {
                    for (AnimatedThumbnail thumb : animatedThumbnails) {
                        if (thumb.filename.equals(e.file().getName())) {
                            thumb.label.setIcon(new ImageIcon(scaled));
                            break;
                        }
                    }
                });
            });
        });

        EventBus.get().register(UserKeyboardEvent.class, e -> {
            SwingUtilities.invokeLater(() -> handleKeyboardCommand(e.command()));
        });

        EventBus.get().register(MediaviewPlayEvent.class, e -> {
            SwingUtilities.invokeLater(this::selectCurrentFileThumbnail);
        });
    }

    private void handleKeyboardCommand(UserCommand command) {
        if (myLabel == null) return;  // Falls noch nie eins geklickt

        // Aktuellen Index suchen
        int index = -1;
        for (int i = 0; i < animatedThumbnails.size(); i++) {
            if (animatedThumbnails.get(i).label == myLabel) {
                index = i;
                break;
            }
        }

        int nextIndex = index; // Default: unverändert

        switch (command) {
            case RIGHT:
                if (index != -1 && index + 1 < animatedThumbnails.size()) {
                    nextIndex = index + 1;
                }
                break;
            case LEFT:
                if (index > 0) {
                    nextIndex = index - 1;
                }
                break;
            case UP:
                if (index - 3 >= 0) {
                    nextIndex = index - 3;
                }
                break;
            case DOWN:
                if (index + 3 < animatedThumbnails.size()) {
                    nextIndex = index + 3;
                }
                break;
        }


        if (nextIndex != index) {
            JLabel next = animatedThumbnails.get(nextIndex).label;

            if (selectedLabel != null) {
                selectedLabel.setBorder(null);
            }

            selectedLabel = next;
            selectedLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 4));

            File file = (File) next.getClientProperty("file");
            AppState.get().setCurrentFile(file);
            Controller.getInstance().handleMedia(file, false);

            myLabel = selectedLabel;

            Rectangle r = selectedLabel.getBounds();
            Rectangle viewRect = SwingUtilities.convertRectangle(
                    selectedLabel.getParent(), r, scrollPane.getViewport());
            scrollPane.getViewport().scrollRectToVisible(viewRect);


        }
        Rectangle r = myLabel.getBounds();
        Rectangle viewRect = SwingUtilities.convertRectangle(myLabel.getParent(), r, scrollPane.getViewport());
        viewRect.y = Math.max(viewRect.y - 50, 0);
        viewRect.height += 100;
        scrollPane.scrollRectToVisible(viewRect);
    }

    private void selectCurrentFileThumbnail() {
        File current = AppState.get().getCurrentFile();
        if (current == null) return;

        selectFileThumbnail(current);
    }

    public void selectFileThumbnail(File file) {
        if (file == null) return;

        runOnEdt(() -> {
            for (AnimatedThumbnail thumb : animatedThumbnails) {
                File thumbFile = (File) thumb.label.getClientProperty("file");
                if (thumbFile != null && thumbFile.getName().equals(file.getName())) {
                    selectThumbnailLabel(thumb.label, true);
                    break;
                }
            }
        });
    }

    private void selectThumbnailLabel(JLabel label, boolean scrollToVisible) {
        if (label == null) return;

        if (selectedLabel != null && selectedLabel != label) {
            selectedLabel.setBorder(null);
        }

        selectedLabel = label;
        selectedLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 4));
        myLabel = selectedLabel;

        File file = (File) selectedLabel.getClientProperty("file");
        if (file != null) {
            AppState.get().setCurrentFile(file);
            SettingsService.getIntance().storeLastSelectedFile(AppState.get().getCurrentDirectory(), file);
        }

        if (scrollToVisible) {
            pendingScrollToSelectedThumbnail = true;
            scrollSelectedThumbnailToVisible();
        }
    }

    private void scrollSelectedThumbnailToVisible() {
        if (!pendingScrollToSelectedThumbnail || selectedLabel == null || selectedLabel.getParent() == null) return;
        if (selectedLabel.getWidth() <= 0 || selectedLabel.getHeight() <= 0) return;

        pendingScrollToSelectedThumbnail = false;
        scrollLabelToVisible(selectedLabel);
    }

    private void scrollLabelToVisible(JLabel label) {
        if (label == null || label.getParent() == null) return;
        if (label.getWidth() <= 0 || label.getHeight() <= 0) return;

        Rectangle r = label.getBounds();
        Rectangle viewRect = SwingUtilities.convertRectangle(label.getParent(), r, scrollPane.getViewport());
        viewRect.y = Math.max(viewRect.y - 50, 0);
        viewRect.height += 100;
        scrollPane.getViewport().scrollRectToVisible(viewRect);
    }

    private MouseAdapter createMouseListener() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                JLabel label = (JLabel) e.getSource();
                myLabel = label;
                File file = (File) label.getClientProperty("file");
                thumbnailPressLabel = label;
                thumbnailPressFile = file;
                thumbnailPressPoint = e.getPoint();
                thumbnailPressClickCount = e.getClickCount();

                if (SwingUtilities.isRightMouseButton(e)) {
                    clearThumbnailPress();
                    // Kontextmenü wie gehabt
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem deleteItem = new JMenuItem("Bild löschen");

                    deleteItem.addActionListener(ev -> {
                        if (MediaDeleteSupport.moveFileToTrash(file)) {
                            removeThumbnail(label);
                        } else {
                            JOptionPane.showMessageDialog(label, "Datei konnte nicht in den Papierkorb verschoben werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
                        }
                    });

                    popup.add(deleteItem);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                    return; // Rechtsklick fertig
                }

                prepareThumbnailZoomDrag(label, file, e);

                selectThumbnailLabel(label, false);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (zoomDragLabel == null || zoomDragFile == null || zoomDragStartSelection == null) return;
                if (!zoomDragStarted && zoomDragStartPoint.distance(e.getPoint()) < THUMBNAIL_PAN_START_DISTANCE) return;
                zoomDragStarted = true;
                updateThumbnailZoomDrag(e.getPoint(), false);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (zoomDragLabel != null && zoomDragStarted) {
                    updateThumbnailZoomDrag(e.getPoint(), true);
                    clearThumbnailZoomDrag();
                    clearThumbnailPress();
                    return;
                }

                clearThumbnailZoomDrag();

                if (thumbnailPressLabel != null && thumbnailPressFile != null && isThumbnailClick(e)) {
                    if (isPointInThumbnailZoomToggle(thumbnailPressLabel, e.getPoint())) {
                        performThumbnailZoomToggle(thumbnailPressFile);
                    } else {
                        performThumbnailClick(thumbnailPressFile, thumbnailPressClickCount);
                    }
                }
                clearThumbnailPress();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                JLabel label = (JLabel) e.getSource();
                boolean overZoomToggle = isPointInThumbnailZoomToggle(label, e.getPoint());
                updateThumbnailZoomToggleHover(label, overZoomToggle);
                if (overZoomToggle || isPointInThumbnailZoomRect(label, e.getPoint())) {
                    label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    label.setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                JLabel label = (JLabel) e.getSource();
                updateThumbnailZoomToggleHover(label, false);
                label.setCursor(Cursor.getDefaultCursor());
            }
        };
    }

    private boolean isThumbnailClick(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e) || thumbnailPressPoint == null) return false;
        return thumbnailPressPoint.distance(e.getPoint()) < THUMBNAIL_PAN_START_DISTANCE;
    }

    private void performThumbnailClick(File file, int clickCount) {
        if (clickCount >= 2) {
            AppState.get().setCurrentFile(file);
            Controller.getInstance().handleMedia(file, true);
            return;
        }

        H.out("once pressed " + file.getName());
        if (Controller.isImageFile(file) && MediaView.getInstance().isShowingImage(file)) {
            MediaView.getInstance().hideFrame();
            return;
        }
        AppState.get().setCurrentFile(file);
        Controller.getInstance().handleMedia(file, false);
    }

    private void performThumbnailZoomToggle(File file) {
        if (file == null || !Controller.isImageFile(file)) return;
        if (ImageZoomHandler.getInstance().getZoomForFile(file) == null) return;

        AppState.get().setCurrentFile(file);
        MediaView.getInstance().toggleImageDisplayMode(file);
    }

    private void clearThumbnailPress() {
        thumbnailPressLabel = null;
        thumbnailPressFile = null;
        thumbnailPressPoint = null;
        thumbnailPressClickCount = 0;
    }

    private void removeThumbnail(JLabel label) {
        int removedIndex = -1;
        AnimatedThumbnail removedThumbnail = null;
        for (int i = 0; i < animatedThumbnails.size(); i++) {
            AnimatedThumbnail thumbnail = animatedThumbnails.get(i);
            if (thumbnail.label == label) {
                removedIndex = i;
                removedThumbnail = thumbnail;
                break;
            }
        }

        if (removedIndex == -1) return;

        removedThumbnail.stop();
        animatedThumbnails.remove(removedIndex);

        Container parent = label.getParent();
        if (parent != null) {
            parent.remove(label);
            parent.revalidate();
            parent.repaint();
        }

        if (selectedLabel == label || myLabel == label) {
            selectedLabel = null;
            myLabel = null;

            if (!animatedThumbnails.isEmpty()) {
                int nextIndex = Math.min(removedIndex, animatedThumbnails.size() - 1);
                JLabel next = animatedThumbnails.get(nextIndex).label;
                selectedLabel = next;
                myLabel = next;
                selectedLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 4));

                File nextFile = (File) next.getClientProperty("file");
                if (nextFile != null) {
                    AppState.get().setCurrentFile(nextFile);
                    Controller.getInstance().handleMedia(nextFile, false);
                }
            }
        }

        updateVisibleThumbnails();
    }

    private void removeThumbnailForFile(File file) {
        if (file == null) return;
        runOnEdt(() -> {
            for (AnimatedThumbnail thumbnail : new ArrayList<>(animatedThumbnails)) {
                if (file.getName().equals(thumbnail.filename)) {
                    removeThumbnail(thumbnail.label);
                    return;
                }
            }
        });
    }

    private List<File> loadImageThumbnail(File imageFile) {
        List<File> result = new ArrayList<>();
        if (imageFile == null || !imageFile.isFile() || !imageFile.canRead()) {
            System.err.println("[ThumbnailPanel] Bilddatei nicht lesbar, Thumbnail wird übersprungen: "
                    + (imageFile == null ? "null" : imageFile.getAbsolutePath()));
            return result;
        }

        File thumbDir = getThumbnailCacheDir();
        String thumbName = "thumb_" + imageFile.getName() + ".jpg";
        File thumbFile = new File(thumbDir, thumbName);

        if (!thumbFile.exists()) {
            try {
                BufferedImage original;
                if (imageFile.getName().toLowerCase().endsWith(".mpo")) {
                    original = MpoReader.getLeftFrame(imageFile);
                } else {
                    original = ImageIO.read(imageFile);
                }
                if (original == null) return result;

                Image scaled = getScaledImagePreserveRatio(original, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
                BufferedImage resultImg = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = resultImg.createGraphics();
                g2.setColor(Color.DARK_GRAY);
                g2.fillRect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
                g2.drawImage(scaled, 0, 0, null);
                g2.dispose();

                ImageIO.write(resultImg, "jpg", thumbFile);
            } catch (IOException e) {
                System.err.println("[ThumbnailPanel] Thumbnail konnte nicht geladen werden: "
                        + imageFile.getAbsolutePath() + " (" + e.getMessage() + ")");
            }
        }

        result.add(thumbFile);
        return result;
    }

    private void refreshImageThumbnail(File file) {
        if (file == null) return;

        for (AnimatedThumbnail thumbnail : new ArrayList<>(animatedThumbnails)) {
            if (!file.getName().equals(thumbnail.filename)) continue;
            JLabel label = thumbnail.label;
            if (label == null) continue;

            CompletableFuture
                    .supplyAsync(() -> createImageThumbnailIcon(file), Controller.getInstance().getExecutorService())
                    .thenAccept(result -> {
                        if (result == null) return;
                        SwingUtilities.invokeLater(() -> {
                            if (animatedThumbnails.contains(thumbnail)) {
                                applyThumbnailRenderResult(label, result);
                            }
                        });
                    });
        }
    }

    private void previewImageThumbnail(File file, ImageZoomHandler.ZoomSelection zoom) {
        if (file == null || zoom == null) return;
        if (Controller.getInstance().getControlPanel().getThumbnailZoomMode() != ThumbnailZoomMode.GRAYED_OUT) return;

        for (AnimatedThumbnail thumbnail : new ArrayList<>(animatedThumbnails)) {
            if (!file.getName().equals(thumbnail.filename)) continue;
            JLabel label = thumbnail.label;
            if (label == null) continue;

            SwingUtilities.invokeLater(() -> {
                Object image = label.getClientProperty(ZOOM_RENDER_IMAGE_PROPERTY);
                Object imageSize = label.getClientProperty(ZOOM_IMAGE_SIZE_PROPERTY);
                Object imageBounds = label.getClientProperty(ZOOM_IMAGE_BOUNDS_PROPERTY);
                if (!(image instanceof BufferedImage baseThumbnail)
                        || !(imageSize instanceof Dimension size)
                        || !(imageBounds instanceof Rectangle2D bounds)) {
                    return;
                }

                BufferedImage preview = copyImage(baseThumbnail);
                Rectangle2D visibleRect = paintGrayedOutZoomMask(preview, size, zoom);
                applyThumbnailRenderResult(label, new ThumbnailRenderResult(
                        new MarchingAntsIcon(preview, visibleRect),
                        visibleRect,
                        bounds,
                        size,
                        baseThumbnail
                ));
            });
            return;
        }
    }

    private void applyThumbnailRenderResult(JLabel label, ThumbnailRenderResult result) {
        label.setIcon(result.icon());
        label.putClientProperty(ZOOM_VISIBLE_RECT_PROPERTY, result.visibleRect());
        label.putClientProperty(ZOOM_IMAGE_BOUNDS_PROPERTY, result.imageBounds());
        label.putClientProperty(ZOOM_IMAGE_SIZE_PROPERTY, result.imageSize());
        label.putClientProperty(ZOOM_RENDER_IMAGE_PROPERTY, result.renderImage());
    }

    private void prepareThumbnailZoomDrag(JLabel label, File file, MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        if (Controller.getInstance().getControlPanel().getThumbnailZoomMode() != ThumbnailZoomMode.GRAYED_OUT) return;
        if (!Controller.isImageFile(file)) return;

        ImageZoomHandler.ZoomSelection zoom = ImageZoomHandler.getInstance().getZoomForFile(file);
        if (zoom == null || !isPointInThumbnailZoomRect(label, e.getPoint())) return;

        Dimension imageSize = (Dimension) label.getClientProperty(ZOOM_IMAGE_SIZE_PROPERTY);
        Rectangle2D imageBounds = (Rectangle2D) label.getClientProperty(ZOOM_IMAGE_BOUNDS_PROPERTY);
        if (imageSize == null || imageBounds == null) return;

        zoomDragLabel = label;
        zoomDragFile = file;
        zoomDragStartPoint = e.getPoint();
        zoomDragStartSelection = zoom;
        zoomDragImageSize = imageSize;
        zoomDragImageBounds = imageBounds;
        zoomDragStarted = false;
        label.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }

    private void updateThumbnailZoomDrag(Point point, boolean persist) {
        if (zoomDragImageSize == null || zoomDragImageBounds == null || zoomDragStartPoint == null) return;

        double dxImage = (point.x - zoomDragStartPoint.x) * zoomDragImageSize.getWidth() / zoomDragImageBounds.getWidth();
        double dyImage = (point.y - zoomDragStartPoint.y) * zoomDragImageSize.getHeight() / zoomDragImageBounds.getHeight();

        double newX = clamp(
                zoomDragStartSelection.x() + dxImage / zoomDragImageSize.getWidth(),
                0,
                1 - zoomDragStartSelection.width()
        );
        double newY = clamp(
                zoomDragStartSelection.y() + dyImage / zoomDragImageSize.getHeight(),
                0,
                1 - zoomDragStartSelection.height()
        );

        ImageZoomHandler.ZoomSelection updated = new ImageZoomHandler.ZoomSelection(
                newX,
                newY,
                zoomDragStartSelection.width(),
                zoomDragStartSelection.height()
        );

        previewThumbnailZoom(updated);
        EventBus.get().publish(new ImageZoomPreviewEvent(zoomDragFile, updated));

        if (persist) {
            ImageZoomHandler.getInstance().setZoomForFile(zoomDragFile, updated);
        }
    }

    private void previewThumbnailZoom(ImageZoomHandler.ZoomSelection zoom) {
        if (zoomDragLabel == null) return;
        Object image = zoomDragLabel.getClientProperty(ZOOM_RENDER_IMAGE_PROPERTY);
        if (!(image instanceof BufferedImage baseThumbnail)) return;

        BufferedImage preview = copyImage(baseThumbnail);
        paintGrayedOutZoomMask(preview, zoomDragImageSize, zoom);
        Rectangle2D visibleRect = getVisibleZoomRectInThumbnail(zoomDragImageSize, zoom);
        ThumbnailRenderResult result = new ThumbnailRenderResult(
                new MarchingAntsIcon(preview, visibleRect),
                visibleRect,
                zoomDragImageBounds,
                zoomDragImageSize,
                baseThumbnail
        );
        applyThumbnailRenderResult(zoomDragLabel, result);
    }

    private boolean isPointInThumbnailZoomRect(JLabel label, Point point) {
        if (Controller.getInstance().getControlPanel().getThumbnailZoomMode() != ThumbnailZoomMode.GRAYED_OUT) return false;
        Object rect = label.getClientProperty(ZOOM_VISIBLE_RECT_PROPERTY);
        return rect instanceof Rectangle2D visibleRect && visibleRect.contains(point);
    }

    private boolean isPointInThumbnailZoomToggle(JLabel label, Point point) {
        File file = (File) label.getClientProperty("file");
        if (file == null || !Controller.isImageFile(file)) return false;
        if (ImageZoomHandler.getInstance().getZoomForFile(file) == null) return false;

        return thumbnailZoomToggleBounds(label).contains(point);
    }

    private void updateThumbnailZoomToggleHover(JLabel label, boolean hovered) {
        if (label instanceof ThumbnailLabel thumbnailLabel) {
            thumbnailLabel.setZoomToggleHovered(hovered);
        }
    }

    private void animateThumbnailZoomToggles() {
        boolean needsNextTick = false;
        for (AnimatedThumbnail thumb : new ArrayList<>(animatedThumbnails)) {
            if (thumb.label instanceof ThumbnailLabel thumbnailLabel) {
                needsNextTick |= thumbnailLabel.animateZoomToggle();
            }
        }
        if (!needsNextTick) {
            zoomToggleAnimationTimer.stop();
        }
    }

    private void requestZoomToggleAnimation() {
        if (!zoomToggleAnimationTimer.isRunning()) {
            zoomToggleAnimationTimer.start();
        }
    }

    private Rectangle thumbnailZoomToggleBounds(JLabel label) {
        int width = Math.max(1, (int) Math.round(label.getWidth() * THUMBNAIL_ZOOM_TOGGLE_WIDTH_RATIO));
        return new Rectangle(label.getWidth() - width, 0, width, label.getHeight());
    }

    private void clearThumbnailZoomDrag() {
        if (zoomDragLabel != null) {
            zoomDragLabel.setCursor(Cursor.getDefaultCursor());
        }
        zoomDragLabel = null;
        zoomDragFile = null;
        zoomDragStartPoint = null;
        zoomDragStartSelection = null;
        zoomDragImageSize = null;
        zoomDragImageBounds = null;
        zoomDragStarted = false;
    }

    public void reloadDirectory() {
        List<File> mediaFiles = MediaService.getInstance().loadFilesFromDirectory();
        populate(mediaFiles);
    }

    public void reloadDirectoryAndSelect(File fileToSelect) {
        if (fileToSelect != null) {
            AppState.get().setCurrentFile(fileToSelect);
        }
        reloadDirectory();
        if (fileToSelect != null) {
            SwingUtilities.invokeLater(() -> selectAndOpenFile(fileToSelect));
        }
    }

    private void selectAndOpenFile(File file) {
        if (file == null) return;

        for (AnimatedThumbnail thumb : animatedThumbnails) {
            if (file.getName().equals(thumb.filename)) {
                selectThumbnailLabel(thumb.label, false);
                if (MediaView.getInstance().isVisible()) {
                    Controller.getInstance().handleMedia(file, false);
                }
                scrollLabelToVisible(thumb.label);
                return;
            }
        }
    }

    public void selectNextUntaggedAfter(File currentFile) {
        if (currentFile == null) return;

        runOnEdt(() -> {
            if (animatedThumbnails.isEmpty()) return;

            int currentIndex = -1;
            for (int i = 0; i < animatedThumbnails.size(); i++) {
                if (currentFile.getName().equals(animatedThumbnails.get(i).filename)) {
                    currentIndex = i;
                    break;
                }
            }
            if (currentIndex < 0) return;

            for (int i = currentIndex + 1; i < animatedThumbnails.size(); i++) {
                AnimatedThumbnail thumb = animatedThumbnails.get(i);
                if (thumb.filename != null && TagHandler.getInstance().getTagsForFile(thumb.filename).isEmpty()) {
                    selectAndOpenThumbnail(thumb.label);
                    scrollLabelToVisible(thumb.label);
                    return;
                }
            }
        });
    }

    void updateVisibleThumbnails() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::updateVisibleThumbnails);
            return;
        }

        Rectangle view = scrollPane.getViewport().getViewRect();
        for (AnimatedThumbnail thumb : new ArrayList<>(animatedThumbnails)) {
            boolean visible = view.intersects(thumb.label.getBounds());
            if (visible) {
                requestThumbnailLoad(thumb, currentGenerationId);
                requestRemainingVideoFramesIfNeeded(thumb);
                if (!thumb.isRunning) {
                    thumb.start();
                }
            } else if (!visible && thumb.isRunning) {
                thumb.stop();
            }
        }
    }

    private void repaintMarchingAntsThumbnails() {
        marchingAntsPhase = (marchingAntsPhase + 0.6f) % 14f;
        Rectangle view = scrollPane.getViewport().getViewRect();
        for (AnimatedThumbnail thumb : new ArrayList<>(animatedThumbnails)) {
            JLabel label = thumb.label;
            if (label != null && label.getIcon() instanceof MarchingAntsIcon && label.getBounds().intersects(view)) {
                label.repaint();
            }
        }
    }

    public List<AnimatedThumbnail> animatedThumbnails = new ArrayList<>();
    int thumbnailsLoadedCount = 0;
    int totalFramesLoaded = 0;
    int framesFromCache = 0;

    private volatile long currentGenerationId = 0;
    private volatile int previewProgressLoaded = 0;
    private volatile int previewProgressTotal = 0;
    private volatile Set<String> thumbnailCacheNames = ConcurrentHashMap.newKeySet();
    private volatile Path thumbnailCacheIndexPath;
    private final Set<String> requestedThumbnailLoads = ConcurrentHashMap.newKeySet();
    private final Queue<File> thumbnailLoadQueue = new ArrayDeque<>();
    private final AtomicInteger thumbnailLoadProcessedFiles = new AtomicInteger();
    private final AtomicInteger activeThumbnailLoadTasks = new AtomicInteger();
    private final Map<String, AnimatedThumbnail> thumbnailsByName = new HashMap<>();
    int processed = 0;

    public void populate(List<File> _mediaFiles) {
        if (_mediaFiles == null) {
            runOnEdt(() -> scrollPane.setViewportView(new JPanel(new GridLayout(0, 3, 5, 5))));
            return;
        }

        List<File> sortedMediaFiles = new ArrayList<>(_mediaFiles);
        MediaService.sortByCreationDateDescending(sortedMediaFiles);
        final List<File> mediaFiles = new ArrayList<>(sortedMediaFiles);
        Map<String, Integer> displayOrder = new HashMap<>();
        for (int i = 0; i < mediaFiles.size(); i++) {
            displayOrder.put(mediaFiles.get(i).getName(), i);
        }

        long generation = ++currentGenerationId;

        thumbnailsLoadedCount = 0;
        totalFramesLoaded = 0;
        framesFromCache = 0;
        previewProgressLoaded = 0;
        previewProgressTotal = 0;
        thumbnailLoadProcessedFiles.set(0);
        activeThumbnailLoadTasks.set(0);
        requestedThumbnailLoads.clear();
        synchronized (thumbnailLoadQueue) {
            thumbnailLoadQueue.clear();
        }
        thumbnailLoadQueueTimer.stop();
        rebuildThumbnailCacheIndex();
        updateInitialLoadOverlay(0, 0);

        // Neues GridPanel erzeugen
        JPanel newGridPanel = new JPanel(new GridLayout(0, 3, 5, 5));
        installFileDropHandler(newGridPanel);
        JLabel loadingLabel = new JLabel("Lade Medien ...", SwingConstants.CENTER);
        installFileDropHandler(loadingLabel);
        loadingLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        runOnEdtAndWait(() -> {
            long now = System.currentTimeMillis();
            animatedThumbnails.forEach(AnimatedThumbnail::stop);
            animatedThumbnails.clear();
            thumbnailsByName.clear();
            selectedLabel = null;
            myLabel = null;
            scrollPane.setViewportView(loadingLabel);
            System.out.println("thumbnail ui reset took " + (System.currentTimeMillis() - now));
        });

        List<File> eligibleMediaFiles = new ArrayList<>();
        for (File file : mediaFiles) {
            MEDIA_TYPE type = Controller.isImageFile(file) ? MEDIA_TYPE.IMAGE : Controller.isVideoFile(file) ? MEDIA_TYPE.VIDEO : null;
            if (type == null) {
                continue;
            }

            try {
                if (type == MEDIA_TYPE.IMAGE) {
                    if (RangeHandler.getInstance().getTotalLength(file) > 0) {
                        continue;
                    }
                } else {
                    try {
                        int minDuration = AppState.get().getMinimunDuration();
                        int actualDuration = RangeHandler.getInstance().getTotalLength(file);

                        if (actualDuration > 0 && actualDuration < minDuration) {
                            continue;
                        }


                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception es) {
                es.printStackTrace();
            }

            eligibleMediaFiles.add(file);
        }

        previewProgressTotal = eligibleMediaFiles.size();
        EventBus.get().publishDirect(new ThumbnailsLoadedEvent(0, eligibleMediaFiles.size()));
        updateInitialLoadOverlay(0, eligibleMediaFiles.size());

        runOnEdtAndWait(() -> {
            if (generation != currentGenerationId) return;
            scrollPane.setViewportView(newGridPanel);
            for (File file : eligibleMediaFiles) {
                MEDIA_TYPE type = Controller.isImageFile(file) ? MEDIA_TYPE.IMAGE : MEDIA_TYPE.VIDEO;
                addThumbnailPlaceholderTo(newGridPanel, type, file, displayOrder);
            }
            requestThumbnailUiRefresh(newGridPanel);
            updateVisibleThumbnails();
        });

        List<File> prioritizedMediaFiles = prioritizeMediaLoadOrder(eligibleMediaFiles);
        synchronized (thumbnailLoadQueue) {
            thumbnailLoadQueue.clear();
            thumbnailLoadQueue.addAll(prioritizedMediaFiles);
        }
        SwingUtilities.invokeLater(() -> {
            drainThumbnailLoadQueue();
            if (!thumbnailLoadQueueTimer.isRunning()) {
                thumbnailLoadQueueTimer.start();
            }
        });

    }

    public void retainDisplayedFiles(List<File> filesToKeep) {
        Set<String> namesToKeep = new HashSet<>();
        if (filesToKeep != null) {
            for (File file : filesToKeep) {
                if (file != null) {
                    namesToKeep.add(file.getName());
                }
            }
        }

        runOnEdt(() -> {
            Component view = scrollPane.getViewport().getView();
            if (!(view instanceof JPanel gridPanel)) return;

            boolean changed = false;
            boolean removedActiveThumbnail = false;
            int firstRemovedIndex = Integer.MAX_VALUE;
            File currentFile = AppState.get().getCurrentFile();
            List<AnimatedThumbnail> removedThumbnails = new ArrayList<>();
            for (AnimatedThumbnail thumb : new ArrayList<>(animatedThumbnails)) {
                if (thumb.filename == null || namesToKeep.contains(thumb.filename)) {
                    continue;
                }

                int removedIndex = animatedThumbnails.indexOf(thumb);
                if (removedIndex >= 0) {
                    firstRemovedIndex = Math.min(firstRemovedIndex, removedIndex);
                }
                if (thumb.label == selectedLabel || thumb.label == myLabel
                        || (currentFile != null && currentFile.getName().equals(thumb.filename))) {
                    removedActiveThumbnail = true;
                }

                thumb.stop();
                removedThumbnails.add(thumb);
                thumbnailsByName.remove(thumb.filename);
                requestedThumbnailLoads.remove(thumb.filename);
                gridPanel.remove(thumb.label);
                if (thumb.label == selectedLabel) {
                    selectedLabel = null;
                }
                if (thumb.label == myLabel) {
                    myLabel = null;
                }
                changed = true;
            }

            if (!changed) return;

            animatedThumbnails.removeAll(removedThumbnails);
            if (removedActiveThumbnail && !animatedThumbnails.isEmpty()) {
                int nextIndex = Math.min(firstRemovedIndex, animatedThumbnails.size() - 1);
                selectAndOpenThumbnail(animatedThumbnails.get(nextIndex).label);
            }
            synchronized (thumbnailLoadQueue) {
                thumbnailLoadQueue.removeIf(file -> file == null || !namesToKeep.contains(file.getName()));
            }
            previewProgressTotal = Math.max(0, previewProgressTotal - removedThumbnails.size());
            updateInitialLoadOverlay(previewProgressLoaded, previewProgressTotal);

            gridPanel.revalidate();
            gridPanel.repaint();
            updateVisibleThumbnails();
        });
    }

    private void selectAndOpenThumbnail(JLabel label) {
        if (label == null) return;

        if (selectedLabel != null && selectedLabel != label) {
            selectedLabel.setBorder(null);
        }

        selectedLabel = label;
        myLabel = label;
        selectedLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 4));

        File file = (File) label.getClientProperty("file");
        if (file != null) {
            AppState.get().setCurrentFile(file);
            Controller.getInstance().handleMedia(file, false);
        }
    }

    private List<File> prioritizeMediaLoadOrder(List<File> mediaFiles) {
        List<File> result = new ArrayList<>(mediaFiles.size());
        File current = AppState.get().getCurrentFile();

        if (current != null) {
            int currentIndex = findFileIndexByName(mediaFiles, current.getName());
            if (currentIndex >= 0) {
                addMediaRange(result, mediaFiles,
                        currentIndex - RESTORED_SELECTION_PRIORITY_RADIUS,
                        currentIndex + RESTORED_SELECTION_PRIORITY_RADIUS
                );
            }
        }

        addMediaRange(result, mediaFiles, 0, INITIAL_VISIBLE_PRIORITY_COUNT - 1);
        addMediaRange(result, mediaFiles, 0, mediaFiles.size() - 1);
        return result;
    }

    private int findFileIndexByName(List<File> files, String fileName) {
        if (fileName == null) return -1;
        for (int i = 0; i < files.size(); i++) {
            if (fileName.equals(files.get(i).getName())) {
                return i;
            }
        }
        return -1;
    }

    private void addMediaRange(List<File> target, List<File> source, int startInclusive, int endInclusive) {
        int start = Math.max(0, startInclusive);
        int end = Math.min(source.size() - 1, endInclusive);
        for (int i = start; i <= end; i++) {
            File file = source.get(i);
            if (!target.contains(file)) {
                target.add(file);
            }
        }
    }

    private void installFileDropHandler(JComponent component) {
        component.setTransferHandler(fileDropTransferHandler);
    }

    private TransferHandler createFileDropTransferHandler() {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                boolean canImport = AppState.get().getCurrentDirectory() != null
                        && (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || support.isDataFlavorSupported(DataFlavor.imageFlavor)
                        || support.isDataFlavorSupported(DataFlavor.stringFlavor)
                        || hasTextFlavor(support.getDataFlavors())
                        || hasUrlFlavor(support.getDataFlavors()));

                if (canImport) {
                    logDrop("canImport=true, action=" + support.getDropAction());
                    logTransferFlavors("[ThumbnailPanel DnD]", support.getDataFlavors());
                }

                return canImport;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;

                try {
                    Transferable transferable = support.getTransferable();
                    logDrop("importData gestartet");
                    logTransferFlavors("[ThumbnailPanel DnD]", transferable.getTransferDataFlavors());

                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> droppedFiles = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        List<File> imageFiles = droppedFiles.stream()
                                .filter(File::isFile)
                                .filter(Controller::isImageFile)
                                .toList();

                        logDrop("Lokale Dateien empfangen: " + droppedFiles.size() + ", Bilder: " + imageFiles.size());

                        if (imageFiles.isEmpty()) {
                            JOptionPane.showMessageDialog(thisComponent(), "Keine unterstützten Bilddateien gefunden.", "Drag and Drop", JOptionPane.INFORMATION_MESSAGE);
                            return false;
                        }

                        Controller.getInstance().getExecutorService().submit(() -> copyDroppedImages(imageFiles));
                        return true;
                    }

                    URL url = extractUrl(transferable);
                    if (url != null) {
                        logDrop("URL empfangen: " + url);
                        Controller.getInstance().getExecutorService().submit(() -> downloadDroppedImage(url));
                        return true;
                    }

                    if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                        logDrop("Direktes Browser-Bild empfangen, aber nicht gespeichert: Das waere eine neu kodierte grosse PNG-Datei ohne Originalnamen.");
                        JOptionPane.showMessageDialog(
                                thisComponent(),
                                "Der Browser hat nur ein gerendertes Bild geliefert, keine Originaldatei/URL.\n"
                                        + "Das Bild wurde nicht gespeichert, damit keine grosse neu kodierte PNG-Datei entsteht.",
                                "Drag and Drop",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                        return false;
                    }

                    logDrop("Kein verwertbares Bildformat im Drop gefunden");
                    JOptionPane.showMessageDialog(thisComponent(), "Keine unterstützten Bilddaten gefunden.", "Drag and Drop", JOptionPane.INFORMATION_MESSAGE);
                    return false;
                } catch (UnsupportedFlavorException | IOException e) {
                    showDropError("Dateien konnten nicht gelesen werden.", e);
                    return false;
                }
            }

            private Component thisComponent() {
                return ThumbnailPanel.this;
            }
        };
    }

    private boolean hasUrlFlavor(DataFlavor[] flavors) {
        for (DataFlavor flavor : flavors) {
            if (URL.class.isAssignableFrom(flavor.getRepresentationClass())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTextFlavor(DataFlavor[] flavors) {
        return DataFlavor.selectBestTextFlavor(flavors) != null;
    }

    private URL extractUrl(Transferable transferable) throws UnsupportedFlavorException, IOException {
        for (DataFlavor flavor : transferable.getTransferDataFlavors()) {
            if (URL.class.isAssignableFrom(flavor.getRepresentationClass())) {
                Object data = transferable.getTransferData(flavor);
                if (data instanceof URL url) {
                    return url;
                }
            }
        }

        String text = null;
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
        } else {
            DataFlavor textFlavor = DataFlavor.selectBestTextFlavor(transferable.getTransferDataFlavors());
            if (textFlavor != null) {
                text = readTextFlavor(transferable, textFlavor);
            }
        }

        if (text == null || text.isBlank()) return null;

        logDrop("String-Drop-Inhalt: " + abbreviate(text));

        Matcher imageSrcMatcher = HTML_IMAGE_SRC_PATTERN.matcher(text);
        if (imageSrcMatcher.find()) {
            return toUrl(imageSrcMatcher.group(1));
        }

        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            URL url = toUrl(trimmed);
            if (url != null) {
                return url;
            }
        }

        return null;
    }

    private String readTextFlavor(Transferable transferable, DataFlavor textFlavor) throws UnsupportedFlavorException, IOException {
        StringBuilder text = new StringBuilder();
        try (Reader reader = textFlavor.getReaderForText(transferable)) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                text.append(buffer, 0, read);
            }
        }
        logDrop("Text-Flavor gelesen: " + textFlavor.getMimeType());
        return text.toString();
    }

    private URL toUrl(String text) {
        try {
            URI uri = new URI(text);
            if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                return uri.toURL();
            }
        } catch (URISyntaxException | MalformedURLException e) {
            logDrop("Keine gueltige URL: " + text);
        }
        return null;
    }

    private void copyDroppedImages(List<File> imageFiles) {
        Path targetDirectory = AppState.get().getCurrentDirectory();
        if (targetDirectory == null) return;

        int copied = 0;
        File firstCopiedFile = null;
        List<String> failed = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();

        for (File source : imageFiles) {
            try {
                Path sourcePath = source.toPath().toAbsolutePath().normalize();
                Path duplicate = DuplicateImageFinder.findExistingDuplicate(targetDirectory, sourcePath).orElse(null);

                if (duplicate != null) {
                    duplicates.add(source.getName() + " ist bereits vorhanden als " + duplicate.getFileName());
                    logDrop("Duplikat nicht kopiert: " + sourcePath + " == " + duplicate);
                    continue;
                }

                Path targetPath = uniqueTargetPath(targetDirectory, source.getName());
                Files.copy(sourcePath, targetPath);
                if (firstCopiedFile == null) {
                    firstCopiedFile = targetPath.toFile();
                }
                copied++;
                logDrop("Kopiert: " + sourcePath + " -> " + targetPath);
            } catch (IOException e) {
                logDrop("Kopieren fehlgeschlagen fuer " + source.getAbsolutePath() + ": " + e.getMessage());
                failed.add(source.getName());
            }
        }

        if (copied > 0) {
            reloadDirectoryAndSelect(firstCopiedFile);
        }

        if (!failed.isEmpty()) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    this,
                    "Einige Bilder konnten nicht kopiert werden:\n" + String.join("\n", failed),
                    "Drag and Drop",
                    JOptionPane.ERROR_MESSAGE
            ));
        }

        if (!duplicates.isEmpty()) {
            showDuplicateMessage("Drag and Drop", duplicates);
        }
    }

    private void downloadDroppedImage(URL url) {
        Path targetDirectory = AppState.get().getCurrentDirectory();
        if (targetDirectory == null) return;

        Path tempPath = null;
        try {
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 ImageViewer");
            String contentDisposition = connection.getHeaderField("Content-Disposition");
            String contentType = connection.getContentType();
            String fileName = fileNameFromDownloadMetadata(url, contentDisposition, contentType);
            tempPath = Files.createTempFile(targetDirectory, ".download-", ".tmp");

            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Path duplicate = DuplicateImageFinder.findExistingDuplicate(targetDirectory, tempPath).orElse(null);
            if (duplicate != null) {
                Files.deleteIfExists(tempPath);
                logDrop("URL-Bild ist Duplikat und wurde nicht gespeichert: " + url + " == " + duplicate);
                showDuplicateMessage("Drag and Drop", List.of(fileName + " ist bereits vorhanden als " + duplicate.getFileName()));
                return;
            }

            Path targetPath = uniqueTargetPath(targetDirectory, fileName);
            Files.move(tempPath, targetPath);

            logDrop("Bild von URL gespeichert: " + url
                    + ", contentType=" + contentType
                    + ", contentDisposition=" + contentDisposition
                    + " -> " + targetPath);
            reloadDirectoryAndSelect(targetPath.toFile());
        } catch (IOException e) {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException cleanupException) {
                    logDrop("Temporaere Download-Datei konnte nicht geloescht werden: " + tempPath);
                }
            }
            showDropError("Bild von URL konnte nicht gespeichert werden.", e);
        }
    }

    private void showDuplicateMessage(String title, List<String> duplicates) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                this,
                "Bereits vorhandene Bilder wurden nicht importiert:\n" + String.join("\n", duplicates),
                title,
                JOptionPane.INFORMATION_MESSAGE
        ));
    }

    private String fileNameFromDownloadMetadata(URL url, String contentDisposition, String contentType) {
        String fileName = fileNameFromContentDisposition(contentDisposition);
        if (fileName == null || fileName.isBlank()) {
            fileName = fileNameFromUrl(url);
        }

        if (!Controller.isImageFile(new File(fileName))) {
            fileName = fileName + extensionForContentType(contentType);
        }

        if (!Controller.isImageFile(new File(fileName))) {
            fileName = generatedImageFileName(contentType);
        }

        return fileName;
    }

    private String fileNameFromContentDisposition(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isBlank()) {
            return null;
        }

        Matcher utf8Matcher = Pattern.compile("filename\\*=UTF-8''([^;]+)", Pattern.CASE_INSENSITIVE).matcher(contentDisposition);
        if (utf8Matcher.find()) {
            return sanitizeFileName(URLDecoder.decode(utf8Matcher.group(1).trim(), StandardCharsets.UTF_8));
        }

        Matcher matcher = Pattern.compile("filename=\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE).matcher(contentDisposition);
        if (matcher.find()) {
            return sanitizeFileName(matcher.group(1).trim());
        }

        return null;
    }

    private String fileNameFromUrl(URL url) {
        String path = url.getPath();
        if (path == null || path.isBlank()) {
            return generatedImageFileName(null);
        }

        int lastSlash = path.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
        fileName = sanitizeFileName(fileName);

        if (fileName.isBlank()) {
            return generatedImageFileName(null);
        }
        return fileName;
    }

    private String extensionForContentType(String contentType) {
        if (contentType == null) return "";
        String normalized = contentType.toLowerCase();
        if (normalized.contains("jpeg") || normalized.contains("jpg")) return ".jpg";
        if (normalized.contains("png")) return ".png";
        if (normalized.contains("gif")) return ".gif";
        if (normalized.contains("webp")) return ".webp";
        if (normalized.contains("bmp")) return ".bmp";
        return "";
    }

    private String generatedImageFileName(String contentType) {
        String extension = extensionForContentType(contentType);
        if (extension.isBlank()) {
            extension = ".jpg";
        }
        return UUID.randomUUID().toString().toUpperCase() + extension;
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private Path uniqueTargetPath(Path targetDirectory, String fileName) {
        Path targetPath = targetDirectory.resolve(fileName);
        if (!Files.exists(targetPath)) {
            return targetPath;
        }

        String baseName = fileName;
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        int counter = 1;
        do {
            targetPath = targetDirectory.resolve(baseName + " " + counter + extension);
            counter++;
        } while (Files.exists(targetPath));

        return targetPath;
    }

    private void showDropError(String message, Exception e) {
        e.printStackTrace();
        logDrop(message + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message, "Drag and Drop", JOptionPane.ERROR_MESSAGE));
    }

    private void logTransferFlavors(String prefix, DataFlavor[] flavors) {
        for (DataFlavor flavor : flavors) {
            System.out.println(prefix + " Flavor: mime=" + flavor.getMimeType()
                    + ", human=" + flavor.getHumanPresentableName()
                    + ", class=" + flavor.getRepresentationClass().getName());
        }
    }

    private void logDrop(String message) {
        System.out.println("[ThumbnailPanel DnD] " + message);
    }

    private String abbreviate(String text) {
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= 300) return normalized;
        return normalized.substring(0, 300) + "...";
    }

    private void publishProgress(int loaded, int total) {
        previewProgressLoaded = loaded;
        previewProgressTotal = total;
        updateInitialLoadOverlay(loaded, total);
        EventBus.get().publishDirect(new ThumbnailsLoadedEvent(loaded, total));
    }

    private void updateInitialLoadOverlay(int loaded, int total) {
        Runnable update = () -> initialLoadOverlay.hideOverlay();
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void runOnEdtAndWait(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(task);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void addThumbnailPlaceholderTo(JPanel panel, MEDIA_TYPE type, File file, Map<String, Integer> displayOrder) {
        JLabel label = createThumbnailLabel(type, file);

        AnimatedThumbnail thumbnail = new AnimatedThumbnail();
        thumbnail.imageFiles = null;
        thumbnail.animationTimer = null;
        thumbnail.label = label;
        thumbnail.isRunning = false;
        thumbnail.type = type;
        thumbnail.filename = file.getName();

        int insertAt = findThumbnailInsertIndex(file, displayOrder);
        panel.add(label, insertAt);
        animatedThumbnails.add(insertAt, thumbnail);
        thumbnailsByName.put(file.getName(), thumbnail);
        selectRestoredThumbnailIfNeeded(label, file);
    }

    private JLabel createThumbnailLabel(MEDIA_TYPE type, File file) {
        JLabel label = new ThumbnailLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
        label.setOpaque(true);
        label.setBackground(Color.DARK_GRAY);
        label.putClientProperty("file", file);
        label.addMouseListener(mouseListener);
        label.addMouseMotionListener((MouseMotionListener) mouseListener);
        installThumbnailHoverPreload(label, type, file);
        return label;
    }

    private void installThumbnailHoverPreload(JLabel label, MEDIA_TYPE type, File file) {
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (type == MEDIA_TYPE.IMAGE) {
                    currentHoverFile = file;

                    Controller.getInstance().getExecutorService().submit(() -> {
                        H.sleep(50);
                        if (currentHoverFile != file) return;

                        try {
                            if (file.getName().toLowerCase().endsWith(".mpo")) {
                                MpoReader.preloadFrames(file);
                            } else {
                                File resolved = AppState.get().getFileForCurrentDirectory(file);
                                BufferedImage image = ImageIO.read(resolved);
                                H.out("setting preloaded image " + file.getName());
                                AppState.get().setPreloadedImage(image);
                                AppState.get().setPreloadedImageFile(resolved);
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                }
            }
        });
    }

    private void drainThumbnailLoadQueue() {
        if (currentGenerationId == 0) return;

        int submitted = 0;
        while (submitted < THUMBNAIL_LOAD_BATCH_SIZE && activeThumbnailLoadTasks.get() < MAX_BACKGROUND_THUMBNAIL_LOADS) {
            File file;
            synchronized (thumbnailLoadQueue) {
                file = thumbnailLoadQueue.poll();
            }
            if (file == null) {
                thumbnailLoadQueueTimer.stop();
                return;
            }

            AnimatedThumbnail thumbnail = thumbnailsByName.get(file.getName());
            if (thumbnail != null && requestThumbnailLoad(thumbnail, currentGenerationId)) {
                submitted++;
            }
        }
    }

    private boolean requestThumbnailLoad(AnimatedThumbnail thumbnail, long generation) {
        if (thumbnail == null || thumbnail.filename == null || thumbnail.label == null) return false;
        if (thumbnail.imageFiles != null && !thumbnail.imageFiles.isEmpty()) return false;
        if (!requestedThumbnailLoads.add(thumbnail.filename)) return false;

        File file = (File) thumbnail.label.getClientProperty("file");
        if (file == null) return false;

        MEDIA_TYPE type = thumbnail.type;
        int initialFrameCount = type == MEDIA_TYPE.IMAGE ? ANIMATION_FRAMES_PER_THUMBNAIL : INITIAL_VIDEO_THUMBNAIL_FRAMES;
        activeThumbnailLoadTasks.incrementAndGet();
        CompletableFuture
                .supplyAsync(() -> type == MEDIA_TYPE.IMAGE ? loadImageThumbnail(file) : loadThumbnails(file, initialFrameCount), Controller.getInstance().getExecutorService())
                .exceptionally(ex -> {
                    System.err.println("[ThumbnailPanel] Initial thumbnail failed for " + file.getAbsolutePath() + ": " + ex.getMessage());
                    return List.of();
                })
                .thenAccept(thumbFiles -> {
                    if (generation != currentGenerationId) return;
                    int done = thumbnailLoadProcessedFiles.incrementAndGet();
                    publishProgress(done, previewProgressTotal);
                    if (thumbFiles == null || thumbFiles.isEmpty()) return;

                    SwingUtilities.invokeLater(() -> {
                        if (generation != currentGenerationId || !animatedThumbnails.contains(thumbnail)) return;
                        applyLoadedThumbnail(thumbnail, type, thumbFiles, file);
                    });
                })
                .whenComplete((ignored, throwable) -> {
                    activeThumbnailLoadTasks.decrementAndGet();
                    SwingUtilities.invokeLater(this::drainThumbnailLoadQueue);
                });
        return true;
    }

    private void applyLoadedThumbnail(AnimatedThumbnail thumbnail, MEDIA_TYPE type, List<File> thumbnailFiles, File file) {
        JLabel label = thumbnail.label;
        thumbnail.imageFiles = thumbnailFiles;

        if (type == MEDIA_TYPE.IMAGE) {
            int rotation = RotationHandler.getInstance().getRotation(file);
            ThumbnailZoomMode mode = Controller.getInstance().getControlPanel().getThumbnailZoomMode();
            boolean needsDynamicThumbnail = rotation != 0
                    || (mode != ThumbnailZoomMode.STANDARD && ImageZoomHandler.getInstance().getZoomForFile(file) != null);
            if (!needsDynamicThumbnail && !thumbnailFiles.isEmpty()) {
                CompletableFuture
                        .supplyAsync(() -> createCachedThumbnailIcon(thumbnailFiles.get(0)), Controller.getInstance().getExecutorService())
                        .thenAccept(icon -> {
                            if (icon != null) {
                                SwingUtilities.invokeLater(() -> label.setIcon(icon));
                            }
                        });
            } else {
                CompletableFuture.runAsync(() -> {
                    ThumbnailRenderResult result = createImageThumbnailIcon(file);
                    if (result != null) {
                        SwingUtilities.invokeLater(() -> applyThumbnailRenderResult(label, result));
                    }
                }, Controller.getInstance().getExecutorService());
            }
        } else {
            Image image = Toolkit.getDefaultToolkit().getImage(thumbnailFiles.get(0).getAbsolutePath());
            label.setIcon(new ImageIcon(image));
        }

        thumbnailsLoadedCount++;
        if (animatedThumbnails.indexOf(thumbnail) < 20) {
            thumbnail.start();
            thumbnail.preload(PRELOAD_FRAMES_FOR_NEW_VIDEO_THUMBNAIL);
        }
    }

    private void addThumbnailLabelTo(JPanel panel, MEDIA_TYPE type, List<File> thumbnailFiles, File file, Map<String, Integer> displayOrder) {
        JLabel label = new ThumbnailLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
        label.setOpaque(true);
        label.setBackground(Color.DARK_GRAY);
        label.putClientProperty("file", file);
        label.addMouseListener(mouseListener);
        label.addMouseMotionListener((MouseMotionListener) mouseListener);

//        in slideshow angezeigtes bild soll in thumbailview angezeigt werden


        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {

                if (type == MEDIA_TYPE.IMAGE) {
                    currentHoverFile = file;

                    Controller.getInstance().getExecutorService().submit(() -> {
                        // Kurze künstliche Verzögerung, optional:
                        H.sleep(50);

                        // Bin ich noch der aktuelle Hover?
                        if (currentHoverFile != file) return;

                        // Aufwendig laden:
                        try {
                            BufferedImage image;
                            if (file.getName().toLowerCase().endsWith(".mpo")) {
                                MpoReader.preloadFrames(file);
//                                List<BufferedImage> images = new JPGExtractor().createBufferdImageFromMpo(file);
//                                double p = ParallaxHandler.getInstance().getParallaxForFile(file);
//                                image = AnaglyphUtils.createSimpleAnaglyphVarianteC(images.get(0), images.get(1), p, 0.8f, 1.0f);
//                                AppState.get().setPreloadedImage(image);
                            } else {
                                File resolved = AppState.get().getFileForCurrentDirectory(file);
                                image = ImageIO.read(resolved);
                                H.out("setting preloaded image " + file.getName());
                                AppState.get().setPreloadedImage(image);
                                AppState.get().setPreloadedImageFile(resolved);
                            }

                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }

                    });
                }
            }
        });

        boolean imagedOK = true;

        if (type == MEDIA_TYPE.IMAGE) {
            int rotation = RotationHandler.getInstance().getRotation(file);
            ThumbnailZoomMode mode = Controller.getInstance().getControlPanel().getThumbnailZoomMode();
            boolean needsDynamicThumbnail = rotation != 0
                    || (mode != ThumbnailZoomMode.STANDARD && ImageZoomHandler.getInstance().getZoomForFile(file) != null);
            if (!needsDynamicThumbnail && !thumbnailFiles.isEmpty()) {
                CompletableFuture
                        .supplyAsync(() -> createCachedThumbnailIcon(thumbnailFiles.get(0)), Controller.getInstance().getExecutorService())
                        .thenAccept(icon -> {
                            if (icon != null) {
                                SwingUtilities.invokeLater(() -> label.setIcon(icon));
                            }
                        });
            } else {
                CompletableFuture.runAsync(() -> {
                    ThumbnailRenderResult result = createImageThumbnailIcon(file);
                    if (result != null) {
                        SwingUtilities.invokeLater(() -> applyThumbnailRenderResult(label, result));
                    }
                }, Controller.getInstance().getExecutorService());
            }
        } else {
            Image image = Toolkit.getDefaultToolkit().getImage(thumbnailFiles.get(0).getAbsolutePath());
            label.setIcon(new ImageIcon(image));
        }

        if (imagedOK) {
            AnimatedThumbnail aNail = new AnimatedThumbnail();
            aNail.imageFiles = thumbnailFiles;
            aNail.animationTimer = null;
            aNail.label = label;
            aNail.isRunning = false;
            aNail.type = type;
            aNail.filename = file.getName();

            int insertAt = findThumbnailInsertIndex(file, displayOrder);
            panel.add(label, insertAt);
            animatedThumbnails.add(insertAt, aNail);
            requestThumbnailUiRefresh(panel);
            selectRestoredThumbnailIfNeeded(label, file);

            if (animatedThumbnails.size() < 20) {
                aNail.start();
                aNail.preload(PRELOAD_FRAMES_FOR_NEW_VIDEO_THUMBNAIL);
            }
        }
    }

    private void selectRestoredThumbnailIfNeeded(JLabel label, File file) {
        File current = AppState.get().getCurrentFile();
        if (current == null || file == null || !current.getName().equals(file.getName())) return;

        selectThumbnailLabel(label, true);
        EventBus.get().publish(new CurrentlySelectedFileEvent(file));
    }

    private int findThumbnailInsertIndex(File file, Map<String, Integer> displayOrder) {
        int fileOrder = displayOrder.getOrDefault(file.getName(), Integer.MAX_VALUE);
        for (int i = 0; i < animatedThumbnails.size(); i++) {
            int existingOrder = displayOrder.getOrDefault(animatedThumbnails.get(i).filename, Integer.MAX_VALUE);
            if (existingOrder > fileOrder) {
                return i;
            }
        }
        return animatedThumbnails.size();
    }

    private void requestThumbnailUiRefresh(JPanel panel) {
        pendingRefreshPanel = panel;
        if (!thumbnailUiRefreshTimer.isRunning()) {
            thumbnailUiRefreshTimer.start();
        }
    }

    private void flushThumbnailUiRefresh() {
        JPanel panel = pendingRefreshPanel;
        if (panel == null) return;

        panel.revalidate();
        panel.repaint();
        if (pendingScrollToSelectedThumbnail) {
            SwingUtilities.invokeLater(this::scrollSelectedThumbnailToVisible);
        }
        updateVisibleThumbnails();
    }

    private void loadRemainingVideoFramesAsync(AnimatedThumbnail thumbnail, File file) {
        CompletableFuture
                .supplyAsync(() -> loadThumbnails(file, ANIMATION_FRAMES_PER_THUMBNAIL), Controller.getInstance().getExecutorService())
                .thenAccept(thumbFiles -> {
                    if (thumbFiles == null || thumbFiles.isEmpty()) {
                        thumbnail.fullFrameListRequested = false;
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (!animatedThumbnails.contains(thumbnail)) return;

                        boolean wasRunning = thumbnail.isRunning;
                        if (wasRunning) {
                            thumbnail.stop();
                        }

                        thumbnail.imageFiles = thumbFiles;
                        thumbnail.fullFrameListLoaded = true;

                        if (wasRunning) {
                            thumbnail.start();
                            thumbnail.preload(PRELOAD_FRAMES_FOR_NEW_VIDEO_THUMBNAIL);
                        }
                        updateVisibleThumbnails();
                    });
                });
    }

    private void requestRemainingVideoFramesIfNeeded(AnimatedThumbnail thumbnail) {
        if (thumbnail.type != MEDIA_TYPE.VIDEO) return;
        if (thumbnail.fullFrameListRequested || thumbnail.fullFrameListLoaded) return;
        File file = (File) thumbnail.label.getClientProperty("file");
        if (file == null) return;

        thumbnail.fullFrameListRequested = true;
        loadRemainingVideoFramesAsync(thumbnail, file);
    }

    public void invalidateThumbnails(File videoFile) {
        File[] cachedFiles = getThumbnailCacheDir().listFiles((dir, name) -> name.endsWith(videoFile.getName() + ".jpg"));
        if (cachedFiles != null) {
            for (File f : cachedFiles) {
                thumbnailCacheNames.remove(f.getName());
                f.delete();
            }
        }
    }

    private ThumbnailRenderResult createImageThumbnailIcon(File file) {
        try {
            File resolved = AppState.get().getFileForCurrentDirectory(file);
            BufferedImage original;

            if (file.getName().toLowerCase().endsWith(".mpo")) {
                original = MpoReader.getLeftFrame(resolved);
            } else {
                original = ImageIO.read(resolved);
            }

            if (original == null) {
                H.out("Problems remain " + file.getAbsoluteFile().toPath());
                return null;
            }

            int rotation = RotationHandler.getInstance().getRotation(file);
            BufferedImage rotated = H.rotate(original, rotation);
            ThumbnailZoomMode mode = Controller.getInstance().getControlPanel().getThumbnailZoomMode();
            return renderImageThumbnail(rotated, file, mode);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ImageIcon createCachedThumbnailIcon(File thumbnailFile) {
        byte[] bytes = ThumbnailCache.getByteArray(thumbnailFile);
        if (bytes == null) return null;
        ImageIcon icon = new ImageIcon(bytes);
        icon.getIconWidth();
        icon.getIconHeight();
        return icon;
    }

    private ThumbnailRenderResult renderImageThumbnail(BufferedImage image, File file, ThumbnailZoomMode mode) {
        return renderImageThumbnail(image, file, mode, ImageZoomHandler.getInstance().getZoomForFile(file));
    }

    private ThumbnailRenderResult renderImageThumbnail(BufferedImage image, File file, ThumbnailZoomMode mode, ImageZoomHandler.ZoomSelection zoom) {
        if (mode == ThumbnailZoomMode.ZOOMED && zoom != null) {
            return new ThumbnailRenderResult(new ImageIcon(renderZoomedThumbnail(image, zoom)), null, null, null, image);
        }

        BufferedImage standard = renderStandardThumbnail(image);
        if (mode == ThumbnailZoomMode.GRAYED_OUT && zoom != null) {
            BufferedImage masked = copyImage(standard);
            Dimension imageSize = new Dimension(image.getWidth(), image.getHeight());
            Rectangle2D visibleRect = paintGrayedOutZoomMask(masked, imageSize, zoom);
            return new ThumbnailRenderResult(
                    new MarchingAntsIcon(masked, visibleRect),
                    visibleRect,
                    getStandardRenderedImageBounds(image),
                    imageSize,
                    standard
            );
        }
        return new ThumbnailRenderResult(new ImageIcon(standard), null, null, null, null);
    }

    private BufferedImage renderStandardThumbnail(BufferedImage image) {
        BufferedImage result = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        applyThumbnailRenderingHints(g);
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);

        Rectangle2D bounds = getStandardRenderedImageBounds(image);
        g.drawImage(
                image,
                (int) Math.round(bounds.getX()),
                (int) Math.round(bounds.getY()),
                (int) Math.round(bounds.getWidth()),
                (int) Math.round(bounds.getHeight()),
                null
        );
        g.dispose();
        return result;
    }

    private BufferedImage renderZoomedThumbnail(BufferedImage image, ImageZoomHandler.ZoomSelection zoom) {
        BufferedImage result = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        applyThumbnailRenderingHints(g);
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);

        Rectangle2D viewRect = getZoomViewRect(image, zoom);
        double scale = Math.max(THUMBNAIL_WIDTH / viewRect.getWidth(), THUMBNAIL_HEIGHT / viewRect.getHeight());
        int width = (int) Math.round(image.getWidth() * scale);
        int height = (int) Math.round(image.getHeight() * scale);
        int x = (int) Math.round(-viewRect.getX() * scale);
        int y = (int) Math.round(-viewRect.getY() * scale);

        g.drawImage(image, x, y, width, height, null);
        paintZoomMarker(g);
        g.dispose();
        return result;
    }

    private Rectangle2D paintGrayedOutZoomMask(BufferedImage thumbnail, Dimension imageSize, ImageZoomHandler.ZoomSelection zoom) {
        Graphics2D g = thumbnail.createGraphics();
        applyThumbnailRenderingHints(g);

        Rectangle2D imageBounds = getStandardRenderedImageBounds(imageSize);
        Rectangle2D viewRect = getZoomViewRect(imageSize, zoom);
        double scaleX = imageBounds.getWidth() / imageSize.getWidth();
        double scaleY = imageBounds.getHeight() / imageSize.getHeight();

        Rectangle2D visibleRect = new Rectangle2D.Double(
                imageBounds.getX() + viewRect.getX() * scaleX,
                imageBounds.getY() + viewRect.getY() * scaleY,
                viewRect.getWidth() * scaleX,
                viewRect.getHeight() * scaleY
        );

        Area outside = new Area(new Rectangle(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
        outside.subtract(new Area(visibleRect));
        g.setColor(new Color(0, 0, 0, 150));
        g.fill(outside);
        g.dispose();
        return visibleRect;
    }

    private Rectangle2D getVisibleZoomRectInThumbnail(Dimension imageSize, ImageZoomHandler.ZoomSelection zoom) {
        Rectangle2D imageBounds = getStandardRenderedImageBounds(imageSize);
        Rectangle2D viewRect = getZoomViewRect(imageSize, zoom);
        double scaleX = imageBounds.getWidth() / imageSize.getWidth();
        double scaleY = imageBounds.getHeight() / imageSize.getHeight();
        return new Rectangle2D.Double(
                imageBounds.getX() + viewRect.getX() * scaleX,
                imageBounds.getY() + viewRect.getY() * scaleY,
                viewRect.getWidth() * scaleX,
                viewRect.getHeight() * scaleY
        );
    }

    private Rectangle2D getStandardRenderedImageBounds(BufferedImage image) {
        return getStandardRenderedImageBounds(new Dimension(image.getWidth(), image.getHeight()));
    }

    private Rectangle2D getStandardRenderedImageBounds(Dimension imageSize) {
        double widthRatio = (double) THUMBNAIL_WIDTH / imageSize.getWidth();
        double heightRatio = (double) THUMBNAIL_HEIGHT / imageSize.getHeight();
        double scale = Math.max(widthRatio, heightRatio);

        double width = imageSize.getWidth() * scale;
        double height = imageSize.getHeight() * scale;
        double x = (THUMBNAIL_WIDTH - width) / 2.0;
        double overflowY = Math.max(0, height - THUMBNAIL_HEIGHT);
        double y = -(overflowY / 3.0);

        return new Rectangle2D.Double(x, y, width, height);
    }

    private Rectangle2D getZoomViewRect(BufferedImage image, ImageZoomHandler.ZoomSelection zoom) {
        return getZoomViewRect(new Dimension(image.getWidth(), image.getHeight()), zoom);
    }

    private Rectangle2D getZoomViewRect(Dimension imageSize, ImageZoomHandler.ZoomSelection zoom) {
        double selectedX = clamp(zoom.x(), 0, 1) * imageSize.getWidth();
        double selectedY = clamp(zoom.y(), 0, 1) * imageSize.getHeight();
        double selectedWidth = clamp(zoom.width(), 0, 1) * imageSize.getWidth();
        double selectedHeight = clamp(zoom.height(), 0, 1) * imageSize.getHeight();

        if (selectedWidth <= 0 || selectedHeight <= 0) {
            return new Rectangle2D.Double(0, 0, imageSize.getWidth(), imageSize.getHeight());
        }

        double viewportAspect = (double) THUMBNAIL_WIDTH / THUMBNAIL_HEIGHT;
        double selectedAspect = selectedWidth / selectedHeight;
        double viewWidth = selectedWidth;
        double viewHeight = selectedHeight;

        if (selectedAspect < viewportAspect) {
            viewWidth = selectedHeight * viewportAspect;
        } else {
            viewHeight = selectedWidth / viewportAspect;
        }

        viewWidth = Math.min(viewWidth, imageSize.getWidth());
        viewHeight = Math.min(viewHeight, imageSize.getHeight());

        double centerX = selectedX + selectedWidth / 2.0;
        double centerY = selectedY + selectedHeight / 2.0;
        double x = clamp(centerX - viewWidth / 2.0, 0, imageSize.getWidth() - viewWidth);
        double y = clamp(centerY - viewHeight / 2.0, 0, imageSize.getHeight() - viewHeight);

        return new Rectangle2D.Double(x, y, viewWidth, viewHeight);
    }

    private void paintZoomMarker(Graphics2D g) {
        int size = 18;
        Polygon marker = new Polygon(
                new int[]{THUMBNAIL_WIDTH, THUMBNAIL_WIDTH, THUMBNAIL_WIDTH - size},
                new int[]{THUMBNAIL_HEIGHT, THUMBNAIL_HEIGHT - size, THUMBNAIL_HEIGHT},
                3
        );
        g.setColor(new Color(255, 221, 0, 230));
        g.fillPolygon(marker);
    }

    private void applyThumbnailRenderingHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private BufferedImage copyImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return copy;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class InitialLoadOverlay extends JComponent {
        private static final int STALE_PROGRESS_HIDE_DELAY_MS = 1000;
        private static final int PAINT_THROTTLE_MS = 100;
        private int loaded;
        private int total;
        private int visibleLoaded;
        private long lastProgressChangeMillis;
        private long lastPaintMillis;
        private final Timer staleProgressTimer;

        InitialLoadOverlay() {
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
            });
            addMouseMotionListener(new MouseMotionAdapter() {
            });
            staleProgressTimer = new Timer(200, e -> hideIfProgressIsStale());
            staleProgressTimer.setRepeats(true);
        }

        void setProgress(Component owner, int loaded, int total) {
            JRootPane rootPane = SwingUtilities.getRootPane(owner);
            if (rootPane != null && rootPane.getGlassPane() != this) {
                rootPane.setGlassPane(this);
            }

            int safeTotal = Math.max(0, total);
            int safeLoaded = Math.max(0, loaded);
            if (safeTotal != this.total || safeLoaded == 0) {
                visibleLoaded = 0;
            }
            visibleLoaded = Math.max(visibleLoaded, safeLoaded);

            if (visibleLoaded != this.loaded) {
                lastProgressChangeMillis = System.currentTimeMillis();
            }
            this.loaded = visibleLoaded;
            this.total = safeTotal;
            setVisible(this.total > 0 && this.loaded < this.total);
            if (isVisible() && !staleProgressTimer.isRunning()) {
                if (lastProgressChangeMillis == 0) {
                    lastProgressChangeMillis = System.currentTimeMillis();
                }
                staleProgressTimer.start();
            } else if (!isVisible()) {
                staleProgressTimer.stop();
            }
            long now = System.currentTimeMillis();
            if (now - lastPaintMillis >= PAINT_THROTTLE_MS || this.loaded >= this.total) {
                lastPaintMillis = now;
                repaint();
            }
            if (isShowing() && now - lastPaintMillis <= 1) {
                paintImmediately(0, 0, getWidth(), getHeight());
            }
        }

        void hideOverlay() {
            setVisible(false);
            staleProgressTimer.stop();
        }

        private void hideIfProgressIsStale() {
            if (!isVisible()) {
                staleProgressTimer.stop();
                return;
            }
            if (System.currentTimeMillis() - lastProgressChangeMillis >= STALE_PROGRESS_HIDE_DELAY_MS) {
                setVisible(false);
                staleProgressTimer.stop();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (total <= 0 || loaded >= total) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(35, 35, 35, 170));
            g2.fillRect(0, 0, getWidth(), getHeight());

            int barWidth = Math.max(240, (int) (getWidth() * 0.90));
            barWidth = Math.min(barWidth, getWidth() - 40);
            int barHeight = Math.min(200, Math.max(100, getHeight() / 5));
            int x = (getWidth() - barWidth) / 2;
            int y = (getHeight() - barHeight) / 2;

            double progress = total == 0 ? 0.0 : Math.min(1.0, loaded / (double) total);
            int fillWidth = (int) Math.round(barWidth * progress);

            int radius = 12;
            g2.setColor(new Color(12, 18, 12, 235));
            g2.fillRoundRect(x, y, barWidth, barHeight, radius, radius);

            Shape oldClip = g2.getClip();
            g2.clip(new Rectangle(x, y, fillWidth, barHeight));
            g2.setColor(new Color(62, 255, 0));
            g2.fillRoundRect(x, y, barWidth, barHeight, radius, radius);
            g2.setColor(new Color(170, 255, 130, 80));
            g2.fillRect(x, y, barWidth, Math.max(1, barHeight / 5));
            g2.setClip(oldClip);

            int segmentWidth = 102;
            int gapWidth = 4;
            g2.setColor(new Color(0, 0, 0, 150));
            for (int sx = x + segmentWidth; sx < x + barWidth; sx += segmentWidth + gapWidth) {
                g2.fillRect(sx, y + 2, gapWidth, barHeight - 4);
            }

            g2.setColor(new Color(190, 255, 165));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(x, y, barWidth, barHeight, radius, radius);
            g2.dispose();
        }
    }

    private record ThumbnailRenderResult(
            ImageIcon icon,
            Rectangle2D visibleRect,
            Rectangle2D imageBounds,
            Dimension imageSize,
            BufferedImage renderImage
    ) {
    }

    private class ThumbnailLabel extends JLabel {
        private boolean zoomToggleHovered;
        private float zoomToggleReveal;

        void setZoomToggleHovered(boolean hovered) {
            if (zoomToggleHovered == hovered) return;
            zoomToggleHovered = hovered;
            requestZoomToggleAnimation();
        }

        boolean animateZoomToggle() {
            float target = zoomToggleHovered ? 1f : 0f;
            if (Math.abs(zoomToggleReveal - target) < 0.01f) {
                zoomToggleReveal = target;
                return false;
            }

            if (zoomToggleReveal < target) {
                zoomToggleReveal = Math.min(target, zoomToggleReveal + THUMBNAIL_ZOOM_TOGGLE_ANIMATION_STEP);
            } else {
                zoomToggleReveal = Math.max(target, zoomToggleReveal - THUMBNAIL_ZOOM_TOGGLE_ANIMATION_STEP);
            }
            repaint();
            return true;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            File file = (File) getClientProperty("file");
            if (file == null || !Controller.isImageFile(file)) return;
            if (ImageZoomHandler.getInstance().getZoomForFile(file) == null) return;
            if (zoomToggleReveal <= 0f) return;

            Rectangle bounds = thumbnailZoomToggleBounds(this);
            int revealWidth = Math.max(1, Math.round(bounds.width * zoomToggleReveal));
            int x = bounds.x + bounds.width - revealWidth;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 221, 0, 115));
            g2.fillRect(x, bounds.y, revealWidth, bounds.height);
            g2.dispose();
        }
    }

    private class MarchingAntsIcon extends ImageIcon {
        private final Rectangle2D rect;

        MarchingAntsIcon(BufferedImage image, Rectangle2D rect) {
            super(image);
            this.rect = rect;
        }

        @Override
        public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
            super.paintIcon(c, g, x, y);
            if (rect == null) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float phase = marchingAntsPhase;
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, MARCHING_ANTS_DASH, phase));

            double inset = 6;
            double x1 = Math.max(inset, rect.getX() + 1);
            double y1 = Math.max(inset, rect.getY() + 1);
            double x2 = Math.min(getIconWidth() - inset, rect.getMaxX() - 1);
            double y2 = Math.min(getIconHeight() - inset, rect.getMaxY() - 1);
            if (x2 <= x1 || y2 <= y1) return;

            Rectangle2D drawRect = new Rectangle2D.Double(
                    x + x1,
                    y + y1,
                    x2 - x1,
                    y2 - y1
            );

            g2.setColor(Color.BLACK);
            g2.draw(drawRect);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, MARCHING_ANTS_DASH, phase + 7f));
            g2.setColor(Color.WHITE);
            g2.draw(drawRect);
            g2.dispose();
        }
    }

    private Image getScaledImagePreserveRatio(Image srcImg, int maxWidth, int maxHeight) {
        int srcWidth = srcImg.getWidth(null);
        int srcHeight = srcImg.getHeight(null);

        if (srcWidth <= 0 || srcHeight <= 0) return srcImg;

        double widthRatio = (double) maxWidth / srcWidth;
        double heightRatio = (double) maxHeight / srcHeight;
        double scale = Math.max(widthRatio, heightRatio);

        int newWidth = (int) Math.round(srcWidth * scale);
        int newHeight = (int) Math.round(srcHeight * scale);
        int x = (maxWidth - newWidth) / 2;
        int overflowY = Math.max(0, newHeight - maxHeight);
        int y = -(overflowY / 3);

        BufferedImage result = new BufferedImage(maxWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, maxWidth, maxHeight);
        g.drawImage(srcImg, x, y, newWidth, newHeight, null);
        g.dispose();

        return result;
    }

    private List<File> loadThumbnails(File videoFile, int count) {
        int millis = 0;
        RangeHandler.Range range = RangeHandler.getInstance().getRangeForFile(videoFile);

        if (range != null) {
            millis = (int) (range.start * 1000);
        }

        List<File> files = new ArrayList<>();
        for (int t = 0; t < count; t++) {
            File thumb = fetchVideoThumbnail(videoFile, millis);
            if (thumb != null) {
                files.add(thumb);
            }
            millis += ANIMATION_DELAY_RECORD;
        }
        return files;
    }

    private File fetchVideoThumbnail(File videoFile, int milli) {
        File thumbnailCacheDir = getThumbnailCacheDir();
        if (!thumbnailCacheDir.exists()) {
            thumbnailCacheDir.mkdirs();
        }

        String nameInCache = milli + "_" + videoFile.getName() + ".jpg";
        totalFramesLoaded++;
        File file = new File(thumbnailCacheDir, nameInCache);
        if (isKnownCachedThumbnail(thumbnailCacheDir, nameInCache, file)) {
            framesFromCache++;
            return file;
        }

        file = extractVideoThumbnail(videoFile, milli);
        return file;
    }

    private File getThumbnailCacheDir() {
        Path currentDir = AppState.get().getCurrentDirectory();
        if (currentDir == null) return new File("thumbnails"); // Fallback für Notfall
        Path thumbsDir = currentDir.resolve("thumbnails");
        if (!Files.exists(thumbsDir)) {
            try {
                Files.createDirectories(thumbsDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return thumbsDir.toFile();
    }

    private void rebuildThumbnailCacheIndex() {
        File cacheDir = getThumbnailCacheDir();
        Path cachePath = cacheDir.toPath().toAbsolutePath().normalize();
        Set<String> names = ConcurrentHashMap.newKeySet();
        File[] cachedFiles = cacheDir.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (cachedFiles != null) {
            for (File file : cachedFiles) {
                names.add(file.getName());
            }
        }
        thumbnailCacheNames = names;
        thumbnailCacheIndexPath = cachePath;
    }

    private boolean isKnownCachedThumbnail(File cacheDir, String nameInCache, File file) {
        Path cachePath = cacheDir.toPath().toAbsolutePath().normalize();
        Set<String> names = thumbnailCacheNames;
        if (cachePath.equals(thumbnailCacheIndexPath) && names.contains(nameInCache)) {
            return true;
        }

        if (file.exists()) {
            names.add(nameInCache);
            return true;
        }

        return false;
    }

    private File extractVideoThumbnail(File videoFile, int milli) {
        try {
            String nameInCache = milli + "_" + videoFile.getName() + ".jpg";
            File file = new File(getThumbnailCacheDir(), nameInCache);

            int totalSeconds = milli / 1000;
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int seconds = totalSeconds % 60;
            int ms = milli % 1000;
            String timestamp = String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);

            String[] cmd = {"ffmpeg", "-y", "-loglevel", "error", "-ss", timestamp, "-i", videoFile.getAbsolutePath(), "-vframes", "1", "-vf", "scale='min(" + PREVIEW_IMAGE_WIDTH + "\\,iw)':min(" + PREVIEW_IMAGE_HEIGHT + "\\,ih):force_original_aspect_ratio=decrease,pad=" + PREVIEW_IMAGE_WIDTH + ":" + PREVIEW_IMAGE_HEIGHT + ":(ow-iw)/2:(oh-ih)/2", file.getAbsolutePath()};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.start().waitFor();

            if (file.exists()) {
                thumbnailCacheNames.add(file.getName());
                return file;
            }
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
