package service;

import event.CurrentDirectoryChangedEvent;

import java.io.File;

public class ImageSaturationHandler extends JsonSettingsStore {
    private static final ImageSaturationHandler instance = new ImageSaturationHandler();

    public static ImageSaturationHandler getInstance() {
        return instance;
    }

    private ImageSaturationHandler() {
        load();
        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> load());
    }

    @Override
    protected String settingsFileName() {
        return "media_saturation.json";
    }

    public SaturationLevel getLevelForFile(File file) {
        if (file == null) return SaturationLevel.NORMAL;
        return SaturationLevel.fromKey(data.optString(file.getName(), SaturationLevel.NORMAL.key()));
    }

    public void setLevelForFile(File file, SaturationLevel level) {
        if (file == null || level == null || level == SaturationLevel.NORMAL) {
            if (file != null) {
                data.remove(file.getName());
                save();
            }
            return;
        }

        data.put(file.getName(), level.key());
        save();
    }

    public enum SaturationLevel {
        NORMAL("normal", "Normal", 1.0f),
        LIGHT("light", "Leicht", 1.25f),
        STRONG("strong", "Stark", 1.55f);

        private final String key;
        private final String label;
        private final float factor;

        SaturationLevel(String key, String label, float factor) {
            this.key = key;
            this.label = label;
            this.factor = factor;
        }

        public String key() {
            return key;
        }

        public float factor() {
            return factor;
        }

        @Override
        public String toString() {
            return label;
        }

        static SaturationLevel fromKey(String key) {
            for (SaturationLevel level : values()) {
                if (level.key.equals(key)) {
                    return level;
                }
            }
            return NORMAL;
        }
    }
}
