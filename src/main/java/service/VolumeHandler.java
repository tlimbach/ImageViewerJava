package service;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class VolumeHandler {

    private static final String FILE_NAME = "volume_settings.json";
    private static VolumeHandler instance = new VolumeHandler();

    private JSONObject data;

    private VolumeHandler() {
        load();
    }

    public static VolumeHandler getInstance() {
        return instance;
    }

    public int getVolumeForFile(String path) {
        return data.optInt(path, 50); }

    public void setVolumeForFile(String path, int volume) {
        data.put(path, volume);
        save();
    }

    private void load() {
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            data = new JSONObject();
            return;
        }

        try (InputStream is = new FileInputStream(file)) {
            JSONTokener tokener = new JSONTokener(is);
            data = new JSONObject(tokener);
        } catch (Exception e) {
            e.printStackTrace();
            data = new JSONObject(); // fallback
        }
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(Paths.get(FILE_NAME))) {
            writer.write(data.toString(2)); // pretty print
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}