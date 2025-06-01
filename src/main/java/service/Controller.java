package service;

import org.json.JSONObject;
import ui.ControlPanel;
import ui.MediaView;
import ui.ThumbnailPanel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Controller {
    private static Controller controller = new Controller();
    private ControlPanel controlPanel;
    private ThumbnailPanel thumbnailPanel;
    private MediaView mediaView;

    public static Controller getInstance() {
        return controller;
    }



    public void handleDirectory(Path directory) {

        storeDirectoryToSesingsJson(directory);

        File dir = directory.toFile();
        if (!dir.isDirectory()) {
            System.err.println("Kein gültiges Verzeichnis: " + directory);
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            System.err.println("Keine Dateien gefunden.");
            return;
        }

        // Nur Bilddateien sammeln
        List<File> imageFiles = new ArrayList<>();
        for (File file : files) {
            if (isImageFile(file) || isVideoFile(file)) {
                imageFiles.add(file);
            }
        }

        thumbnailPanel.populate(imageFiles);
    }

    public void storeDirectoryToSesingsJson(Path directory) {
        JSONObject json = new JSONObject();
        json.put("defaultDirectory", directory.toString());

        try (FileWriter writer = new FileWriter("settings.json")) {
            writer.write(json.toString(4)); // 4 = pretty print indent
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public static boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".gif") ||
                name.endsWith(".bmp") || name.endsWith(".webp");
    }

    public static boolean isVideoFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp4");
    }

    public void setControlPanel(ControlPanel controlPanel) {

        this.controlPanel = controlPanel;
    }

    public void setThumbnailPanel(ThumbnailPanel thumbnailPanel) {

        this.thumbnailPanel = thumbnailPanel;
    }

    public void setMediaPanel(MediaView mediaView) {

        this.mediaView = mediaView;
    }

    public void handleMedia(File file) {
        mediaView.display(file);
    }
}
