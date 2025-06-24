package service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Robuster MPO-Reader — nutzt nur den stabilen JPGExtractor.
 */
public class MpoReader {

    private static String preloadedFileName;
    private static List<BufferedImage> preloadedBuffereedImages;

    /**
     * Gibt beide Frames zurück (links, rechts).
     */
    public static List<BufferedImage> getFrames(File mpoFile) throws IOException {

        if (mpoFile.getName().equals(preloadedFileName)) {
            H.out("from cahcscsojcso");
            return preloadedBuffereedImages;
        }

        JPGExtractor extractor = new JPGExtractor();
        return extractor.createBufferdImageFromMpo(mpoFile);
    }

    /**
     * Gibt nur das linke Bild zurück (für Thumbnails etc.).
     */
    public static BufferedImage getLeftFrame(File mpoFile) throws IOException {
     return new JPGExtractor().createLeftThumbnailFromMpo(mpoFile, 450);
    }


    public static void preloadFrames(File file) throws IOException {
        preloadedBuffereedImages = getFrames(file);
        preloadedFileName = file.getName();

        H.out("preloded mpo" + preloadedFileName);
    }
}