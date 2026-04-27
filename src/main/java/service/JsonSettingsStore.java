package service;

import model.AppState;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

abstract class JsonSettingsStore {

    protected JSONObject data = new JSONObject();

    protected abstract String settingsFileName();

    protected File getSettingsFile() {
        Path settingsDir = AppState.get().getSettingsDirectory();
        return settingsDir != null
                ? settingsDir.resolve(settingsFileName()).toFile()
                : new File(settingsFileName());
    }

    protected synchronized void load() {
        File file = getSettingsFile();
        if (!file.exists()) {
            data = new JSONObject();
            return;
        }

        try (InputStream is = new FileInputStream(file)) {
            data = new JSONObject(new JSONTokener(is));
        } catch (Exception e) {
            e.printStackTrace();
            data = new JSONObject();
        }

        cleanupMissingFiles();
    }

    protected synchronized void save() {
        try (Writer writer = Files.newBufferedWriter(getSettingsFile().toPath())) {
            writer.write(data.toString(2));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cleanupMissingFiles() {
        Path currentDir = AppState.get().getCurrentDirectory();
        if (currentDir == null) return;

        boolean modified = false;
        Iterator<String> iter = data.keySet().iterator();
        while (iter.hasNext()) {
            String filename = iter.next();
            if (!Files.exists(currentDir.resolve(filename))) {
                iter.remove();
                modified = true;
            }
        }

        if (modified) {
            save();
            System.out.println(cleanupLogMessage());
        }
    }

    protected String cleanupLogMessage() {
        return "[Cleanup] Ungültige Einträge in " + settingsFileName() + " entfernt.";
    }
}
