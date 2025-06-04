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
        frame.setLayout(new BorderLayout());
        frame.add(controlPanel, BorderLayout.WEST);
        frame.add(thumbnailPanel, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        // Zweiten Monitor finden (sofern vorhanden)
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        GraphicsDevice targetDevice = devices.length > 1 ? devices[1] : devices[0];

        Rectangle bounds = targetDevice.getDefaultConfiguration().getBounds();

        // Fenster dorthin verschieben und maximieren
        frame.setLocation(bounds.x, bounds.y);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        frame.setVisible(true);

        Path def = controller.loadDefaultDirectoryFromSettingsJson();
        controller.handleDirectory(def);
        controller.updateUntaggedCount();
    }
}