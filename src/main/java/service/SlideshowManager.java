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
    private long heldImageRemainingMillis;
    private long interactionHoldStarted;
    private boolean interactionHold;
    private File originalSelectedFile;

    private final Timer repeatCheckTimer = new Timer(500, e -> checkRepeatVideo());

    public void start(List<File> files, int durationSeconds, int totalDurationMinutes) {
        if (files == null || files.isEmpty()) return;

        stopTimersOnly();
        this.originalSelectedFile = AppState.get().getCurrentFile();
        this.files = new java.util.ArrayList<>(files);
        java.util.Collections.shuffle(this.files);
        this.durationSeconds = durationSeconds;
        this.totalEndTime = System.currentTimeMillis() + totalDurationMinutes * 60_000L;
        this.currentIndex = 0;
        this.running = true;
        this.interactionHold = false;
        this.heldImageRemainingMillis = 0;
        this.interactionHoldStarted = 0;

        mediaView.resetSlideshowTransitionState();
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
            scheduleNext(durationSeconds * 1000L);
        } else if (Controller.isVideoFile(file)) {
            mediaView.display(file, true);
            mediaView.getLeftBar().start(durationSeconds * 1000L);
        }
    }

    private void scheduleNext(long delayMillis) {
        if (slideshowTimer != null) {
            slideshowTimer.stop();
        }

        slideshowTimer = new Timer((int) Math.max(1, delayMillis), e -> {
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
        interactionHold = false;
        stopTimersOnly();
        mediaView.stop();
        mediaView.getLeftBar().pause();
        mediaView.stopSlideshowProgress();
        mediaView.resetSlideshowTransitionState();
        restoreOriginalThumbnailSelection();
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

    public void holdImageForInteraction() {
        if (!running || interactionHold || files == null || currentIndex >= files.size()) return;
        File current = files.get(currentIndex);
        if (!Controller.isImageFile(current)) return;

        interactionHold = true;
        interactionHoldStarted = System.currentTimeMillis();
        heldImageRemainingMillis = Math.max(1000, endTime - interactionHoldStarted);

        if (slideshowTimer != null) {
            slideshowTimer.stop();
        }
        mediaView.getLeftBar().pause();
        mediaView.pauseSlideshowProgress();
    }

    public void resumeAfterInteraction() {
        if (!running || !interactionHold) return;

        long now = System.currentTimeMillis();
        totalEndTime += Math.max(0, now - interactionHoldStarted);
        endTime = now + heldImageRemainingMillis;
        interactionHold = false;

        mediaView.getLeftBar().resume(heldImageRemainingMillis);
        mediaView.resumeSlideshowProgress(Math.max(1000, totalEndTime - now));
        scheduleNext(heldImageRemainingMillis);
    }

    public long getDurationMillis() {
        return durationSeconds*1000;
    }

    private void restoreOriginalThumbnailSelection() {
        if (originalSelectedFile == null) return;
        if (Controller.getInstance().getThumbnailPanel() == null) return;

        Controller.getInstance().getThumbnailPanel().selectFileThumbnail(originalSelectedFile);
    }
}
