package service;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RangeHandler {

    private final String rangesFile = "video_ranges.json";
    private JSONObject rangeData;

    public RangeHandler() {
        load();
    }

    public Range getRangeForFile(File file) {
        String key = file.getAbsolutePath();
        if (!rangeData.has(key)) return null;

        JSONObject rangeObj = rangeData.optJSONObject(key);
        if (rangeObj == null) return null;

        double start = rangeObj.optDouble("start", 0.0);
        double end = rangeObj.optDouble("end", 0.0);

        return new Range(start, end);
    }

    public void setRangeForFile(double start, double end, File file) {
        String key = file.getAbsolutePath();

        if (start >= end) {
            rangeData.remove(key);
        } else {
            JSONObject rangeObj = new JSONObject();
            rangeObj.put("start", start);
            rangeObj.put("end", end);
            rangeData.put(key, rangeObj);
        }

        save();
    }

    private void load() {
        File file = new File(rangesFile);
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
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(Paths.get(rangesFile))) {
            writer.write(rangeData.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
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