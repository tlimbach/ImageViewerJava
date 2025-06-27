package ui;

import event.CurrentDirectoryChangedEvent;
import model.AppState;
import service.Controller;
import service.EventBus;
import service.SettingsService;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ImageViewer {

    ControlPanel controlPanel;
    ThumbnailPanel thumbnailPanel;
    MediaView mediaView;

    Controller controller = Controller.getInstance();

    public ImageViewer() {

        Path def = SettingsService.getIntance().loadDefaultDirectoryFromSettingsJson();
        AppState.get().setCurrentDirectory(def);

        thumbnailPanel = new ThumbnailPanel();
        mediaView = MediaView.getInstance();
        controlPanel = new ControlPanel();

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

        EventBus.get().register(CurrentDirectoryChangedEvent.class, c->{
            frame.setTitle("Image Viewer - " + AppState.get().getCurrentDirectory());
        });

        frame.setTitle("Image Viewer - " + AppState.get().getCurrentDirectory());

//        Controller.getInstance().getExecutorService().submit(()->thumbnailPanel.reloadDirectory());

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            thumbnailPanel.reloadDirectory(); // Achtung: darf hier kein UI-Zugriff enthalten sein!
        }, 500, TimeUnit.MILLISECONDS);
    }
}