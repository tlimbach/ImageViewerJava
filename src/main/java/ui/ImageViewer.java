package ui;

import service.Controller;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

public class ImageViewer {

    ControlPanel controlPanel;
    ThumbnailPanel thumbnailPanel;
    MediaView mediaView;

    Controller controller = Controller.getInstance();

    public ImageViewer() {
        controlPanel = new ControlPanel();
        thumbnailPanel = new ThumbnailPanel();
        mediaView = MediaView.getInstance();

        controller.setControlPanel(controlPanel);
        controller.setThumbnailPanel(thumbnailPanel);
        controller.setMediaPanel(mediaView);

        JFrame frame = new JFrame("Image Viewer");
        frame.setUndecorated(true); // Für echten Vollbildmodus
        frame.setLayout(new BorderLayout());
        frame.add(controlPanel, BorderLayout.WEST);
        frame.add(thumbnailPanel, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        // Zweiten Monitor finden (sofern vorhanden)
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        GraphicsDevice targetDevice = devices.length > 1 ? devices[1] : devices[0];

        // Vollbild auf Zielgerät setzen
        GraphicsConfiguration config = targetDevice.getDefaultConfiguration();
        Rectangle bounds = config.getBounds();

        frame.setBounds(bounds);
        targetDevice.setFullScreenWindow(frame);

        frame.setVisible(true);

        // Standardverzeichnis laden
        Path def = controller.loadDefaultDirectoryFromSettingsJson();
        controller.handleDirectory(def);
        controller.updateUntaggedCount();
    }
}