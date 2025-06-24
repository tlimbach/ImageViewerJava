package ui;

import javax.swing.*;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.TimerTask;
import java.util.function.Consumer;

public class CroppableImagePanel extends JPanel {

    private final BufferedImage image;
    private Rectangle selection;
    private Point startPoint;
    private final double aspectRatio;
    private final java.util.Timer timer = new java.util.Timer();
    private TimerTask cropTask;

    private final Consumer<BufferedImage> onCropFinished;

    public CroppableImagePanel(BufferedImage image, Consumer<BufferedImage> onCropFinished) {
        this.image = image;
        this.aspectRatio = (double) image.getWidth() / image.getHeight();
        this.onCropFinished = onCropFinished;

        MouseAdapter handler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                startPoint = e.getPoint();
                selection = new Rectangle();
                cancelCropTask();
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int x1 = Math.min(startPoint.x, e.getX());
                int y1 = Math.min(startPoint.y, e.getY());
                int w = Math.abs(e.getX() - startPoint.x);
                int h = (int) (w / aspectRatio);

                // Begrenzen
                if (y1 + h > getHeight()) {
                    h = getHeight() - y1;
                    w = (int) (h * aspectRatio);
                }
                if (x1 + w > getWidth()) {
                    w = getWidth() - x1;
                    h = (int) (w / aspectRatio);
                }

                selection.setBounds(x1, y1, w, h);
                scheduleCropTask();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                scheduleCropTask();
            }
        };

        addMouseListener(handler);
        addMouseMotionListener(handler);
    }

    private void cancelCropTask() {
        if (cropTask != null) {
            cropTask.cancel();
            cropTask = null;
        }
    }

    private void scheduleCropTask() {
        cancelCropTask();
        cropTask = new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    BufferedImage cropped = getCroppedImage();
                    if (cropped != null) {
                        onCropFinished.accept(cropped);
                    }
                });
            }
        };
        timer.schedule(cropTask, 2000);
    }

    private BufferedImage getCroppedImage() {
        if (selection == null) return null;

        int imgX = (getWidth() - image.getWidth()) / 2;
        int imgY = (getHeight() - image.getHeight()) / 2;

        int cropX = selection.x - imgX;
        int cropY = selection.y - imgY;

        cropX = Math.max(0, cropX);
        cropY = Math.max(0, cropY);
        int cropW = Math.min(selection.width, image.getWidth() - cropX);
        int cropH = Math.min(selection.height, image.getHeight() - cropY);

        return image.getSubimage(cropX, cropY, cropW, cropH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int x = (getWidth() - image.getWidth()) / 2;
        int y = (getHeight() - image.getHeight()) / 2;
        g.drawImage(image, x, y, this);

        if (selection != null) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(2));
            g2.draw(selection);
        }
    }
}