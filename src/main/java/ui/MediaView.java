package ui;

import service.Controller;
import service.RangeHandler;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class MediaView {

    private static final MediaView instance = new MediaView();


    public static MediaView getInstance() {
        return instance;
    }

    private final JFrame frame;
    private final CardLayout cardLayout;
    private final JPanel stackPanel;
    private final JLabel imageLabel;
    private final CallbackMediaPlayerComponent mediaPlayerComponent;

    RangeHandler.Range range;

    enum MODE {PLAY, STOP, PAUSE}

    private MODE mode;

    private MediaView() {
        frame = new JFrame("Media Viewer");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1280, 768);
        frame.setLayout(new BorderLayout());

        cardLayout = new CardLayout();
        stackPanel = new JPanel(cardLayout);

        // Bildanzeige
        imageLabel = new JLabel("", SwingConstants.CENTER);
        imageLabel.setBackground(Color.BLACK);
        imageLabel.setOpaque(true);
        stackPanel.add(imageLabel, "image");

        // Videoplayer-Komponente
        mediaPlayerComponent = new CallbackMediaPlayerComponent();
        stackPanel.add(mediaPlayerComponent.videoSurfaceComponent(), "video");

        frame.add(stackPanel, BorderLayout.CENTER);

        startPositionUpdateTimer();

        mediaPlayerComponent.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter(){
            @Override
            public void finished(MediaPlayer mediaPlayer) {
                super.finished(mediaPlayer);
                if (mode == MODE.PLAY){
                    play();
                }
            }
        });
    }

    private void startPositionUpdateTimer() {
        Timer timer = new Timer(500, e -> {
            long millis = mediaPlayerComponent.mediaPlayer().status().time();
            long total = mediaPlayerComponent.mediaPlayer().status().length();

            if (range != null && !Controller.getInstance().isIgnoreTimerange()) {
                long startMillis = (long) (range.start * 1000);
                long endMillis = (long) (range.end * 1000);

                if (millis < startMillis) {
                    mediaPlayerComponent.mediaPlayer().controls().setTime(startMillis);
                    return;
                }

                if (millis > endMillis) {
                    mediaPlayerComponent.mediaPlayer().controls().setTime(startMillis); // oder stop?
                    return;
                }
            }

            Controller.getInstance().showCurrentPlayPosMillis(millis, total);
        });
        timer.start();
    }

    public void display(File file) {
        if (file == null || !file.exists()) return;

        range = new RangeHandler().getRangeForFile(file);

        stop();

        frame.setVisible(true);

        String name = file.getName().toLowerCase();
        if (Controller.isImageFile(file)) {
            ImageIcon icon = new ImageIcon(file.getAbsolutePath());
            Image image = icon.getImage();

            int maxWidth = frame.getWidth() - 50;
            int maxHeight = frame.getHeight() - 70;
            double scale = Math.min((double) maxWidth / image.getWidth(null), (double) maxHeight / image.getHeight(null));

            int newWidth = (int) (image.getWidth(null) * scale);
            int newHeight = (int) (image.getHeight(null) * scale);

            imageLabel.setIcon(new ImageIcon(image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)));
            cardLayout.show(stackPanel, "image");

        }

        if (Controller.isVideoFile(file)) {
            cardLayout.show(stackPanel, "video");
            SwingUtilities.invokeLater(() -> {
                mediaPlayerComponent.mediaPlayer().media().play(file.getAbsolutePath());
            });
        }
    }

    public void play() {
        mode = MODE.PLAY;
        SwingUtilities.invokeLater(() -> {
            mediaPlayerComponent.mediaPlayer().controls().play();
        });
    }

    public void stop() {
        mode = MODE.STOP;
        SwingUtilities.invokeLater(() -> {
            mediaPlayerComponent.mediaPlayer().controls().stop();
        });
    }

    public void pause() {
        mode = MODE.PAUSE;
        SwingUtilities.invokeLater(() -> {
            mediaPlayerComponent.mediaPlayer().controls().pause();
        });
    }

    public void fullscreen(boolean fullscreen) {
        SwingUtilities.invokeLater(() -> {
            mediaPlayerComponent.mediaPlayer().fullScreen();
        });
    }

    public void setPlayPos(float playPosInPercentage) {
        SwingUtilities.invokeLater(() -> {
            mediaPlayerComponent.mediaPlayer().controls().setPosition(playPosInPercentage);
        });
    }
}