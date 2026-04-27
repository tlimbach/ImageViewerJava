package service;

import event.CurrentDirectoryChangedEvent;
import event.RangeChangedEvent;
import org.json.JSONObject;

import java.io.*;

public class RangeHandler extends JsonSettingsStore {


    private static RangeHandler rangeHandler = new RangeHandler();

    public static RangeHandler getInstance() {
        return rangeHandler;
    }

    private RangeHandler() {
        load();
        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> load());
    }

    public Range getRangeForFile(File file) {
        String key = file.getName();
        if (!data.has(key)) return null;

        JSONObject rangeObj = data.optJSONObject(key);
        if (rangeObj == null) return null;

        double start = rangeObj.optDouble("start", 0.0);
        double end = rangeObj.optDouble("end", 0.0);
        int length = rangeObj.optInt("length", 0);

        return new Range(start, end, length);
    }

    @Override
    protected String settingsFileName() {
        return "video_ranges.json";
    }

    public void setRangeForFile(double start, double end, File file) {
        String key = file.getName();

        if (start >= end) {
            data.remove(key);
        } else {
            JSONObject rangeObj = new JSONObject();
            rangeObj.put("start", start);
            rangeObj.put("end", end);
            rangeObj.put("length", (int) RangeHandler.getInstance().getDuration(file));
            data.put(key, rangeObj);
        }

        save();

        EventBus.get().publish(new RangeChangedEvent(file));

    }

    public double getDuration(File file) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", file.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    return Double.parseDouble(line.trim());
                }
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Lesen der Dauer mit ffprobe: " + e.getMessage());
        }
        return 0;
    }

    public int getTotalLength(File file) {
        Range range = getRangeForFile(file);
        if (range != null) {
            return range.totalLength;
        } else return -1;
    }

    public static class Range {
        public final double start;
        public final double end;

        public final int totalLength;

        public Range(double start, double end, int totalLength) {
            this.start = start;
            this.end = end;
            this.totalLength = totalLength;
        }
    }
}
