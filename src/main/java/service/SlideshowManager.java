package service;

import event.CurrentlySelectedFileEvent;
import event.MediaviewPlayEvent;
import model.AppState;
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
    private boolean moveImages;

    public void start(List<File> files, int durationSeconds, boolean moveImages) {
        this.moveImages = moveImages;
        if (files == null || files.isEmpty()) return;

        this.files = new java.util.ArrayList<>(files);
        java.util.Collections.shuffle(this.files);
        this.durationSeconds = durationSeconds;
        this.currentIndex = 0;
        this.running = true;

        showCurrent();
        repeatCheckTimer.start();
    }

    private void showCurrent() {
        if (!running) return;
        if (files == null || files.isEmpty()) return;
        if (currentIndex >= files.size()) {
            java.util.Collections.shuffle(files);
            currentIndex = 0;
        }

        File file = files.get(currentIndex);
        AppState.get().setCurrentFile(file);
        EventBus.get().publish(new MediaviewPlayEvent(true));

        endTime = System.currentTimeMillis() + durationSeconds * 1000L;

        if (Controller.isImageFile(file)) {
            mediaView.display(file, false);
            scheduleNext();
        } else if (Controller.isVideoFile(file)) {
            mediaView.display(file, true);
            mediaView.getLeftBar().start(durationSeconds * 1000L);
            EventBus.get().publish(new CurrentlySelectedFileEvent(file));
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

    public long getDurationMillis() {
        return durationSeconds*1000;
    }


    public boolean isMoveImages() {
        return moveImages;
    }
}
