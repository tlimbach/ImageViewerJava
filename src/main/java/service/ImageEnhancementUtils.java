package service;

import java.awt.Color;
import java.awt.Transparency;
import java.awt.image.BufferedImage;

public final class ImageEnhancementUtils {
    private ImageEnhancementUtils() {
    }

    public static BufferedImage adjustSaturation(BufferedImage source, ImageSaturationHandler.SaturationLevel level) {
        if (source == null || level == null || level == ImageSaturationHandler.SaturationLevel.NORMAL) {
            return source;
        }

        int type = source.getTransparency() == Transparency.OPAQUE
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), type);
        float factor = level.factor();
        float[] hsb = new float[3];

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                int red = (argb >>> 16) & 0xff;
                int green = (argb >>> 8) & 0xff;
                int blue = argb & 0xff;

                Color.RGBtoHSB(red, green, blue, hsb);
                hsb[1] = Math.min(1.0f, hsb[1] * factor);
                int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0x00ffffff;
                result.setRGB(x, y, (alpha << 24) | rgb);
            }
        }

        return result;
    }
}
