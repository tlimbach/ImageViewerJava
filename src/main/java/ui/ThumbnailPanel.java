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

    private final static int THUMBNAIL_SIZE = 400;

    /**
     * Anzahl Bilder pro Thumbnail für die Animation
     */
    private final static int ANIMATION_FRAMES_PER_THUMBNAIL = 35;


    /**
     * Delay in ms zwischen zwei angezeigten Bildern beim Playback der Animation
     */
    private final static int ANIMATION_DELAY_PLAYBACK = 130;


    /**
     * Delay in ms beim Laden der Bilder der Animation
     */
    private final static int ANIMATION_DELAY_RECORD = 80;


    private final JPanel gridPanel;
    private final JScrollPane scrollPane;
    private boolean thumbnailLoadingCompleted = false;


    public ThumbnailPanel() {
        setLayout(new BorderLayout());

        gridPanel = new JPanel(new GridLayout(0, 3, 5, 5)); // 4 Spalten, variable Zeilen, Lücken: 10px
        scrollPane = new JScrollPane(gridPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        add(scrollPane, BorderLayout.CENTER);
    }

    int thumbnailsLoadedCount = 0;

    int totalFramesLoaded = 0;
    int framesFromCache =0;

    public void populate(List<File> mediaFiles) {
        System.out.println("Populating " + mediaFiles.size() + " files");
        gridPanel.removeAll();

        for (File file : mediaFiles) {
            if (Controller.isImageFile(file)) {
                // Bilder sofort synchron laden (geht schnell)
                ImageIcon icon = new ImageIcon(file.getAbsolutePath());
                Image scaled = getScaledImagePreserveRatio(icon.getImage(), THUMBNAIL_SIZE, THUMBNAIL_SIZE);
                addThumbnailLabel(Collections.singletonList(new ImageIcon(scaled)), file);
                thumbnailsLoadedCount++;
                Controller.getInstance().setThumbnailsLoaded(thumbnailsLoadedCount, mediaFiles.size());
            } else if (Controller.isVideoFile(file)) {
                // Videos asynchron laden
                CompletableFuture.supplyAsync(() -> loadThumbnails(file, 30000, ANIMATION_FRAMES_PER_THUMBNAIL  ), Controller.getInstance().getExecutor()).thenAccept(thumbFiles -> {
                    thumbnailsLoadedCount++;
                    if (thumbFiles != null && thumbFiles.size()>0)
                    {
                        List<ImageIcon> animationImages = new ArrayList<>();
                        thumbFiles.forEach(f-> {
                            ImageIcon icon = new ImageIcon(f.getAbsolutePath());
                            Image scaled = getScaledImagePreserveRatio(icon.getImage(), THUMBNAIL_SIZE, THUMBNAIL_SIZE);
                            animationImages.add(new ImageIcon(scaled));
                        });

                        SwingUtilities.invokeLater(() -> {
                            addThumbnailLabel(animationImages, file);
                        });
                        Controller.getInstance().setThumbnailsLoaded(thumbnailsLoadedCount, mediaFiles.size());

                        thumbnailLoadingCompleted = thumbnailsLoadedCount == mediaFiles.size();

                        H.out("total frames " + totalFramesLoaded + " from cache " + framesFromCache);
                    }
                });
            }
        }

      //  gridPanel.revalidate();
      //  gridPanel.repaint();
    }

    private void addThumbnailLabel(List<ImageIcon> images, File file) {
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

        // Animation via Swing Timer
        final int[] index = {0};
        Timer timer = new Timer(ANIMATION_DELAY_PLAYBACK, e -> {
            if (index[0] > 0 && !thumbnailLoadingCompleted) {
                return; // Animation pausieren, bis alles geladen ist
            }

            label.setIcon(images.get(index[0] % images.size()));
            index[0]++;
        });

        timer.setRepeats(true);
        timer.start();
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

    private static final File THUMBNAIL_CACHE_DIR = new File("thumbnails");


    private List<File> loadThumbnails(File videoFile, int startMillis, int count) {
        int millis = startMillis;
        List<File> files = new ArrayList<>();
        for (int t=0; t<count; t++) {
            files.add(extractVideoThumbnail(videoFile, millis));
            millis+= ANIMATION_DELAY_RECORD;
        }
        return files;
    }

    private File extractVideoThumbnail(File videoFile, int milli) {
        try {
            if (!THUMBNAIL_CACHE_DIR.exists()) {
                THUMBNAIL_CACHE_DIR.mkdirs();
            }

            String hash = Integer.toHexString(videoFile.getAbsolutePath().hashCode());
            File cachedThumbnail = new File(THUMBNAIL_CACHE_DIR, hash + "_" + milli + "_.png");
totalFramesLoaded++;
            if (cachedThumbnail.exists()) {
                framesFromCache++;
                return cachedThumbnail;
            }

            // Millisekunden in hh:mm:ss.SSS umwandeln
            int totalSeconds = milli / 1000;
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int seconds = totalSeconds % 60;
            int ms = milli % 1000;

            String timestamp = String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);

            String[] cmd = {
                    "ffmpeg", "-y",
                    "-ss", timestamp,
                    "-i", videoFile.getAbsolutePath(),
                    "-vframes", "1",
                    cachedThumbnail.getAbsolutePath()
            };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO().start().waitFor();

            return cachedThumbnail.exists() ? cachedThumbnail : null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}