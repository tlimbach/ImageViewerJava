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
    private final int workerThreadCount;

    public static Controller getInstance() {
        return instance;
    }

    private ControlPanel controlPanel;
    private ThumbnailPanel thumbnailPanel;
    private MediaView mediaView;

    public ExecutorService getExecutorService() {
        return ex;
    }

    private Controller() {
        workerThreadCount = Math.max(1, Runtime.getRuntime().availableProcessors());
        ex = Executors.newFixedThreadPool(workerThreadCount);
        System.out.println("[Controller] Background worker threads: " + workerThreadCount);
    }


    public void setControlPanel(ControlPanel cp) {
        this.controlPanel = cp;
    }

    public void setThumbnailPanel(ThumbnailPanel tp) {
        this.thumbnailPanel = tp;
    }

    public void setMediaPanel(MediaView mv) {
        this.mediaView = mv;
    }

    public void setSelectedFiles(List<String> filePaths) {

        Runnable task = () -> {
            if (filePaths == null) {
                thumbnailPanel.reloadDirectory();
                return;
            }
            List<File> files = filePaths.stream()
                    .map(this::resolveSelectedFile)
                    .collect(Collectors.toList());


            thumbnailPanel.populate(files);

        };

        if (SwingUtilities.isEventDispatchThread()) new Thread(task).start();
        else task.run();
    }

    private File resolveSelectedFile(String filePath) {
        File file = new File(filePath);
        if (file.isAbsolute()) {
            return file;
        }

        Path currentDirectory = AppState.get().getCurrentDirectory();
        if (currentDirectory == null) {
            return file;
        }
        return currentDirectory.resolve(filePath).toFile();
    }

    public void handleMedia(File file, boolean isDoubleClick) {
        EventBus.get().publish(new CurrentlySelectedFileEvent(file));
        mediaView.display(file, isDoubleClick || controlPanel.isAutostart());
    }


    public static boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|mpo)");
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

    public ThumbnailPanel getThumbnailPanel(){
        return thumbnailPanel;
    }

    public List<File> getCurrentlyDisplayedFiles() {
        if (thumbnailPanel == null) return Collections.emptyList();

        List<File> files = new ArrayList<>();
        for (AnimatedThumbnail thumb : thumbnailPanel.animatedThumbnails) {
            if (thumb.filename != null) {
                File file = new File(AppState.get().getCurrentDirectory().toFile(), thumb.filename);
                files.add(file);
            }
        }

        return files;
    }

    public List<File> getSlideshowFilesAroundCurrentSelection(Integer limit) {
        List<File> files = getCurrentlyDisplayedFiles();
        if (files.isEmpty() || limit == null || limit <= 0 || files.size() <= limit) {
            return files;
        }

        File currentFile = AppState.get().getCurrentFile();
        int selectedIndex = findFileIndexByName(files, currentFile);
        if (selectedIndex < 0) {
            return new ArrayList<>(files.subList(0, limit));
        }

        int before = (limit - 1) / 2;
        int start = selectedIndex - before;
        int end = start + limit;

        if (start < 0) {
            end = Math.min(files.size(), end - start);
            start = 0;
        }
        if (end > files.size()) {
            start = Math.max(0, start - (end - files.size()));
            end = files.size();
        }

        return new ArrayList<>(files.subList(start, end));
    }

    private int findFileIndexByName(List<File> files, File file) {
        if (file == null) return -1;

        String name = file.getName();
        for (int i = 0; i < files.size(); i++) {
            File candidate = files.get(i);
            if (candidate != null && candidate.getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

}
