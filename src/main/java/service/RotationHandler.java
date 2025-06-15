package service;

import event.RotationChangedEvent;
import model.AppState;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RotationHandler {

    private static final RotationHandler instance = new RotationHandler();

    private final Map<String, Integer> rotations = new HashMap<>();

    private RotationHandler() {
        load();
    }

    public static RotationHandler getInstance() {
        return instance;
    }

    private File getRotationFile() {
        Path settingsDir = AppState.get().getSettingsDirectory();
        return settingsDir != null
                ? settingsDir.resolve("rotation.json").toFile()
                : new File("rotation.json");
    }

    public synchronized int getRotation(File file) {
        return rotations.getOrDefault(file.getName(), 0);
    }

    public synchronized void setRotation(File file, int rotation) {
        int clean = (rotation + 360) % 360;
        rotations.put(file.getName(), clean);
        save();
        EventBus.get().publish(new RotationChangedEvent(file, clean));
    }

    private void load() {
        if (!getRotationFile().exists()) return;
        try {
            String content = Files.readString(getRotationFile().toPath());
            JSONObject obj = new JSONObject(content);
            for (String key : obj.keySet()) {
                rotations.put(key, obj.getInt(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        cleanup();
    }

    private void save() {
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, Integer> entry : rotations.entrySet()) {
            obj.put(entry.getKey(), entry.getValue());
        }
        try (FileWriter writer = new FileWriter(getRotationFile())) {
            writer.write(obj.toString(2));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void cleanup() {
        Path currentDir = AppState.get().getCurrentDirectory();
        if (currentDir == null) return;

        boolean modified = false;

        Iterator<String> iter = rotations.keySet().iterator();
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
            System.out.println("[Cleanup] Ungültige Einträge in rotation.json entfernt.");
        }
    }
}