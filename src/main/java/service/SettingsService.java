package service;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SettingsService {
    private static SettingsService instance = new SettingsService();

    private  SettingsService() {

    }

    public static SettingsService getIntance() {
        return instance;
    }

    public Path loadDefaultDirectoryFromSettingsJson() {
        File file = new File("settings.json");
        if (!file.exists()) return Paths.get(System.getProperty("user.home"));

        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            JSONObject json = new JSONObject(content);
            String dirString = json.optString("defaultDirectory", System.getProperty("user.home"));
            return Paths.get(dirString);
        } catch (IOException e) {
            e.printStackTrace();
            return Paths.get(System.getProperty("user.home"));
        }
    }

    public void storeDirectory(Path dir) {
        JSONObject json = new JSONObject();
        json.put("defaultDirectory", dir.toString());
        try (FileWriter writer = new FileWriter("settings.json")) {
            writer.write(json.toString(4));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
