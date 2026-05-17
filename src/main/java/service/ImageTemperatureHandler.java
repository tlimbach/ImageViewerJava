package service;

import event.CurrentDirectoryChangedEvent;

import java.io.File;

public class ImageTemperatureHandler extends JsonSettingsStore {
    public static final int MIN_TEMPERATURE = -5;
    public static final int MAX_TEMPERATURE = 5;
    public static final int NEUTRAL_TEMPERATURE = 0;

    private static final ImageTemperatureHandler instance = new ImageTemperatureHandler();

    public static ImageTemperatureHandler getInstance() {
        return instance;
    }

    private ImageTemperatureHandler() {
        load();
        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> load());
    }

    @Override
    protected String settingsFileName() {
        return "media_temperature.json";
    }

    public int getTemperatureForFile(File file) {
        if (file == null) return NEUTRAL_TEMPERATURE;
        return clamp(data.optInt(file.getName(), NEUTRAL_TEMPERATURE));
    }

    public void setTemperatureForFile(File file, int temperature) {
        if (file == null) return;

        int clamped = clamp(temperature);
        if (clamped == NEUTRAL_TEMPERATURE) {
            data.remove(file.getName());
        } else {
            data.put(file.getName(), clamped);
        }
        save();
    }

    private int clamp(int value) {
        return Math.max(MIN_TEMPERATURE, Math.min(MAX_TEMPERATURE, value));
    }
}
