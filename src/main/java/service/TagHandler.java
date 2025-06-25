package service;

import event.CurrentDirectoryChangedEvent;
import model.AppState;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TagHandler {

    private static final TagHandler tagHandler = new TagHandler();

    public static TagHandler getInstance() {
        return tagHandler;
    }


    private JSONObject tagData;

    private TagHandler() {
        load();
        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> load());
    }


    private File getTagFile() {
        Path settingsDir = AppState.get().getSettingsDirectory();
        return settingsDir != null
                ? settingsDir.resolve("media_tags.json").toFile()
                : new File("media_tags.json"); // Fallback (optional)
    }

    public List<String> getTagsForFile(String filename) {
        if (!tagData.has(filename)) return Collections.emptyList();

        JSONArray arr = tagData.optJSONArray(filename);
        if (arr == null) return Collections.emptyList();

        List<String> tags = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            String tag = arr.optString(i, "").trim();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    public void setTagsToFile(List<String> tags, String filename) {
        if (tags == null || tags.isEmpty()) {
            tagData.remove(filename);
        } else {
            JSONArray arr = new JSONArray(tags);
            tagData.put(filename, arr);
        }
        save();
    }

    private void load() {
        File file = getTagFile();
        if (!file.exists()) {
            tagData = new JSONObject();
            return;
        }

        try (InputStream is = new FileInputStream(file)) {
            JSONTokener tokener = new JSONTokener(is);
            tagData = new JSONObject(tokener);
        } catch (Exception e) {
            e.printStackTrace();
            tagData = new JSONObject();
        }

        cleanup();
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(getTagFile().toPath())) {
            writer.write(tagData.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Integer> allTags() {
        Map<String, Integer> tagCounts = new HashMap<>();

        for (String key : tagData.keySet()) {
            JSONArray arr = tagData.optJSONArray(key);
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String tag = arr.optString(i, "").trim();
                    if (!tag.isEmpty()) {
                        tagCounts.put(tag, tagCounts.getOrDefault(tag, 0) + 1);
                    }
                }
            }
        }

        return tagCounts;
    }

    public List<String> getFilesForSelectedTags(List<String> selectedTags, boolean matchAllTags) {
        if (selectedTags == null || selectedTags.isEmpty()) return null;

        List<String> matchingFiles = new ArrayList<>();

        for (String key : tagData.keySet()) {
            JSONArray arr = tagData.optJSONArray(key);
            if (arr != null) {
                Set<String> fileTags = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    String tag = arr.optString(i, "").trim();
                    if (!tag.isEmpty()) {
                        fileTags.add(tag);
                    }
                }

                if (matchAllTags) {
                    // Mindestens ein Tag muss übereinstimmen (ODER-Logik)
                    if (!Collections.disjoint(fileTags, selectedTags)) {
                        matchingFiles.add(key);
                    }
                } else {
                    // Alle Tags müssen enthalten sein (UND-Logik)
                    if (fileTags.containsAll(selectedTags)) {
                        matchingFiles.add(key);
                    }
                }
            }
        }

        return matchingFiles;
    }

    public List<File> getUntaggedFiles() {
        Path currentDir = AppState.get().getCurrentDirectory();
        if (currentDir == null) return Collections.emptyList();

        File[] files = currentDir.toFile().listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                    || lower.endsWith(".gif") || lower.endsWith(".bmp")
                    || lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".mkv");
        });

        if (files == null) return Collections.emptyList();

        List<File> untagged = new ArrayList<>();

        for (File file : files) {
            String filename = file.getName();
            if (!tagData.has(filename)) {
                untagged.add(file);
            } else {
                JSONArray arr = tagData.optJSONArray(filename);
                if (arr == null || arr.isEmpty()) {
                    untagged.add(file);
                }
            }
        }

        return untagged;
    }

    /**
     * Entfernt Einträge für Dateien, die im aktuellen Ordner nicht mehr existieren.
     * Führt ein Save aus, falls Änderungen auftraten.
     */
    private void cleanup() {
        Path currentDir = AppState.get().getCurrentDirectory();
        if (currentDir == null) return;

        boolean modified = false;

        Iterator<String> iter = tagData.keySet().iterator();
        while (iter.hasNext()) {
            String filename = iter.next();
            Path filePath = currentDir.resolve(filename);
            if (!Files.exists(filePath)) {
                iter.remove();
                modified = true;
            }
        }

        if (modified) {
            save();
            System.out.println("[Cleanup] Ungültige Einträge in media_tags.json entfernt.");
        }
    }

    public void renameTag(String oldTag, String newTag) {
        boolean changed = false;

        for (String key : tagData.keySet()) {
            JSONArray arr = tagData.optJSONArray(key);
            if (arr == null) continue;

            List<String> tags = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String tag = arr.optString(i, "").trim();
                if (!tag.isEmpty()) {
                    tags.add(tag.equals(oldTag) ? newTag : tag);
                }
            }

            // Duplikate vermeiden, falls alter + neuer Tag gleichzeitig vorkamen
            Set<String> uniqueTags = new LinkedHashSet<>(tags);

            JSONArray newArr = new JSONArray();
            for (String tag : uniqueTags) {
                newArr.put(tag);
            }

            tagData.put(key, newArr);
            changed = true;
        }

        if (changed) {
            save();
        }
    }
}