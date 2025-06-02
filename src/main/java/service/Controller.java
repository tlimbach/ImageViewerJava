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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Controller {
    private static Controller controller = new Controller();
    private ControlPanel controlPanel;
    private ThumbnailPanel thumbnailPanel;
    private MediaView mediaView;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

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

    public void playPause(boolean selected) {
        if (selected) {
            mediaView.play();
        } else {
            mediaView.pause();
        }
    }

    public void setFullscreen(boolean fullscreen) {
        mediaView.fullscreen(fullscreen);
    }

    public void showCurrentPlayPosMillis(long millis, long total) {
        controlPanel.setCurrentPlayPosMillis(millis, total);
    }

    public void setPlayPos(float playPosInPercentage) {
        mediaView.setPlayPos(playPosInPercentage);
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setThumbnailsLoaded(int thumbnailsLoadedCount, int totalThumbnails) {
        controlPanel.setThumbnailsLoaded( thumbnailsLoadedCount,  totalThumbnails);
    }

    public static void printMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();

        long maxMemory = runtime.maxMemory();       // Maximal möglicher Speicher (vom JVM-Flag -Xmx abhängig)
        long allocatedMemory = runtime.totalMemory(); // Aktuell zugewiesener Speicher
        long freeMemory = runtime.freeMemory();       // Freier Speicher innerhalb des zugewiesenen Speichers
        long usedMemory = allocatedMemory - freeMemory;

        System.out.println("========== Speicherstatus ==========");
        System.out.printf("Max Memory     : %.2f MB%n", maxMemory / (1024.0 * 1024));
        System.out.printf("Allocated      : %.2f MB%n", allocatedMemory / (1024.0 * 1024));
        System.out.printf("Used           : %.2f MB%n", usedMemory / (1024.0 * 1024));
        System.out.printf("Free (in alloc): %.2f MB%n", freeMemory / (1024.0 * 1024));
        System.out.println("====================================");
    }
}
