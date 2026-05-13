package ui;

public enum ThumbnailZoomMode {
    STANDARD("Standard"),
    ZOOMED("Zoomed"),
    GRAYED_OUT("Grayed out");

    private final String label;

    ThumbnailZoomMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
