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
    private long totalEndTime;
    private List<File> files;
    private int currentIndex;
    private boolean running;
    private final MediaView mediaView = MediaView.getInstance();

    private final Timer repeatCheckTimer = new Timer(500, e -> checkRepeatVideo());
    private boolean moveImages;

    public void start(List<File> files, int durationSeconds, int totalDurationMinutes, boolean moveImages) {
        this.moveImages = moveImages;
        if (files == null || files.isEmpty()) return;

        stopTimersOnly();
        this.files = new java.util.ArrayList<>(files);
        java.util.Collections.shuffle(this.files);
        this.durationSeconds = durationSeconds;
        this.totalEndTime = System.currentTimeMillis() + totalDurationMinutes * 60_000L;
        this.currentIndex = 0;
        this.running = true;

        mediaView.startSlideshowProgress(totalDurationMinutes * 60_000L);
        showCurrent();
        repeatCheckTimer.start();
    }

    private void showCurrent() {
        if (!running) return;
        if (isTotalDurationReached()) {
            stop();
            return;
        }
        if (files == null || files.isEmpty()) return;
        if (currentIndex >= files.size()) {
            java.util.Collections.shuffle(files);
            currentIndex = 0;
        }

        File file = files.get(currentIndex);
        AppState.get().setCurrentFile(file);
        EventBus.get().publish(new CurrentlySelectedFileEvent(file));
        EventBus.get().publish(new MediaviewPlayEvent(true));

        endTime = System.currentTimeMillis() + durationSeconds * 1000L;

        if (Controller.isImageFile(file)) {
            mediaView.display(file, false);
            scheduleNext();
        } else if (Controller.isVideoFile(file)) {
            mediaView.display(file, true);
            mediaView.getLeftBar().start(durationSeconds * 1000L);
        }
    }

    private void scheduleNext() {
        if (slideshowTimer != null) {
            slideshowTimer.stop();
        }

        slideshowTimer = new Timer(durationSeconds * 1000, e -> {
            if (isTotalDurationReached()) {
                stop();
                return;
            }
            currentIndex++;
            showCurrent();
        });
        slideshowTimer.setRepeats(false);
        slideshowTimer.start();
    }

    private void checkRepeatVideo() {
        if (!running || currentIndex >= files.size()) return;
        if (isTotalDurationReached()) {
            stop();
            return;
        }

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
        stopTimersOnly();
        mediaView.stop();
        mediaView.getLeftBar().stop();
        mediaView.stopSlideshowProgress();
    }

    private void stopTimersOnly() {
        if (slideshowTimer != null) slideshowTimer.stop();
        repeatCheckTimer.stop();
    }

    private boolean isTotalDurationReached() {
        return totalEndTime > 0 && System.currentTimeMillis() >= totalEndTime;
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
