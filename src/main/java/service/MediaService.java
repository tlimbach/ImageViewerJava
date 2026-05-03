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
                .comparingLong(MediaService::creationTimeMillis)
                .reversed()
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER);
    }

    private static long creationTimeMillis(File file) {
        try {
            return Files.readAttributes(file.toPath(), BasicFileAttributes.class).creationTime().toMillis();
        } catch (IOException e) {
            return file.lastModified();
        }
    }
}
