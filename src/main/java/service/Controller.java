package service;

import event.CurrentlySelectedFileEvent;
import model.AppState;
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
    private final ExecutorService ex;

    public static Controller getInstance() { return instance; }

    private ControlPanel controlPanel;
    private ThumbnailPanel thumbnailPanel;
    private MediaView mediaView;

    private List<File> mediaFiles = new ArrayList<>();

    public ExecutorService getExecutorService() {
        return ex;
    }

    private Controller(){
        ex = Executors.newFixedThreadPool(2);
    }


    public void setControlPanel(ControlPanel cp) { this.controlPanel = cp; }
    public void setThumbnailPanel(ThumbnailPanel tp) { this.thumbnailPanel = tp; }
    public void setMediaPanel(MediaView mv) { this.mediaView = mv; }

    public void handleDirectory(Path directory) {
        SettingsService.getIntance().storeDirectory(directory);

        File[] files = directory.toFile().listFiles();
        if (files == null) return;

        mediaFiles = Arrays.stream(files)
                .filter(f -> isImageFile(f) || isVideoFile(f))
                .collect(Collectors.toList());

        thumbnailPanel.populate(mediaFiles);

        List <File> untagged = TagHandler.getInstance().getUntaggedFiles();
        controlPanel.setUntaggedCount(untagged.size());
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
        EventBus.get().publish(new CurrentlySelectedFileEvent(file));
        mediaView.display(file, isDoubleClick ||controlPanel.isAutostart());
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

    public ControlPanel getControlPanel() {
        return controlPanel;
    }

    public List<File> getCurrentlyDisplayedFiles() {
        if (thumbnailPanel == null) return Collections.emptyList();

        List<File> files = new ArrayList<>();
        for (AnimatedThumbnail thumb : thumbnailPanel.animatedThumbnails) {
            File file = new File(AppState.get().getCurrentDirectory().toFile(), thumb.filename);
            files.add(file);
        }

        return files;
    }

}
