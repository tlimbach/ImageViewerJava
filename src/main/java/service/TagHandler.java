package service;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TagHandler {

    private final String tagFileName = "media_tags.json";
    private JSONObject tagData;

    public TagHandler() {
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

    public List<String> allTags() {
        Set<String> uniqueTags = new HashSet<>();
        Path currentDir = Controller.getInstance().getCurrentDirectory();

        for (String key : tagData.keySet()) {
            Path filePath = Paths.get(key);
            if (!filePath.getParent().equals(currentDir)) continue;

            String tagString = tagData.optString(key, "");
            if (!tagString.isEmpty()) {
                String[] tags = tagString.trim().split("\\s+");
                Collections.addAll(uniqueTags, tags);
            }
        }

        List<String> result = new ArrayList<>(uniqueTags);
        Collections.sort(result);
        return result;
    }

    public List<String> getFilesForSelectedTags(List<String> selectedTags) {
        long t0 = System.nanoTime();
        if (selectedTags == null || selectedTags.isEmpty()) return Collections.emptyList();

        Path currentDir = Controller.getInstance().getCurrentDirectory();
        List<String> matchingFiles = new ArrayList<>();

        for (String key : tagData.keySet()) {
            String tagString = tagData.optString(key, "");
            if (!tagString.isEmpty()) {
                Set<String> fileTags = new HashSet<>(Arrays.asList(tagString.trim().split("\\s+")));

                if (!Collections.disjoint(fileTags, selectedTags)) {
                    if (Paths.get(key).getParent().equals(currentDir)) {
                        matchingFiles.add(key);
                    }
                }
            }
        }
        long t1 = System.nanoTime();
//        System.out.println("Tag filter duration: " + (t1 - t0)/1_000_000.0 + " ms");
        return matchingFiles;
    }
}