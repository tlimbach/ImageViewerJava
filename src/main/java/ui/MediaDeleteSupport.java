package ui;

import event.MediaFileDeletedEvent;
import event.TagsChangedEvent;
import service.EventBus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class MediaDeleteSupport {

    private MediaDeleteSupport() {
    }

    static boolean moveFileToTrash(File file) {
        if (file == null) return false;
        Path source = file.toPath();
        Path parent = source.getParent();
        if (parent == null) return false;

        try {
            Path trashDirectory = parent.resolve("trash");
            Files.createDirectories(trashDirectory);
            Files.move(source, uniqueTrashTarget(trashDirectory, file.getName()));
            EventBus.get().publish(new MediaFileDeletedEvent(file));
            EventBus.get().publish(new TagsChangedEvent());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Path uniqueTrashTarget(Path trashDirectory, String fileName) {
        Path target = trashDirectory.resolve(fileName);
        if (!Files.exists(target)) return target;

        String baseName = fileName;
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        int counter = 1;
        do {
            target = trashDirectory.resolve(baseName + " " + counter + extension);
            counter++;
        } while (Files.exists(target));

        return target;
    }
}
