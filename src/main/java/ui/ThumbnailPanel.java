package ui;

import service.Controller;
import service.H;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ThumbnailPanel extends JPanel {

    private final static int THUMBNAIL_SIZE = 470;

    /**
     * Anzahl Bilder pro Thumbnail für die Animation
     */
    private final static int ANIMATION_FRAMES_PER_THUMBNAIL = 35;


    /**
     * Delay in ms zwischen zwei angezeigten Bildern beim Playback der Animation
     */
    public final static int ANIMATION_DELAY_PLAYBACK = 130;


    /**
     * Delay in ms beim Laden der Bilder der Animation
     */
    private final static int ANIMATION_DELAY_RECORD = 80;


    private final JPanel gridPanel;
    private final JScrollPane scrollPane;
    private boolean thumbnailLoadingCompleted = false;

    private static final File THUMBNAIL_CACHE_DIR = new File("thumbnails");
    public ThumbnailPanel() {
        setLayout(new BorderLayout());

        gridPanel = new JPanel(new GridLayout(0, 3, 5, 5)); // 4 Spalten, variable Zeilen, Lücken: 10px
        scrollPane = new JScrollPane(gridPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.getViewport().addChangeListener(e -> updateVisibleThumbnails());
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);
    }

    void updateVisibleThumbnails() {
        Rectangle view = scrollPane.getViewport().getViewRect();
        for (AnimatedThumbnail thumb : animatedThumbnails) {
            boolean visible = view.intersects(thumb.label.getBounds());
            if (visible && !thumb.isRunning) {
                thumb.start();
            } else if (!visible && thumb.isRunning) {
                thumb.stop();
            }
        }
    }

    List<AnimatedThumbnail> animatedThumbnails = new ArrayList<>();

    int thumbnailsLoadedCount = 0;

    int totalFramesLoaded = 0;
    int framesFromCache = 0;

    public void populate(List<File> mediaFiles) {
        System.out.println("Populating " + mediaFiles.size() + " files");
        gridPanel.removeAll();

        for (File file : mediaFiles) {
            if (Controller.isImageFile(file)) {
                // Bilder sofort synchron laden (geht schnell)
                ImageIcon icon = new ImageIcon(file.getAbsolutePath());
                Image scaled = getScaledImagePreserveRatio(icon.getImage(), THUMBNAIL_SIZE, THUMBNAIL_SIZE);
                addThumbnailLabel(MEDIA_TYPE.IMAGE, Collections.singletonList(file), file);
                thumbnailsLoadedCount++;
                Controller.getInstance().setThumbnailsLoaded(thumbnailsLoadedCount, mediaFiles.size());
            } else if (Controller.isVideoFile(file)) {
                // Videos asynchron laden
                CompletableFuture.supplyAsync(() -> loadThumbnails(file, 1000, ANIMATION_FRAMES_PER_THUMBNAIL), Controller.getInstance().getExecutor()).thenAccept(thumbFiles -> {
                    thumbnailsLoadedCount++;
                    if (thumbFiles != null && thumbFiles.size() > 0) {
//                        List<ImageIcon> animationFiles = new ArrayList<>();
//                        thumbFiles.forEach(f -> {
//                            Image image = Toolkit.getDefaultToolkit().getImage(f.getAbsolutePath());
//                            Image scaled = getScaledImagePreserveRatio(image, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
//                            animationFiles.add(new ImageIcon(scaled));
//                        });

                        SwingUtilities.invokeLater(() -> {
                            addThumbnailLabel(MEDIA_TYPE.VIDEO, thumbFiles, file);
                        });
                        Controller.getInstance().setThumbnailsLoaded(thumbnailsLoadedCount, mediaFiles.size());

                        thumbnailLoadingCompleted = true;

                        H.out("total frames " + totalFramesLoaded + " from cache " + framesFromCache);

                    }
                });
            }
        }

        //  gridPanel.revalidate();
        //  gridPanel.repaint();
    }



    private void addThumbnailLabel(MEDIA_TYPE type, List<File> thumbnailFiles, File file) {
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(THUMBNAIL_SIZE, THUMBNAIL_SIZE));
        label.setOpaque(true);
        label.setBackground(Color.DARK_GRAY);

        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Controller.getInstance().handleMedia(file);
            }
        });

        gridPanel.add(label);
        Image image = Toolkit.getDefaultToolkit().getImage(thumbnailFiles.get(0).getAbsolutePath());
        label.setIcon(new ImageIcon(image));

        AnimatedThumbnail aNail = new AnimatedThumbnail();
        aNail.imageFiles = thumbnailFiles;
        aNail.animationTimer = null;
        aNail.label = label;
        aNail.isRunning=false;
        aNail.type=type;
        aNail.filename = file.getName();
        animatedThumbnails.add(aNail);

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

        return srcImg.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
    }




    private List<File> loadThumbnails(File videoFile, int startMillis, int count) {
        int millis = startMillis;
        List<File> files = new ArrayList<>();
        for (int t = 0; t < count; t++) {
            files.add(fetchVideoThumbnail(videoFile, millis));
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
            // Millisekunden in hh:mm:ss.SSS umwandeln
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
                    "-vf", "scale='400:trunc(ih*400/iw/2)*2'",
                    file.getAbsolutePath()
            };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO().start().waitFor();

            return file.exists() ? file : null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}