package ui;

import event.CurrentDirectoryChangedEvent;
import event.RangeChangedEvent;
import event.ThumbnailsLoadedEvent;
import model.AppState;
import service.Controller;
import service.EventBus;
import service.MediaService;
import service.RangeHandler;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

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


    private static final File THUMBNAIL_CACHE_DIR = new File("thumbnails");


    public ThumbnailPanel() {
        setLayout(new BorderLayout());
        scrollPane = new JScrollPane();
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.getViewport().addChangeListener(e -> updateVisibleThumbnails());
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> {
            List<File> files = MediaService.getInstance().loadFilesFromDirectory();
            populate(files);
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

    public void populate(List<File> mediaFiles) {
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

        // Viewport-Listener an neuen Panel binden
        scrollPane.setViewportView(newGridPanel);

        for (File file : mediaFiles) {
            if (Controller.isImageFile(file)) {
                addThumbnailLabelTo(newGridPanel, MEDIA_TYPE.IMAGE, Collections.singletonList(file), file);
                thumbnailsLoadedCount++;
                EventBus.get().publish(new ThumbnailsLoadedEvent(thumbnailsLoadedCount, mediaFiles.size()));
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
                                    updateVisibleThumbnails();  // sicherheitshalber
                                });
                            }
                        });
            }
        }

//        SwingUtilities.invokeLater(() -> {
//            newGridPanel.revalidate();
//            newGridPanel.repaint();
//            updateVisibleThumbnails();
//        });
    }

    private void addThumbnailLabelTo(JPanel panel, MEDIA_TYPE type, List<File> thumbnailFiles, File file) {
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
        label.setOpaque(true);
        label.setBackground(Color.DARK_GRAY);

        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {

                if (SwingUtilities.isRightMouseButton(e)) {
                    // Kontextmenü erstellen
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem deleteItem = new JMenuItem("Bild löschen");

                    deleteItem.addActionListener(ev -> {
                        int result = JOptionPane.showConfirmDialog(label,
                                "Bild wirklich löschen?\n" + file.getName(),
                                "Löschen bestätigen",
                                JOptionPane.YES_NO_OPTION);

                        if (result == JOptionPane.YES_OPTION) {
                            if (file.delete()) {
                                Controller.getInstance().handleDirectory(AppState.get().getCurrentDirectory());
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
                    return; // Kein weiterer Klickhandling bei Rechtsklick
                }

                if (selectedLabel != null) {
                    selectedLabel.setBorder(null);  // vorherige Auswahl entfernen
                }

                selectedLabel = label;
                selectedLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 4));

                if (e.getClickCount() == 1) {
                    AppState.get().setCurrentFile(file);
                    Controller.getInstance().handleMedia(file, false);
                } else if (e.getClickCount() == 2) {
                    AppState.get().setCurrentFile(file);
                    Controller.getInstance().handleMedia(file, true);
                }
            }
        });

        panel.add(label);

        if (type == MEDIA_TYPE.IMAGE) {
            try {
                BufferedImage original = ImageIO.read(file);
                Image scaled = getScaledImagePreserveRatio(original, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
                label.setIcon(new ImageIcon(scaled));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Image image = Toolkit.getDefaultToolkit().getImage(thumbnailFiles.get(0).getAbsolutePath());
            label.setIcon(new ImageIcon(image));
        }

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
    }

    public void invalidateThumbnails(File videoFile) {
        File[] cachedFiles = THUMBNAIL_CACHE_DIR.listFiles((dir, name) -> name.endsWith(videoFile.getName() + ".jpg"));
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
        if (!THUMBNAIL_CACHE_DIR.exists()) {
            THUMBNAIL_CACHE_DIR.mkdirs();
        }

        String nameInCache = milli + "_" + videoFile.getName() + ".jpg";
        totalFramesLoaded++;
        File file = new File(THUMBNAIL_CACHE_DIR, nameInCache);
        if (file.exists()) {
            framesFromCache++;
            return file;
        }

        file = extractVideoThumbnail(videoFile, milli);
        return file;
    }

    private File extractVideoThumbnail(File videoFile, int milli) {
        try {
            String nameInCache = milli + "_" + videoFile.getName() + ".jpg";
            File file = new File(THUMBNAIL_CACHE_DIR, nameInCache);

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