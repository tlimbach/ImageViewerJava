package service;

import java.awt.*;
import java.awt.image.BufferedImage;

public class AnaglyphUtils {

    private static float[][] LM = {
            { 0.437f, 0.449f, 0.164f },
            { -0.062f, -0.062f, -0.024f },
            { -0.048f, -0.050f, -0.017f },
            { 0.378f, 0.733f, 0.088f },
            { -0.086f, -0.089f, -0.034f },
            { -0.016f, -0.017f, -0.006f }
    };

    public static float[][] getDuboisMatrix() { return LM; }
    public static void setDuboisMatrix(float[][] newMatrix) {
        LM = newMatrix;
    }

    // Beispiel-Anaglyph-Erzeugung (du hast wahrscheinlich eine eigene!)
    public static BufferedImage createDuboisAnaglyph(BufferedImage left, BufferedImage right) {
        int w = Math.min(left.getWidth(), right.getWidth());
        int h = Math.min(left.getHeight(), right.getHeight());
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgbL = left.getRGB(x, y);
                int rgbR = right.getRGB(x, y);

                int rL = (rgbL >> 16) & 0xFF;
                int gL = (rgbL >> 8) & 0xFF;
                int bL = rgbL & 0xFF;

                int rR = (rgbR >> 16) & 0xFF;
                int gR = (rgbR >> 8) & 0xFF;
                int bR = rgbR & 0xFF;

                int red   = clip((int)(LM[0][0] * rL + LM[0][1] * gL + LM[0][2] * bL +
                        LM[1][0] * rR + LM[1][1] * gR + LM[1][2] * bR));
                int green = clip((int)(LM[2][0] * rL + LM[2][1] * gL + LM[2][2] * bL +
                        LM[3][0] * rR + LM[3][1] * gR + LM[3][2] * bR));
                int blue  = clip((int)(LM[4][0] * rL + LM[4][1] * gL + LM[4][2] * bL +
                        LM[5][0] * rR + LM[5][1] * gR + LM[5][2] * bR));

                int rgb = (red << 16) | (green << 8) | blue;
                result.setRGB(x, y, rgb);
            }
        }

        return result;
    }



    /**
     * Erstellt ein einfaches Rot-Cyan Anaglyph aus zwei Bildern mit gegebenem horizontalen Versatz.
     */
    public static BufferedImage createSimpleAnaglyph(BufferedImage left, BufferedImage right, double parallax) {
        int pixelShift = (int) Math.round(parallax * right.getWidth());

        int width = Math.min(left.getWidth(), right.getWidth() - Math.abs(pixelShift));
        int height = Math.min(left.getHeight(), right.getHeight());

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int lx = x;
                int rx = x + pixelShift;

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
    /**
     * Variante A: Einfacher Helligkeits-Deckel für den rechten Kanal.
     */
    public static BufferedImage createSimpleAnaglyphWithVarianteA(
            BufferedImage left, BufferedImage right, double parallax, float rightBrightnessFactor) {

        int pixelShift = (int) Math.round(parallax * right.getWidth());

        int width = Math.min(left.getWidth(), right.getWidth() - Math.abs(pixelShift));
        int height = Math.min(left.getHeight(), right.getHeight());

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int lx = x;
                int rx = x + pixelShift;  // korrektes Vorzeichen!

                if (rx < 0 || rx >= right.getWidth()) continue;

                int leftPixel = left.getRGB(lx, y);
                int rightPixel = right.getRGB(rx, y);

                int r = (leftPixel >> 16) & 0xFF;

                int g = (int)(((rightPixel >> 8) & 0xFF) * rightBrightnessFactor);
                int b = (int)((rightPixel & 0xFF) * rightBrightnessFactor);

                int rgb = (r << 16) | (clip(g) << 8) | clip(b);
                result.setRGB(x, y, rgb);
            }
        }

        return result;
    }

    /**
     * Variante B: Helligkeit und optional Sättigung via HSB.
     */
    public static BufferedImage createSimpleAnaglyphWithVarianteB(
            BufferedImage left, BufferedImage right, double parallax, float rightBrightnessFactor, float rightSaturationFactor) {

        int pixelShift = (int) Math.round(parallax * right.getWidth());

        int width = Math.min(left.getWidth(), right.getWidth() - Math.abs(pixelShift));
        int height = Math.min(left.getHeight(), right.getHeight());

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int lx = x;
                int rx = x + pixelShift;  // korrektes Vorzeichen!

                if (rx < 0 || rx >= right.getWidth()) continue;

                int leftPixel = left.getRGB(lx, y);
                int rightPixel = right.getRGB(rx, y);

                int r = (leftPixel >> 16) & 0xFF;

                // Für rechts: HSB
                int gRaw = (rightPixel >> 8) & 0xFF;
                int bRaw = rightPixel & 0xFF;
                int rRaw = (rightPixel >> 16) & 0xFF;

                float[] hsb = Color.RGBtoHSB(rRaw, gRaw, bRaw, null);
                hsb[1] = clamp(hsb[1] * rightSaturationFactor);
                hsb[2] = clamp(hsb[2] * rightBrightnessFactor);
                int rgbRight = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);

                int g = (rgbRight >> 8) & 0xFF;
                int b = rgbRight & 0xFF;

                int rgb = (r << 16) | (g << 8) | b;
                result.setRGB(x, y, rgb);
            }
        }

        return result;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int clip(int v) {
        return Math.max(0, Math.min(255, v));
    }
}