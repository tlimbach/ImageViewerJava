package service;

import model.AppState;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TagHandler {

    private static TagHandler tagHandler = new TagHandler();

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

        String tagString = tagData.optString(filename, "").trim();
        if (tagString.isEmpty()) return Collections.emptyList();

        return Arrays.asList(tagString.split("\\s+"));
    }

    public void setTagsToFile(String tagString, String filename) {
        if (tagString == null || tagString.trim().isEmpty()) {
            tagData.remove(filename);
        } else {
            tagData.put(filename, tagString.trim());
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
            tagData = new JSONObject(); // fallback
        }
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(Paths.get(tagFileName))) {
            writer.write(tagData.toString(2)); // pretty print mit Einrückung
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Integer> allTags() {
        Map<String, Integer> tagCounts = new HashMap<>();


        for (String key : tagData.keySet()) {
            Path filePath = Paths.get(key);
            if (!filePath.getParent().equals(AppState.get().getCurrentDirectory())) continue;

            String tagString = tagData.optString(key, "");
            if (!tagString.isEmpty()) {
                String[] tags = tagString.trim().split("\\s+");
                for (String tag : tags) {
                    tagCounts.put(tag, tagCounts.getOrDefault(tag, 0) + 1);
                }
            }
        }

        return tagCounts;
    }

    public List<String> getFilesForSelectedTags(List<String> selectedTags) {
        long t0 = System.nanoTime();
        if (selectedTags == null || selectedTags.isEmpty()) return Collections.emptyList();


        List<String> matchingFiles = new ArrayList<>();

        for (String key : tagData.keySet()) {
            String tagString = tagData.optString(key, "");
            if (!tagString.isEmpty()) {
                Set<String> fileTags = new HashSet<>(Arrays.asList(tagString.trim().split("\\s+")));

                if (!Collections.disjoint(fileTags, selectedTags)) {
                    if (Paths.get(key).getParent().equals(AppState.get().getCurrentDirectory())) {
                        matchingFiles.add(key);
                    }
                }
            }
        }
        long t1 = System.nanoTime();
//        System.out.println("Tag filter duration: " + (t1 - t0)/1_000_000.0 + " ms");
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
                String tags = tagData.optString(absolutePath, "").trim();
                if (tags.isEmpty()) {
                    untagged.add(file);
                }
            }
        }

        return untagged;
    }
}