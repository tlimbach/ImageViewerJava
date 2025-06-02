package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

class AnimatedThumbnail {
    JLabel label;
    List<File> imageFiles;
    Timer animationTimer;
    boolean isRunning = false;
    MEDIA_TYPE type;

    String filename;

    private int currentIndex = 0;
    private List<ImageIcon> cachedIcons = null;

    public void start() {
        if (type == MEDIA_TYPE.IMAGE) return;
        if (isRunning || imageFiles == null || imageFiles.isEmpty()) return;

        System.out.println("starting animation for " + filename);

        isRunning = true;

        // Lade Bilder asynchron
        CompletableFuture.supplyAsync(() -> {
            List<ImageIcon> icons = new ArrayList<>();
            for (File file : imageFiles) {
                if (file == null) continue;
                try {
                    BufferedImage img = javax.imageio.ImageIO.read(file);
                    if (img != null) {
                        icons.add(new ImageIcon(img));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return icons;
        }).thenAccept(icons -> {
            SwingUtilities.invokeLater(() -> {
                if (!isRunning || icons.isEmpty()) return;

                cachedIcons = icons;
                currentIndex = 0;

                animationTimer = new Timer(ThumbnailPanel.ANIMATION_DELAY_PLAYBACK, e -> {
                    if (cachedIcons != null && !cachedIcons.isEmpty()) {
                        label.setIcon(cachedIcons.get(currentIndex % cachedIcons.size()));
                        currentIndex++;
                    }
                });

                animationTimer.setRepeats(true);
                animationTimer.start();
            });
        });
    }

    public void stop() {
        System.out.println("Stopping animation for " + filename);

        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }

        if (cachedIcons != null) {
            for (ImageIcon icon : cachedIcons) {
                if (icon != null && icon.getImage() != null) {
                    icon.getImage().flush();
                }
            }
            cachedIcons.clear();
            cachedIcons = null;
        }

        isRunning = false;
    }
}