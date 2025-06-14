package service;

import event.RotationChangedEvent;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class RotationHandler {

    private static final RotationHandler instance = new RotationHandler();
    private final File jsonFile = new File("rotation.json");
    private final Map<String, Integer> rotations = new HashMap<>();

    private RotationHandler() {
        load();
    }

    public static RotationHandler getInstance() {
        return instance;
    }

    public synchronized int getRotation(File file) {
        return rotations.getOrDefault(file.getAbsolutePath(), 0);
    }

    public synchronized void setRotation(File file, int rotation) {
        int clean = (rotation + 360) % 360;
        rotations.put(file.getAbsolutePath(), clean);
        save();
        EventBus.get().publish(new RotationChangedEvent(file, clean));
    }

    private void load() {
        if (!jsonFile.exists()) return;
        try {
            String content = Files.readString(jsonFile.toPath());
            JSONObject obj = new JSONObject(content);
            for (String key : obj.keySet()) {
                rotations.put(key, obj.getInt(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void save() {
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, Integer> entry : rotations.entrySet()) {
            obj.put(entry.getKey(), entry.getValue());
        }
        try (FileWriter writer = new FileWriter(jsonFile)) {
            writer.write(obj.toString(2));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}