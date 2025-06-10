package event;

public record CurrentPlaybackPosEvent(long currentMillis, long totalMinis) {
}
