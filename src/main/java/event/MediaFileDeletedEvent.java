package event;

import java.io.File;

public record MediaFileDeletedEvent(File file) {
}
