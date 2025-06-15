package service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class ThumbnailCache {

    private static final Map<File, byte[]> map = new HashMap<>();

    public static byte[] getByteArray(File file) {
        byte[] bytes = map.get(file);
        if (bytes != null) {
            return bytes;
        }

        try {
            bytes = Files.readAllBytes(file.toPath());
            map.put(file, bytes);
            H.out("total cache files: " + map.size());
            return bytes;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}