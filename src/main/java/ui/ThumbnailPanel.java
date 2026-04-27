package ui;

import event.*;
import model.AppState;
import service.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;

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

    private final JScrollPane scrollPane;

    private final MouseListener mouseListener;

    private volatile File currentHoverFile;

    private JLabel myLabel;
    private static final int INITIAL_VIDEO_THUMBNAIL_FRAMES = 1;
    private static final int PRELOAD_FRAMES_FOR_NEW_VIDEO_THUMBNAIL = 3;
    private static final int PROGRESS_UPDATE_STEP = 25;
    private JPanel pendingRefreshPanel;
    private final Timer thumbnailUiRefreshTimer = new Timer(80, e -> flushThumbnailUiRefresh());


    public ThumbnailPanel() {


        mouseListener = createMouseListener();
        thumbnailUiRefreshTimer.setRepeats(false);

        setLayout(new BorderLayout());
        scrollPane = new JScrollPane();
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.getViewport().addChangeListener(e -> updateVisibleThumbnails());
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

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

        for (AnimatedThumbnail thumb : animatedThumbnails) {
            File thumbFile = (File) thumb.label.getClientProperty("file");
            if (thumbFile != null && thumbFile.equals(current)) {

                if (selectedLabel != null) {
                    selectedLabel.setBorder(null);
                }

                selectedLabel = thumb.label;
                selectedLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 4));
                myLabel = selectedLabel;

                Rectangle r = selectedLabel.getBounds();
                Rectangle viewRect = SwingUtilities.convertRectangle(selectedLabel.getParent(), r, scrollPane.getViewport());
                scrollPane.getViewport().scrollRectToVisible(viewRect);

                break;
            }
        }
    }

    private MouseAdapter createMouseListener() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                JLabel label = (JLabel) e.getSource();
                myLabel = label;
                File file = (File) label.getClientProperty("file");

                if (SwingUtilities.isRightMouseButton(e)) {
                    // Kontextmenü wie gehabt
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem deleteItem = new JMenuItem("Bild löschen");

                    deleteItem.addActionListener(ev -> {
                        int result = JOptionPane.showConfirmDialog(label, "Bild wirklich löschen?\n" + file.getName(), "Löschen bestätigen", JOptionPane.YES_NO_OPTION);

                        if (result == JOptionPane.YES_OPTION) {
                            if (file.delete()) {
                                EventBus.get().publish(new TagsChangedEvent());
                                Controller.getInstance().getExecutorService().submit(() -> reloadDirectory());
                            } else {
                                JOptionPane.showMessageDialog(label, "Datei konnte nicht gelöscht werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    });

                    popup.add(deleteItem);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                    return; // Rechtsklick fertig
                }

                if (selectedLabel != null) {
                    selectedLabel.setBorder(null);
                }
                selectedLabel = label;
                selectedLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 4));

                // Single + Double: Zähler über ClickCount
                if (e.getClickCount() == 1) {
                    H.out("once pressed " + file.getName());
                    AppState.get().setCurrentFile(file);
                    Controller.getInstance().handleMedia(file, false);
                } else if (e.getClickCount() == 2) {
                    AppState.get().setCurrentFile(file);
                    Controller.getInstance().handleMedia(file, true);
                }
            }
        };
    }

    private List<File> loadImageThumbnail(File imageFile) {
        List<File> result = new ArrayList<>();
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
                e.printStackTrace();
            }
        }

        result.add(thumbFile);
        return result;
    }

    public void reloadDirectory() {
        List<File> mediaFiles = MediaService.getInstance().loadFilesFromDirectory();
        populate(mediaFiles);
    }

    void updateVisibleThumbnails() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::updateVisibleThumbnails);
            return;
        }

        Rectangle view = scrollPane.getViewport().getViewRect();
        for (AnimatedThumbnail thumb : new ArrayList<>(animatedThumbnails)) {
            boolean visible = view.intersects(thumb.label.getBounds());
            if (visible && !thumb.isRunning) {
                thumb.start();
            } else if (!visible && thumb.isRunning) {
                thumb.stop();
            }
        }
    }

    public List<AnimatedThumbnail> animatedThumbnails = new ArrayList<>();
    int thumbnailsLoadedCount = 0;
    int totalFramesLoaded = 0;
    int framesFromCache = 0;

    private volatile long currentGenerationId = 0;
    int processed = 0;

    public void populate(List<File> _mediaFiles) {
        if (_mediaFiles == null) {
            runOnEdt(() -> scrollPane.setViewportView(new JPanel(new GridLayout(0, 3, 5, 5))));
            return;
        }

        List<File> mediaFiles = new ArrayList<>(_mediaFiles);

        long generation = ++currentGenerationId;

        thumbnailsLoadedCount = 0;
        totalFramesLoaded = 0;
        framesFromCache = 0;

        // Neues GridPanel erzeugen
        JPanel newGridPanel = new JPanel(new GridLayout(0, 3, 5, 5));
        JLabel loadingLabel = new JLabel("Lade Medien ...", SwingConstants.CENTER);
        loadingLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        runOnEdtAndWait(() -> {
            long now = System.currentTimeMillis();
            animatedThumbnails.forEach(AnimatedThumbnail::stop);
            animatedThumbnails.clear();
            selectedLabel = null;
            myLabel = null;
            scrollPane.setViewportView(loadingLabel);
            System.out.println("thumbnail ui reset took " + (System.currentTimeMillis() - now));
        });

        EventBus.get().publish(new ThumbnailsLoadedEvent(0, mediaFiles.size()));
        AtomicInteger processedFiles = new AtomicInteger();

        for (File file : mediaFiles) {
            MEDIA_TYPE type = Controller.isImageFile(file) ? MEDIA_TYPE.IMAGE : Controller.isVideoFile(file) ? MEDIA_TYPE.VIDEO : null;
            if (type == null) {
                publishProgress(processedFiles.incrementAndGet(), mediaFiles.size());
                continue;
            }

            try {
                if (type == MEDIA_TYPE.IMAGE) {
                    if (RangeHandler.getInstance().getTotalLength(file) > 0) {
                        publishProgress(processedFiles.incrementAndGet(), mediaFiles.size());
                        continue;
                    }
                } else {
                    try {
                        int minDuration = AppState.get().getMinimunDuration();
                        int actualDuration = RangeHandler.getInstance().getTotalLength(file);

                        if (actualDuration > 0 && actualDuration < minDuration) {
                            publishProgress(processedFiles.incrementAndGet(), mediaFiles.size());
                            continue;
                        }


                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception es) {
                es.printStackTrace();
            }

            int initialFrameCount = type == MEDIA_TYPE.IMAGE ? ANIMATION_FRAMES_PER_THUMBNAIL : INITIAL_VIDEO_THUMBNAIL_FRAMES;
            CompletableFuture.supplyAsync(() -> type == MEDIA_TYPE.IMAGE ? loadImageThumbnail(file) : loadThumbnails(file, initialFrameCount), Controller.getInstance().getExecutorService()).thenAccept(thumbFiles -> {
                if (generation != currentGenerationId) return;
                int done = processedFiles.incrementAndGet();
                publishProgress(done, mediaFiles.size());
                if (thumbFiles != null && !thumbFiles.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        if (generation != currentGenerationId) return;
                        if (scrollPane.getViewport().getView() == loadingLabel) {
                            scrollPane.setViewportView(newGridPanel);
                        }
                        addThumbnailLabelTo(newGridPanel, type, thumbFiles, file);
                        thumbnailsLoadedCount++;
                    });
                }
            });
        }

    }

    private void publishProgress(int loaded, int total) {
        if (loaded == total || loaded % PROGRESS_UPDATE_STEP == 0) {
            EventBus.get().publish(new ThumbnailsLoadedEvent(loaded, total));
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

    private void addThumbnailLabelTo(JPanel panel, MEDIA_TYPE type, List<File> thumbnailFiles, File file) {
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
        label.setOpaque(true);
        label.setBackground(Color.DARK_GRAY);
        label.putClientProperty("file", file);
        label.addMouseListener(mouseListener);

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
                                image = ImageIO.read(AppState.get().getFileForCurrentDirectory(file));
                                H.out("setting preloaded image " + file.getName());
                                AppState.get().setPreloadedImage(image);
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
            if (rotation == 0 && !thumbnailFiles.isEmpty()) {
                label.setIcon(new ImageIcon(thumbnailFiles.get(0).getAbsolutePath()));
            } else {
                CompletableFuture.runAsync(() -> {
                    try {

                        BufferedImage original;

                        File resolved = AppState.get().getFileForCurrentDirectory(file);

                        if (file.getName().toLowerCase().endsWith(".mpo")) {
                            // Nur linkes Frame laden, perfekt für Thumbnails
                            original = MpoReader.getLeftFrame(resolved);
                        } else {
                            // Normales Bild laden
                            original = ImageIO.read(resolved);
                        }

                        if (original == null) {
                            H.out("Problems remain " + file.getAbsoluteFile().toPath());
                            return;
                        }

                        BufferedImage rotated = H.rotate(original, rotation);
                        Image scaled = getScaledImagePreserveRatio(rotated, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);

                        // UI-Update gehört auf den Swing-Thread!
                        SwingUtilities.invokeLater(() -> label.setIcon(new ImageIcon(scaled)));

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }, Controller.getInstance().getExecutorService());
            }
        } else {
            Image image = Toolkit.getDefaultToolkit().getImage(thumbnailFiles.get(0).getAbsolutePath());
            label.setIcon(new ImageIcon(image));
        }

        if (imagedOK) {
            panel.add(label);

            AnimatedThumbnail aNail = new AnimatedThumbnail();
            aNail.imageFiles = thumbnailFiles;
            aNail.animationTimer = null;
            aNail.label = label;
            aNail.isRunning = false;
            aNail.type = type;
            aNail.filename = file.getName();
            animatedThumbnails.add(aNail);
            requestThumbnailUiRefresh(panel);

            if (animatedThumbnails.size() < 20) {
                aNail.start();
                aNail.preload(PRELOAD_FRAMES_FOR_NEW_VIDEO_THUMBNAIL);
            }

            if (type == MEDIA_TYPE.VIDEO) {
                loadRemainingVideoFramesAsync(aNail, file);
            }
        }
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
        updateVisibleThumbnails();
    }

    private void loadRemainingVideoFramesAsync(AnimatedThumbnail thumbnail, File file) {
        CompletableFuture
                .supplyAsync(() -> loadThumbnails(file, ANIMATION_FRAMES_PER_THUMBNAIL), Controller.getInstance().getExecutorService())
                .thenAccept(thumbFiles -> {
                    if (thumbFiles == null || thumbFiles.isEmpty()) return;

                    SwingUtilities.invokeLater(() -> {
                        if (!animatedThumbnails.contains(thumbnail)) return;

                        boolean wasRunning = thumbnail.isRunning;
                        if (wasRunning) {
                            thumbnail.stop();
                        }

                        thumbnail.imageFiles = thumbFiles;

                        if (wasRunning) {
                            thumbnail.start();
                            thumbnail.preload(PRELOAD_FRAMES_FOR_NEW_VIDEO_THUMBNAIL);
                        }
                    });
                });
    }

    public void invalidateThumbnails(File videoFile) {
        File[] cachedFiles = getThumbnailCacheDir().listFiles((dir, name) -> name.endsWith(videoFile.getName() + ".jpg"));
        if (cachedFiles != null) {
            for (File f : cachedFiles) {
                f.delete();
            }
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
        if (!getThumbnailCacheDir().exists()) {
            getThumbnailCacheDir().mkdirs();
        }

        String nameInCache = milli + "_" + videoFile.getName() + ".jpg";
        totalFramesLoaded++;
        File file = new File(getThumbnailCacheDir(), nameInCache);
        if (file.exists()) {
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

            return file.exists() ? file : null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
