package service;

import event.CurrentDirectoryChangedEvent;
import model.AppState;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SettingsService {
    private static SettingsService instance = new SettingsService();
    private static final int DIRECTORY_HISTORY_LIMIT = 10;

    private SettingsService() {
        EventBus.get().register(CurrentDirectoryChangedEvent.class, c -> {
            storeDirectory(AppState.get().getCurrentDirectory());
        });
    }

    public static SettingsService getIntance() {
        return instance;
    }

    public Path loadDefaultDirectoryFromSettingsJson() {
        JSONObject json = loadRootSettings();
        String dirString = json.optString("defaultDirectory", System.getProperty("user.home"));
        return Paths.get(dirString);
    }

    public List<Path> loadDirectoryHistory() {
        JSONObject json = loadRootSettings();
        JSONArray history = json.optJSONArray("directoryHistory");
        List<Path> paths = new ArrayList<>();
        if (history == null) {
            String defaultDirectory = json.optString("defaultDirectory", null);
            if (defaultDirectory != null && !defaultDirectory.isBlank()) {
                paths.add(Paths.get(defaultDirectory));
            }
            return paths;
        }

        for (int i = 0; i < history.length(); i++) {
            String value = history.optString(i, null);
            if (value != null && !value.isBlank()) {
                paths.add(Paths.get(value));
            }
        }
        return paths;
    }

    public void removeDirectoryFromHistory(Path dir) {
        if (dir == null) return;
        JSONObject json = loadRootSettings();
        JSONArray history = json.optJSONArray("directoryHistory");
        JSONArray updated = new JSONArray();
        if (history != null) {
            String removePath = normalizePath(dir);
            for (int i = 0; i < history.length(); i++) {
                String value = history.optString(i, null);
                if (value != null && !normalizePath(Paths.get(value)).equals(removePath)) {
                    updated.put(value);
                }
            }
        }
        json.put("directoryHistory", updated);
        saveRootSettings(json);
    }

    public void storeDirectory(Path dir) {
        if (dir == null) return;
        JSONObject json = loadRootSettings();
        json.put("defaultDirectory", dir.toString());
        json.put("directoryHistory", updatedHistory(json.optJSONArray("directoryHistory"), dir));
        saveRootSettings(json);
    }

    private JSONArray updatedHistory(JSONArray existing, Path dir) {
        JSONArray result = new JSONArray();
        String selectedPath = normalizePath(dir);
        result.put(dir.toString());

        if (existing != null) {
            for (int i = 0; i < existing.length() && result.length() < DIRECTORY_HISTORY_LIMIT; i++) {
                String value = existing.optString(i, null);
                if (value == null || value.isBlank()) continue;
                if (!normalizePath(Paths.get(value)).equals(selectedPath)) {
                    result.put(value);
                }
            }
        }
        return result;
    }

    private JSONObject loadRootSettings() {
        File file = new File("settings.json");
        if (!file.exists()) return new JSONObject();

        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            return new JSONObject(content);
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    private void saveRootSettings(JSONObject json) {
        try (FileWriter writer = new FileWriter("settings.json")) {
            writer.write(json.toString(4));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String normalizePath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}
