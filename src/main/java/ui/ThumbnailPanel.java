package ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public class ThumbnailPanel extends JPanel {

    private final JPanel gridPanel;
    private final JScrollPane scrollPane;

    private final static int THUMBNAIL_SIZE = 240;

    public ThumbnailPanel() {
        setLayout(new BorderLayout());

        gridPanel = new JPanel(new GridLayout(0, 4, 10, 10)); // 4 Spalten, variable Zeilen, Lücken: 10px
        scrollPane = new JScrollPane(gridPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void populate(List<File> mediaFiles) {
        System.out.println("POpulating "+ mediaFiles.size() + " images");
        gridPanel.removeAll();

        for (File file : mediaFiles) {
            try {
                ImageIcon icon = new ImageIcon(file.getAbsolutePath());
                Image scaled = getScaledImagePreserveRatio(icon.getImage(), THUMBNAIL_SIZE, THUMBNAIL_SIZE);
                JLabel label = new JLabel(new ImageIcon(scaled));
                gridPanel.add(label);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private Image getScaledImagePreserveRatio(Image srcImg, int maxWidth, int maxHeight) {
        int srcWidth = srcImg.getWidth(null);
        int srcHeight = srcImg.getHeight(null);

        if (srcWidth <= 0 || srcHeight <= 0) return srcImg;

        double widthRatio = (double) maxWidth / srcWidth;
        double heightRatio = (double) maxHeight / srcHeight;
        double scale = Math.min(widthRatio, heightRatio);

        int newWidth = (int) (srcWidth * scale);
        int newHeight = (int) (srcHeight * scale);

        return srcImg.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
    }
}