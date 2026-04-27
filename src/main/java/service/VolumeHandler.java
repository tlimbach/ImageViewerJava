package service;

import event.CurrentDirectoryChangedEvent;
import event.VolumeChangedEvent;
import model.AppState;

import java.io.*;

public class VolumeHandler extends JsonSettingsStore {


    private static VolumeHandler instance = new VolumeHandler();

    private VolumeHandler() {
        load();
        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> load());
    }

    public static VolumeHandler getInstance() {
        return instance;
    }

    public int getVolumeForFile(File file) {
        return data.optInt(file.getName(), 50);
    }

    @Override
    protected String settingsFileName() {
        return "volume_settings.json";
    }


    public void setVolumeForCurrentFile(int volume) {
        data.put(AppState.get().getCurrentFile().getName(), volume);
        save();
        EventBus.get().publish(new VolumeChangedEvent(volume));
    }
}
