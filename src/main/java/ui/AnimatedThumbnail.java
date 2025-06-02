package ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
        System.out.println("starting animation for " + filename);
        if (isRunning || imageFiles == null || imageFiles.isEmpty()) return;

        // Icons nur einmal laden
        cachedIcons = new ArrayList<>();
        for (File file : imageFiles) {
            Image image = Toolkit.getDefaultToolkit().getImage(file.getAbsolutePath());
            cachedIcons.add(new ImageIcon(image));
        }

        animationTimer = new Timer(ThumbnailPanel.ANIMATION_DELAY_PLAYBACK, e -> {
            label.setIcon(cachedIcons.get(currentIndex % cachedIcons.size()));
            currentIndex++;
        });

        animationTimer.setRepeats(true);
        animationTimer.start();
        isRunning = true;
    }

    public void stop() {
        System.out.println("Stopping animation for " + filename);
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
        isRunning = false;
        cachedIcons = null; // Speicher wieder freigeben
    }
}