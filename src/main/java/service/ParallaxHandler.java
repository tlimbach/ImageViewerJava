package service;

import event.CurrentDirectoryChangedEvent;
import event.ParalaxChangedEvent;
import model.AppState;

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;

public class ParallaxHandler extends JsonSettingsStore {

    private static final ParallaxHandler instance = new ParallaxHandler();

    private final Timer timer = new Timer("ParallaxDebounce", true);
    private TimerTask pendingTask;

    private ParallaxHandler() {
        load();
        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> load());
    }

    public static ParallaxHandler getInstance() {
        return instance;
    }

    public double getParallaxForFile(File file) {
        return data.optDouble(file.getName(), 0.0);
    }

    public void setParallaxForCurrentFile(double parallax) {
        File current = AppState.get().getCurrentFile();
        if (current == null) return;

        data.put(current.getName(), parallax);
        save();

        // Debounce: vorherigen Task abbrechen
        if (pendingTask != null) {
            pendingTask.cancel();
        }

        // neuen Task planen
        pendingTask = new TimerTask() {
            @Override
            public void run() {
                EventBus.get().publish(new ParalaxChangedEvent(parallax));
                System.out.println("[ParallaxHandler] Parallax EVENT ausgelöst: " + parallax + " für " + current.getName());
            }
        };
        timer.schedule(pendingTask, 100);
    }

    @Override
    protected String settingsFileName() {
        return "parallax_settings.json";
    }

    @Override
    protected String cleanupLogMessage() {
        return "[ParallaxHandler] Ungültige Einträge in parallax_settings.json entfernt.";
    }
}
