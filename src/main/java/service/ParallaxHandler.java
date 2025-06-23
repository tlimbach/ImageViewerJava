package service;

import event.ParalaxChangedEvent;
import event.VolumeChangedEvent;
import model.AppState;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

public class ParallaxHandler {

    private static final ParallaxHandler instance = new ParallaxHandler();

    private JSONObject data;
    private final Timer timer = new Timer("ParallaxDebounce", true);
    private TimerTask pendingTask;
    private ParallaxHandler() {
        load();
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

    private File getParallaxSettingsFile() {
        Path settingsDir = AppState.get().getSettingsDirectory();
        return settingsDir != null
                ? settingsDir.resolve("parallax_settings.json").toFile()
                : new File("parallax_settings.json");
    }

    private void load() {
        File file = getParallaxSettingsFile();
        if (!file.exists()) {
            data = new JSONObject();
            return;
        }

        try (InputStream is = new FileInputStream(file)) {
            JSONTokener tokener = new JSONTokener(is);
            data = new JSONObject(tokener);
        } catch (Exception e) {
            e.printStackTrace();
            data = new JSONObject();
        }

        cleanup();
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(getParallaxSettingsFile().toPath())) {
            writer.write(data.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void cleanup() {
        Path currentDir = AppState.get().getCurrentDirectory();
        if (currentDir == null) return;

        boolean modified = false;

        Iterator<String> iter = data.keySet().iterator();
        while (iter.hasNext()) {
            String filename = iter.next();
            Path filePath = currentDir.resolve(filename);
            if (!Files.exists(filePath)) {
                iter.remove();
                modified = true;
            }
        }

        if (modified) {
            save();
            System.out.println("[ParallaxHandler] Ungültige Einträge in parallax_settings.json entfernt.");
        }
    }
}