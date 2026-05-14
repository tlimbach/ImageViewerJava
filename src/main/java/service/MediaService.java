package service;

import event.CurrentDirectoryChangedEvent;
import model.AppState;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MediaService {
    private static MediaService mediaService = new MediaService();


    private MediaService() {
    }

    public static MediaService getInstance() {
        return mediaService;
    }



    public List<File> loadFilesFromDirectory(){
        H.out("load files from dfirectopry " + SwingUtilities.isEventDispatchThread());
        File[] files = AppState.get().getCurrentDirectory().toFile().listFiles();
        if (files == null) return null;

        List<File> mediaFiles = Arrays.stream(files)
                .filter(f -> Controller.isImageFile(f) || Controller.isVideoFile(f))
                .sorted(creationDateDescendingComparator())
                .collect(Collectors.toList());
        return applyCurrentMediaLimit(mediaFiles);
    }

    public static void sortByCreationDateDescending(List<File> files) {
        files.sort(creationDateDescendingComparator());
    }

    public static List<File> applyCurrentMediaLimit(List<File> files) {
        Integer limit = AppState.get().getMediaLoadLimit();
        if (limit == null || limit <= 0 || files.size() <= limit) {
            return files;
        }
        return files.subList(0, limit);
    }

    private static Comparator<File> creationDateDescendingComparator() {
        return Comparator
                .comparingLong(MediaService::sortCreationTimeMillis)
                .reversed()
                .thenComparing(MediaService::sortGroupName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(MediaService::cropSortOrder)
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER);
    }

    private static long sortCreationTimeMillis(File file) {
        File original = cropOriginalFile(file);
        return creationTimeMillis(original != null ? original : file);
    }

    private static String sortGroupName(File file) {
        File original = cropOriginalFile(file);
        return original != null ? original.getName() : file.getName();
    }

    private static int cropSortOrder(File file) {
        return cropOriginalFile(file) == null ? 0 : 1;
    }

    private static File cropOriginalFile(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        String baseName = dotIndex > 0 ? name.substring(0, dotIndex) : name;
        int markerIndex = baseName.lastIndexOf("_ausschnitt");
        if (markerIndex <= 0) {
            return null;
        }

        String suffix = baseName.substring(markerIndex + "_ausschnitt".length());
        if (!suffix.isEmpty() && !suffix.matches("_\\d+")) {
            return null;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return null;
        }

        String originalBaseName = baseName.substring(0, markerIndex);
        String[] extensions = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".mpo"};
        for (String extension : extensions) {
            File candidate = new File(parent, originalBaseName + extension);
            if (candidate.isFile() && Controller.isImageFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static long creationTimeMillis(File file) {
        try {
            return Files.readAttributes(file.toPath(), BasicFileAttributes.class).creationTime().toMillis();
        } catch (IOException e) {
            return file.lastModified();
        }
    }
}
