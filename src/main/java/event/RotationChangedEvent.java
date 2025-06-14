package event;

import java.io.File;

public record RotationChangedEvent(File file, int degrees) {}