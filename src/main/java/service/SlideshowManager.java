package service;

import ui.MediaView;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class SlideshowManager {

    private Timer slideshowTimer;
    private long endTime;
    private int durationSeconds;
    private List<File> files;
    private int currentIndex;
    private boolean running;
    private final MediaView mediaView = MediaView.getInstance();

    private final Timer repeatCheckTimer = new Timer(500, e -> checkRepeatVideo());

    public void start(List<File> files, int durationSeconds) {
        if (files == null || files.isEmpty()) return;

        this.files = files;
        this.durationSeconds = durationSeconds;
        this.currentIndex = 0;
        this.running = true;

        showCurrent();
        repeatCheckTimer.start();
    }

    private void showCurrent() {
        if (!running || currentIndex >= files.size()) {
            stop();
            return;
        }

        File file = files.get(currentIndex);
        endTime = System.currentTimeMillis() + durationSeconds * 1000L;

        if (Controller.isImageFile(file)) {
            mediaView.display(file, false);
            scheduleNext();
        } else if (Controller.isVideoFile(file)) {
            mediaView.display(file, true);
        }
    }

    private void scheduleNext() {
        if (slideshowTimer != null) {
            slideshowTimer.stop();
        }

        slideshowTimer = new Timer(durationSeconds * 1000, e -> {
            currentIndex++;
            showCurrent();
        });
        slideshowTimer.setRepeats(false);
        slideshowTimer.start();
    }

    private void checkRepeatVideo() {
        if (!running || currentIndex >= files.size()) return;

        File current = files.get(currentIndex);
        if (Controller.isVideoFile(current)) {
            long now = System.currentTimeMillis();
            if (now >= endTime) {
                currentIndex++;
                showCurrent();
            }
        }
    }

    public void stop() {
        running = false;
        if (slideshowTimer != null) slideshowTimer.stop();
        repeatCheckTimer.stop();
    }

    public boolean isRunning() {
        return running;
    }
} 
