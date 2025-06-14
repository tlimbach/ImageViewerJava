package service;

import event.CurrentDirectoryChangedEvent;
import model.AppState;

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
        File[] files = AppState.get().getCurrentDirectory().toFile().listFiles();
        if (files == null) return null;

        return Arrays.stream(files)
                .filter(f -> Controller.isImageFile(f) || Controller.isVideoFile(f))
                .collect(Collectors.toList());
    }
}
