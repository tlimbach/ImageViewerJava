package ui;

import service.Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ThumbnailPanel extends JPanel {

    private final JPanel gridPanel;
    private final JScrollPane scrollPane;

    private final static int THUMBNAIL_SIZE = 400;

    public ThumbnailPanel() {
        setLayout(new BorderLayout());

        gridPanel = new JPanel(new GridLayout(0, 3, 5, 5)); // 4 Spalten, variable Zeilen, Lücken: 10px
        scrollPane = new JScrollPane(gridPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        add(scrollPane, BorderLayout.CENTER);
    }

    int thumbnailsLoadedCount = 0;

    public void populate(List<File> mediaFiles) {
        System.out.println("Populating " + mediaFiles.size() + " files");
        gridPanel.removeAll();

        for (File file : mediaFiles) {
            if (Controller.isImageFile(file)) {
                // Bilder sofort synchron laden (geht schnell)
                ImageIcon icon = new ImageIcon(file.getAbsolutePath());
                Image scaled = getScaledImagePreserveRatio(icon.getImage(), THUMBNAIL_SIZE, THUMBNAIL_SIZE);
                addThumbnailLabel(scaled, file);
                thumbnailsLoadedCount++;
                Controller.getInstance().setThumbnailsLoaded(thumbnailsLoadedCount, mediaFiles.size());
            } else if (Controller.isVideoFile(file)) {
                // Videos asynchron laden
                CompletableFuture.supplyAsync(() -> extractVideoThumbnail(file), Controller.getInstance().getExecutor()).thenAccept(thumbFile -> {
                    thumbnailsLoadedCount++;
                    if (thumbFile != null && thumbFile.exists()) {
                        ImageIcon icon = new ImageIcon(thumbFile.getAbsolutePath());
                        Image scaled = getScaledImagePreserveRatio(icon.getImage(), THUMBNAIL_SIZE, THUMBNAIL_SIZE);
                        SwingUtilities.invokeLater(() -> {
                            addThumbnailLabel(scaled, file);
                        });
                        Controller.getInstance().setThumbnailsLoaded(thumbnailsLoadedCount, mediaFiles.size());
                    }
                });
            }
        }

      //  gridPanel.revalidate();
      //  gridPanel.repaint();
    }

    private void addThumbnailLabel(Image image, File file) {
        JLabel label = new JLabel(new ImageIcon(image));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Controller.getInstance().handleMedia(file);
            }
        });
        gridPanel.add(label);
        //gridPanel.revalidate();
        //gridPanel.repaint();
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

    private File extractVideoThumbnail(File videoFile) {
        try {
            File thumb = File.createTempFile("thumb_", ".png");
            String[] cmd = {"ffmpeg", "-y", "-i", videoFile.getAbsolutePath(), "-ss", "00:00:10.000", "-vframes", "1", thumb.getAbsolutePath()};
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO().start().waitFor();
            return thumb;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}