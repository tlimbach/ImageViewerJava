package service;

import org.json.JSONObject;
import ui.AnimatedThumbnail;
import ui.ControlPanel;
import ui.MediaView;
import ui.ThumbnailPanel;

import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Controller {

    private static final Controller instance = new Controller();
    public static Controller getInstance() { return instance; }

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final String SETTINGS_FILE = "settings.json";

    private ControlPanel controlPanel;
    private ThumbnailPanel thumbnailPanel;
    private MediaView mediaView;

    private List<File> mediaFiles = new ArrayList<>();
    private Path currentDirectory;

    public void setControlPanel(ControlPanel cp) { this.controlPanel = cp; }
    public void setThumbnailPanel(ThumbnailPanel tp) { this.thumbnailPanel = tp; }
    public void setMediaPanel(MediaView mv) { this.mediaView = mv; }

    private volatile boolean slideshowActive = false;
    private Future<?> slideshowTask;

    public void handleDirectory(Path directory) {
        this.currentDirectory = directory;
        storeDirectory(directory);

        File[] files = directory.toFile().listFiles();
        if (files == null) return;

        mediaFiles = Arrays.stream(files)
                .filter(f -> isImageFile(f) || isVideoFile(f))
                .collect(Collectors.toList());

        thumbnailPanel.populate(mediaFiles);
    }

    public void setSelectedFiles(List<String> filePaths) {
        Runnable task = () -> {
            List<File> files = filePaths.isEmpty()
                    ? mediaFiles
                    : filePaths.stream().map(File::new).collect(Collectors.toList());
            thumbnailPanel.populate(files);
        };

        if (SwingUtilities.isEventDispatchThread()) new Thread(task).start();
        else task.run();
    }

    public void handleMedia(File file, boolean isDoubleClick) {
        controlPanel.setSelectedFile(file);
        mediaView.display(file, isDoubleClick ||controlPanel.isAutostart());
    }

    public void playPause(boolean playing) {
        if (playing) mediaView.play();
        else mediaView.pause();
    }

    public void setFullscreen(boolean fullscreen) {
        mediaView.fullscreen(fullscreen);
    }

    public void showCurrentPlayPosMillis(long millis, long total) {
        controlPanel.setCurrentPlayPosMillis(millis, total);
    }

    public void setPlayPos(float pos) {
        mediaView.setPlayPos(pos);
    }

    public boolean isIgnoreTimerange() {
        return controlPanel.isIgnoreTimerange();
    }

    public Executor getExecutor() { return executor; }
    public Path getCurrentDirectory() {
        if (currentDirectory == null) currentDirectory = loadDirectory();
        return currentDirectory;
    }

    public void setThumbnailsLoaded(int loaded, int total) {
        controlPanel.setThumbnailsLoaded(loaded, total);
    }

    private void storeDirectory(Path dir) {
        JSONObject json = new JSONObject();
        json.put("defaultDirectory", dir.toString());
        try (FileWriter writer = new FileWriter(SETTINGS_FILE)) {
            writer.write(json.toString(4));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Path loadDirectory() {
        File file = new File(SETTINGS_FILE);
        if (!file.exists()) return Paths.get(System.getProperty("user.home"));
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            JSONObject json = new JSONObject(content);
            return Paths.get(json.optString("defaultDirectory", System.getProperty("user.home")));
        } catch (IOException e) {
            e.printStackTrace();
            return Paths.get(System.getProperty("user.home"));
        }
    }

    public static boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)");
    }

    public static boolean isVideoFile(File file) {
        return file.getName().toLowerCase().endsWith(".mp4");
    }

    public static void printMemoryUsage() {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long alloc = rt.totalMemory();
        long used = alloc - rt.freeMemory();

        System.out.printf("Memory Used: %.2f MB / %.2f MB (Max)%n",
                used / 1024.0 / 1024,
                max / 1024.0 / 1024);
    }

    public void tagsChanged() {
        // Placeholder – implement as needed
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

    public ControlPanel getControlPanel() {
        return controlPanel;
    }


    public List<File> getCurrentlyDisplayedFiles() {
        if (thumbnailPanel == null) return Collections.emptyList();

        List<File> files = new ArrayList<>();
        for (AnimatedThumbnail thumb : thumbnailPanel.animatedThumbnails) {
            File file = new File(currentDirectory.toFile(), thumb.filename);
            files.add(file);
        }

        return files;
    }

    public void stop() {
        mediaView.stop();
    }

    public void hideMediaPanel() {
        mediaView.hideFrame();
    }

    public void updateUntaggedCount() {
        List <File> untagged = TagHandler.getInstance().getUntaggedFiles();
        controlPanel.setUntaggedCount(untagged.size());
    }

    public MediaView getMediaView(){
        return mediaView;
    }
}
