package ui;

import service.Controller;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.nio.file.Path;

public class ImageViewer {

    ControlPanel controlPanel;
    ThumbnailPanel thumbnailPanel;

    MediaView mediaView;

    Controller controller = Controller.getInstance();

    public ImageViewer(){
        JFrame frame = new JFrame();
        controlPanel = new ControlPanel();
        thumbnailPanel = new ThumbnailPanel();
        mediaView = MediaView.getInstance();

        controller.setControlPanel(controlPanel);
        controller.setThumbnailPanel(thumbnailPanel);
        controller.setMediaPanel(mediaView);

        frame.setLayout(new BorderLayout());
        frame.add(controlPanel, BorderLayout.WEST);
        frame.add(thumbnailPanel, BorderLayout.CENTER);

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1200, 900);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);

        Path def = controller.loadDefaultDirectoryFromSettingsJson();
        controller.handleDirectory(def);

    }


}
