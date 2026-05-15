package ui.transition;

import service.ImageZoomHandler;

import java.awt.image.BufferedImage;

public record SlideshowTransitionImage(
        BufferedImage image,
        ImageZoomHandler.ZoomSelection zoomSelection
) {
}
