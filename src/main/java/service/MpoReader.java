package service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Robuster MPO-Reader — nutzt nur den stabilen JPGExtractor.
 */
public class MpoReader {

    /**
     * Gibt beide Frames zurück (links, rechts).
     */
    public static List<BufferedImage> getFrames(File mpoFile) throws IOException {
        JPGExtractor extractor = new JPGExtractor();
        return extractor.createBufferdImageFromMpo(mpoFile);
    }

    /**
     * Gibt nur das linke Bild zurück (für Thumbnails etc.).
     */
    public static BufferedImage getLeftFrame(File mpoFile) throws IOException {
        List<BufferedImage> frames = getFrames(mpoFile);
        return frames.get(0);
    }

    /**
     * Gibt nur das rechte Bild zurück (optional, selten genutzt).
     */
    public static BufferedImage getRightFrame(File mpoFile) throws IOException {
        List<BufferedImage> frames = getFrames(mpoFile);
        return frames.get(1);
    }

}