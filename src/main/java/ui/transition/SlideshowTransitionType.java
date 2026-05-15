package ui.transition;

import java.util.concurrent.ThreadLocalRandom;

public enum SlideshowTransitionType {
    CROSSFADE,
    FADE_TO_BLACK,
    WIPE,
    SLIDE,
    ZOOM_FADE;

    public static SlideshowTransitionType random() {
        SlideshowTransitionType[] values = values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
