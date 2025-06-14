package service;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class H {

    public static Component makeHorizontalPanel(JComponent... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        Arrays.stream(components).forEach(panel::add);
        return panel;
    }

    public static void out(String string) {
        System.out.println(string);
    }

    public static void isUiThread(String a) {
        if (SwingUtilities.isEventDispatchThread()) {
            System.out.println("Ich bin im UI-Thread. " + a);
        } else {
            System.out.println("Ich bin NICHT im UI-Thread." + a);
        }
    }

    public static void sleep(int i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static BufferedImage rotate(BufferedImage img, int angleDegrees) {
        double radians = Math.toRadians(angleDegrees);

        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        int w = img.getWidth();
        int h = img.getHeight();
        int newW = (int) Math.floor(w * cos + h * sin);
        int newH = (int) Math.floor(h * cos + w * sin);

        BufferedImage rotated = new BufferedImage(newW, newH, img.getType());
        Graphics2D g2d = rotated.createGraphics();

        // Hintergrund optional: schwarz
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, newW, newH);

        // Translation: neue Mitte + Rotation + Rück-Translation
        g2d.translate((newW - w) / 2, (newH - h) / 2);
        g2d.rotate(radians, w / 2.0, h / 2.0);
        g2d.drawRenderedImage(img, null);
        g2d.dispose();
        return rotated;
    }
}
