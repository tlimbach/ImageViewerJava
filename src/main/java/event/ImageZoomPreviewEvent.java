package event;

import service.ImageZoomHandler;

import java.io.File;

public record ImageZoomPreviewEvent(File file, ImageZoomHandler.ZoomSelection zoom) {
}
