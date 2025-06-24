package ui;

import service.Controller;
import service.ThumbnailCache;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

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
            CompletableFuture.supplyAsync(() -> {
                byte[] bytes = ThumbnailCache.getByteArray(imageFiles.get(idx));
                if (bytes == null) return null;
                try {
                    BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
                    return img != null ? new ImageIcon(img) : null;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }).thenAccept(icon -> {
                if (icon != null && isRunning) {
                    cachedIcons.set(idx, icon);
                    SwingUtilities.invokeLater(() -> {
                        if ((currentIndex - 1) % imageFiles.size() == idx) {
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

    public void preload() {
        ExecutorService executor = Controller.getInstance().getExecutorService();
        for (File file : imageFiles) {
            executor.submit(() -> ThumbnailCache.getByteArray(file));
        }
    }
}