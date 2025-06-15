package service;

import event.VolumeChangedEvent;
import model.AppState;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

public class VolumeHandler {


    private static VolumeHandler instance = new VolumeHandler();

    private JSONObject data;

    private VolumeHandler() {
        load();
    }

    public static VolumeHandler getInstance() {
        return instance;
    }

    public int getVolumeForFile(File file) {
        return data.optInt(file.getName(), 50);
    }

    private File getVolumesettingsFile() {
        Path settingsDir = AppState.get().getSettingsDirectory();
        return settingsDir != null
                ? settingsDir.resolve("volume_settings.json").toFile()
                : new File("volume_settings.json");
    }


    public void setVolumeForCurrentFile(int volume) {
        data.put(AppState.get().getCurrentFile().getName(), volume);
        save();
        EventBus.get().publish(new VolumeChangedEvent(volume));
    }

    private void load() {
        File file = getVolumesettingsFile();
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

        cleanup();
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(getVolumesettingsFile().toPath())) {
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
            System.out.println("[Cleanup] Ungültige Einträge in volume_settings.json entfernt.");
        }
    }
}