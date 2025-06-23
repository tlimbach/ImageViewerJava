package service;

import java.awt.image.BufferedImage;

public class AnaglyphUtils {

    /**
     * Erstellt ein einfaches Rot-Cyan Anaglyph aus zwei Bildern mit gegebenem horizontalen Versatz.
     */
    public static BufferedImage createSimpleAnaglyph(BufferedImage left, BufferedImage right, int parallax) {
        int width = Math.min(left.getWidth(), right.getWidth() - Math.abs(parallax));
        int height = Math.min(left.getHeight(), right.getHeight());

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int lx = x;
                int rx = parallax >= 0 ? x + parallax : x - parallax;

                if (rx < 0 || rx >= right.getWidth()) continue;

                int leftPixel = left.getRGB(lx, y);
                int rightPixel = right.getRGB(rx, y);

                int r = (leftPixel >> 16) & 0xFF;
                int g = (rightPixel >> 8) & 0xFF;
                int b = rightPixel & 0xFF;

                int rgb = (r << 16) | (g << 8) | b;
                result.setRGB(x, y, rgb);
            }
        }

        return result;
    }
}