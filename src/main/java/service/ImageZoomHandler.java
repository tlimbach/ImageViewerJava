package service;

import event.CurrentDirectoryChangedEvent;
import event.ImageZoomChangedEvent;
import org.json.JSONObject;

import java.io.File;

public class ImageZoomHandler extends JsonSettingsStore {

    private static final ImageZoomHandler instance = new ImageZoomHandler();

    private ImageZoomHandler() {
        load();
        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> load());
    }

    public static ImageZoomHandler getInstance() {
        return instance;
    }

    public ZoomSelection getZoomForFile(File file) {
        if (file == null) return null;
        JSONObject json = data.optJSONObject(file.getName());
        if (json == null) return null;

        double x = json.optDouble("x", 0);
        double y = json.optDouble("y", 0);
        double width = json.optDouble("width", 0);
        double height = json.optDouble("height", 0);

        if (width <= 0 || height <= 0) return null;
        return new ZoomSelection(x, y, width, height);
    }

    public void setZoomForFile(File file, ZoomSelection zoom) {
        if (file == null || zoom == null) return;

        JSONObject json = new JSONObject();
        json.put("x", zoom.x());
        json.put("y", zoom.y());
        json.put("width", zoom.width());
        json.put("height", zoom.height());
        data.put(file.getName(), json);
        save();
        EventBus.get().publish(new ImageZoomChangedEvent(file));
    }

    public void resetZoomForFile(File file) {
        if (file == null) return;
        data.remove(file.getName());
        save();
        EventBus.get().publish(new ImageZoomChangedEvent(file));
    }

    @Override
    protected String settingsFileName() {
        return "image_zoom_settings.json";
    }

    @Override
    protected String cleanupLogMessage() {
        return "[ImageZoomHandler] Ungültige Einträge in image_zoom_settings.json entfernt.";
    }

    public record ZoomSelection(double x, double y, double width, double height) {
    }
}
