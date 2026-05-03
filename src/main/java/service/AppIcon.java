package service;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AppIcon {
    private static final String ICON_RESOURCE = "/app-icon.png";
    private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256, 512};
    private static List<Image> iconImages;

    private AppIcon() {
    }

    public static void applyTo(JFrame frame) {
        List<Image> images = getIconImages();
        if (!images.isEmpty()) {
            frame.setIconImages(images);
        }
    }

    public static void installTaskbarIcon() {
        if (!Taskbar.isTaskbarSupported()) {
            return;
        }

        List<Image> images = getIconImages();
        if (images.isEmpty()) {
            return;
        }

        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(images.get(images.size() - 1));
            }
        } catch (UnsupportedOperationException | SecurityException e) {
            System.err.println("App-Icon konnte nicht fuer die Taskbar gesetzt werden: " + e.getMessage());
        }
    }

    private static List<Image> getIconImages() {
        if (iconImages != null) {
            return iconImages;
        }

        BufferedImage source = loadSourceIcon();
        if (source == null) {
            iconImages = Collections.emptyList();
            return iconImages;
        }

        List<Image> images = new ArrayList<>();
        for (int size : ICON_SIZES) {
            images.add(scale(source, size));
        }
        iconImages = Collections.unmodifiableList(images);
        return iconImages;
    }

    private static BufferedImage loadSourceIcon() {
        try (InputStream in = AppIcon.class.getResourceAsStream(ICON_RESOURCE)) {
            if (in == null) {
                System.err.println("App-Icon Resource fehlt: " + ICON_RESOURCE);
                return null;
            }
            return ImageIO.read(in);
        } catch (IOException e) {
            System.err.println("App-Icon konnte nicht geladen werden: " + e.getMessage());
            return null;
        }
    }

    private static BufferedImage scale(BufferedImage source, int size) {
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return scaled;
    }
}
