package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AnimatedThumbnail {
    JLabel label;
    List<File> imageFiles;
    Timer animationTimer;
    boolean isRunning = false;
    MEDIA_TYPE type;

    public String filename;

    private int currentIndex = 0;
    private List<ImageIcon> cachedIcons;

    public void start() {
        if (type == MEDIA_TYPE.IMAGE) return;
        if (isRunning || imageFiles == null || imageFiles.isEmpty()) return;

        isRunning = true;
        currentIndex = 0;

        // Initialisiere Liste mit der richtigen Größe
        cachedIcons = new java.util.ArrayList<>();
        for (int i = 0; i < imageFiles.size(); i++) cachedIcons.add(null);

        animationTimer = new Timer(ThumbnailPanel.ANIMATION_DELAY_PLAYBACK, e -> {
            int idx = currentIndex % imageFiles.size();
            showIcon(idx);
            currentIndex++;
        });

        animationTimer.setRepeats(true);
        animationTimer.start();
    }

    private void showIcon(int idx) {

        if (!isRunning)
           return;

        ImageIcon cached = cachedIcons.get(idx);
        if (cached != null) {
            label.setIcon(cached);
        } else {
            // Asynchron laden, aber nur einmal pro Index
            CompletableFuture.supplyAsync(() -> {
                try {
                    BufferedImage img = javax.imageio.ImageIO.read(imageFiles.get(idx));
                    if (img != null) {
                        return new ImageIcon(img);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }).thenAccept(icon -> {
                if (icon != null && isRunning) {
                    cachedIcons.set(idx, icon);
                    SwingUtilities.invokeLater(() -> {
                        if ((currentIndex - 1) % imageFiles.size() == idx) {
                            // Zeige nur dann das Bild, wenn es gerade dran ist
                            label.setIcon(icon);
                        }
                    });
                }
            });
        }
    }

    public void stop() {

        isRunning = false;

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
    }
}