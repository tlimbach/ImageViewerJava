package service;

import model.AppState;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TagHandler {

    private static final TagHandler tagHandler = new TagHandler();

    public static TagHandler getInstance() {
        return tagHandler;
    }

    private final String tagFileName = "media_tags.json";
    private JSONObject tagData;

    private TagHandler() {
        load();
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
        File file = new File(tagFileName);
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
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(Paths.get(tagFileName))) {
            writer.write(tagData.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Integer> allTags() {
        Map<String, Integer> tagCounts = new HashMap<>();

        for (String key : tagData.keySet()) {
            Path filePath = Paths.get(key);
            if (!filePath.getParent().equals(AppState.get().getCurrentDirectory())) continue;

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

    public List<String> getFilesForSelectedTags(List<String> selectedTags) {
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
                if (!Collections.disjoint(fileTags, selectedTags)) {
                    if (Paths.get(key).getParent().equals(AppState.get().getCurrentDirectory())) {
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
            String absolutePath = file.getAbsolutePath();
            if (!tagData.has(absolutePath)) {
                untagged.add(file);
            } else {
                JSONArray arr = tagData.optJSONArray(absolutePath);
                if (arr == null || arr.isEmpty()) {
                    untagged.add(file);
                }
            }
        }

        return untagged;
    }
}