package service;

import event.CurrentDirectoryChangedEvent;
import model.AppState;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
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

        return Arrays.stream(files)
                .filter(f -> Controller.isImageFile(f) || Controller.isVideoFile(f))
                .collect(Collectors.toList());
    }
}
