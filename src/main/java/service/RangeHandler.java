package service;

import event.RangeChangedEvent;
import model.AppState;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

public class RangeHandler {


    private JSONObject rangeData;

    private static RangeHandler rangeHandler = new RangeHandler();

    public static RangeHandler getInstance() {
        return rangeHandler;
    }

    private RangeHandler() {
        load();
    }

    public Range getRangeForFile(File file) {
        String key = file.getName();
        if (!rangeData.has(key)) return null;

        JSONObject rangeObj = rangeData.optJSONObject(key);
        if (rangeObj == null) return null;

        double start = rangeObj.optDouble("start", 0.0);
        double end = rangeObj.optDouble("end", 0.0);

        return new Range(start, end);
    }

    private File getRangesettingsFile() {
        Path settingsDir = AppState.get().getSettingsDirectory();
        return settingsDir != null
                ? settingsDir.resolve("video_ranges.json").toFile()
                : new File("video_ranges.json"); // Fallback (optional)
    }

    public void setRangeForFile(double start, double end, File file) {
        String key = file.getName();

        if (start >= end) {
            rangeData.remove(key);
        } else {
            JSONObject rangeObj = new JSONObject();
            rangeObj.put("start", start);
            rangeObj.put("end", end);
            rangeData.put(key, rangeObj);
        }

        save();

        EventBus.get().publish(new RangeChangedEvent(file));

    }

    private void load() {
        File file = getRangesettingsFile();
        if (!file.exists()) {
            rangeData = new JSONObject();
            return;
        }

        try (InputStream is = new FileInputStream(file)) {
            JSONTokener tokener = new JSONTokener(is);
            rangeData = new JSONObject(tokener);
        } catch (Exception e) {
            e.printStackTrace();
            rangeData = new JSONObject();
        }

        cleanup();
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(getRangesettingsFile().toPath())) {
            writer.write(rangeData.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void cleanup() {
        Path currentDir = AppState.get().getCurrentDirectory();
        if (currentDir == null) return;

        boolean modified = false;

        Iterator<String> iter = rangeData.keySet().iterator();
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
            System.out.println("[Cleanup] Ungültige Einträge in video_ranges.json entfernt.");
        }
    }

    public static class Range {
        public final double start;
        public final double end;

        public Range(double start, double end) {
            this.start = start;
            this.end = end;
        }
    }
}