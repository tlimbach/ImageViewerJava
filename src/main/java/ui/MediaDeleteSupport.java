package ui;

import event.MediaFileDeletedEvent;
import event.TagsChangedEvent;
import service.EventBus;

import java.io.File;

final class MediaDeleteSupport {

    private MediaDeleteSupport() {
    }

    static boolean deleteFile(File file) {
        if (file == null) return false;

        if (file.delete()) {
            EventBus.get().publish(new MediaFileDeletedEvent(file));
            EventBus.get().publish(new TagsChangedEvent());
            return true;
        }

        return false;
    }
}
