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
import java.util.Collections;
import java.util.List;
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


    public ThumbnailPanel() {


        mouseListener = createMouseListener();

        setLayout(new BorderLayout());
        scrollPane = new JScrollPane();
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.getViewport().addChangeListener(e -> updateVisibleThumbnails());
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> {
            List<File> files = MediaService.getInstance().loadFilesFromDirectory();
            Controller.getInstance().getExecutorService().submit(() -> populate(files));
        });

        EventBus.get().register(RangeChangedEvent.class, e -> {
            invalidateThumbnails(e.file());

            AnimatedThumbnail match = animatedThumbnails.stream()
                    .filter(a -> a.filename.equals(e.file().getName()))
                    .findFirst()
                    .orElse(null);

            if (match != null) {
                CompletableFuture
                        .supplyAsync(() -> loadThumbnails(e.file(), ANIMATION_FRAMES_PER_THUMBNAIL),
                                Controller.getInstance().getExecutorService())
                        .thenAccept(thumbFiles -> {
                            if (thumbFiles != null && !thumbFiles.isEmpty()) {
                                SwingUtilities.invokeLater(() -> {
                                    match.stop();                    // alte Animation stoppen
                                    match.imageFiles = thumbFiles;   // neue Frames setzen
                                    match.start();                   // Animation wieder starten
                                });
                            }
                        });
            }
        });

        EventBus.get().register(RotationChangedEvent.class, e -> {
            SwingUtilities.invokeLater(() -> {
                for (AnimatedThumbnail thumb : animatedThumbnails) {
                    if (thumb.filename.equals(e.file().getName())) {
                        try {
                            BufferedImage original = ImageIO.read(e.file());
                            BufferedImage rotated = H.rotate(original, e.degrees());
                            Image scaled = getScaledImagePreserveRatio(rotated, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
                            thumb.label.setIcon(new ImageIcon(scaled));
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        break;
                    }
                }
            });
        });

        EventBus.get().register(UserKeyboardEvent.class, e -> {

            String direction = e.direction();

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

            switch (direction) {
                case "RIGHT":
                    if (index != -1 && index + 1 < animatedThumbnails.size()) {
                        nextIndex = index + 1;
                    }
                    break;
                case "LEFT":
                    if (index > 0) {
                        nextIndex = index - 1;
                    }
                    break;
                case "UP":
                    if (index - 3 >= 0) {
                        nextIndex = index - 3;
                    }
                    break;
                case "DOWN":
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

                // 🔑 Nur das, korrekt:
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


        });
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
                        int result = JOptionPane.showConfirmDialog(label,
                                "Bild wirklich löschen?\n" + file.getName(),
                                "Löschen bestätigen",
                                JOptionPane.YES_NO_OPTION);

                        if (result == JOptionPane.YES_OPTION) {
                            if (file.delete()) {
                                EventBus.get().publish(new TagsChangedEvent());
                                Controller.getInstance().getExecutorService().submit(() -> reloadDirectory());
                            } else {
                                JOptionPane.showMessageDialog(label,
                                        "Datei konnte nicht gelöscht werden.",
                                        "Fehler",
                                        JOptionPane.ERROR_MESSAGE);
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

    private long currentGenerationId = 0;
    int processed = 0;

    public void populate(List<File> _mediaFiles) {
        List<File> mediaFiles = new ArrayList<>(_mediaFiles);

        long generation = ++currentGenerationId;

        thumbnailsLoadedCount = 0;
        totalFramesLoaded = 0;
        framesFromCache = 0;

        long now = System.currentTimeMillis();
        animatedThumbnails.forEach(AnimatedThumbnail::stop);
        animatedThumbnails.clear();
        System.out.println("took " + (System.currentTimeMillis() - now));

        // Neues GridPanel erzeugen
        JPanel newGridPanel = new JPanel(new GridLayout(0, 3, 5, 5));

        newGridPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                H.out("new pressed :" + e.getKeyCode());
            }

            @Override
            public void keyReleased(KeyEvent e) {
                super.keyReleased(e);
                H.out("jdoiwjdiojwoid");
            }

            @Override
            public void keyTyped(KeyEvent e) {
                super.keyTyped(e);
                H.out("oidjwoidjwoid");
            }
        });


        // Viewport-Listener an neuen Panel binden
        scrollPane.setViewportView(newGridPanel);
        scrollPane.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                H.out("new pressed :" + e.getKeyCode());
            }

            @Override
            public void keyTyped(KeyEvent e) {
                super.keyTyped(e);
                H.out("oidjwoidjwoid");
            }
        });


        for (File file : mediaFiles) {
            if (Controller.isImageFile(file)) {
                CompletableFuture
                        .supplyAsync(() -> loadImageThumbnail(file),
                                Controller.getInstance().getExecutorService())
                        .thenAccept(thumbFiles -> {
                            if (generation != currentGenerationId) return;
                            if (thumbFiles != null && !thumbFiles.isEmpty()) {
                                SwingUtilities.invokeLater(() -> {
                                    if (generation != currentGenerationId) return;
                                    addThumbnailLabelTo(newGridPanel, MEDIA_TYPE.IMAGE, thumbFiles, file);
                                    thumbnailsLoadedCount++;
                                    EventBus.get().publish(new ThumbnailsLoadedEvent(thumbnailsLoadedCount, mediaFiles.size()));
                                });
                            }
                        });
            } else if (Controller.isVideoFile(file)) {
                CompletableFuture
                        .supplyAsync(() -> loadThumbnails(file, ANIMATION_FRAMES_PER_THUMBNAIL),
                                Controller.getInstance().getExecutorService())
                        .thenAccept(thumbFiles -> {
                            if (generation != currentGenerationId) return;
                            if (thumbFiles != null && !thumbFiles.isEmpty()) {
                                SwingUtilities.invokeLater(() -> {
                                    if (generation != currentGenerationId) return;
                                    addThumbnailLabelTo(newGridPanel, MEDIA_TYPE.VIDEO, thumbFiles, file);
                                    thumbnailsLoadedCount++;
                                    EventBus.get().publish(new ThumbnailsLoadedEvent(thumbnailsLoadedCount, mediaFiles.size()));
                                    updateVisibleThumbnails();
                                });
                            }
                        });
            }
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

        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {

                if (type == MEDIA_TYPE.IMAGE) {
                    currentHoverFile = file;

                    new Thread(() -> {
                        // Kurze künstliche Verzögerung, optional:
                        H.sleep(50);

                        // Bin ich noch der aktuelle Hover?
                        if (currentHoverFile != file) return;

                        // Aufwendig laden:
                        try {
                            BufferedImage image;
                            if (file.getName().toLowerCase().endsWith(".mpo")) {
                                image = MpoReader.getLeftFrame(AppState.get().getFileForCurrentDirectory(file));
                            } else {
                                image = ImageIO.read(AppState.get().getFileForCurrentDirectory(file));
                            }
                            H.out("setting preloaded image " + file.getName());
                            AppState.get().setPreloadedImage(image);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }


                    }).start();
                }
            }
        });

        boolean imagedOK = true;

        if (type == MEDIA_TYPE.IMAGE) {
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

                    int rotation = RotationHandler.getInstance().getRotation(file);
                    BufferedImage rotated = H.rotate(original, rotation);
                    Image scaled = getScaledImagePreserveRatio(rotated, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);

                    // UI-Update gehört auf den Swing-Thread!
                    SwingUtilities.invokeLater(() -> label.setIcon(new ImageIcon(scaled)));

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, Controller.getInstance().getExecutorService());
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

            if (animatedThumbnails.size() < 20) {
                aNail.start();
            }
            aNail.preload();
        }
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
        double scale = Math.min(widthRatio, heightRatio);

        int newWidth = (int) (srcWidth * scale);
        int newHeight = (int) (srcHeight * scale);

        BufferedImage result = new BufferedImage(maxWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, maxWidth, maxHeight);
        g.drawImage(srcImg, (maxWidth - newWidth) / 2, (maxHeight - newHeight) / 2, newWidth, newHeight, null);
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

            String[] cmd = {
                    "ffmpeg", "-y",
                    "-loglevel", "error",
                    "-ss", timestamp,
                    "-i", videoFile.getAbsolutePath(),
                    "-vframes", "1",
                    "-vf", "scale='min(" + PREVIEW_IMAGE_WIDTH + "\\,iw)':min(" + PREVIEW_IMAGE_HEIGHT + "\\,ih):force_original_aspect_ratio=decrease,pad=" + PREVIEW_IMAGE_WIDTH + ":" + PREVIEW_IMAGE_HEIGHT + ":(ow-iw)/2:(oh-ih)/2",
                    file.getAbsolutePath()
            };

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