package service;

import event.CurrentDirectoryChangedEvent;
import model.AppState;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BookmarkService extends JsonSettingsStore {

    private static final BookmarkService instance = new BookmarkService();

    public record Bookmark(String label, File file) {
        @Override
        public String toString() {
            return label;
        }
    }

    public static BookmarkService getInstance() {
        return instance;
    }

    private BookmarkService() {
        load();
        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> load());
    }

    @Override
    protected String settingsFileName() {
        return "bookmarks.json";
    }

    public synchronized List<Bookmark> getBookmarks() {
        Path currentDir = AppState.get().getCurrentDirectory();
        if (currentDir == null) return List.of();

        List<Bookmark> result = new ArrayList<>();
        for (String filename : data.keySet()) {
            String label = data.optString(filename, "").trim();
            if (label.isEmpty()) {
                label = filename;
            }

            File file = currentDir.resolve(filename).toFile();
            if (file.isFile() && (Controller.isImageFile(file) || Controller.isVideoFile(file))) {
                result.add(new Bookmark(label, file));
            }
        }

        result.sort(Comparator
                .comparing(Bookmark::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(bookmark -> bookmark.file().getName(), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public synchronized String getBookmarkLabel(File file) {
        if (file == null) return "";
        return data.optString(file.getName(), "").trim();
    }

    public synchronized void setBookmark(File file, String label) {
        if (file == null || label == null || label.trim().isEmpty()) return;

        data.put(file.getName(), label.trim());
        save();
    }

    public synchronized void removeBookmark(File file) {
        if (file == null) return;

        data.remove(file.getName());
        save();
    }

    public synchronized boolean hasBookmarks() {
        return !getBookmarks().isEmpty();
    }
}
