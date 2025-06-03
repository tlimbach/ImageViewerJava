package ui;

import service.Controller;
import service.RangeHandler;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.base.State;
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class MediaView {

    private static final MediaView instance = new MediaView();
    private File currentFile;
    private RangeHandler.Range range;

    private final JFrame frame;
    private final CardLayout cardLayout;
    private final JPanel stackPanel;
    private final JLabel imageLabel;
    private final CallbackMediaPlayerComponent mediaPlayerComponent;

    public static MediaView getInstance() {
        return instance;
    }

    private MediaView() {
        frame = new JFrame("Media Viewer");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1280, 768);
        frame.setLayout(new BorderLayout());

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                mediaPlayerComponent.mediaPlayer().controls().stop();
               // mediaPlayerComponent.mediaPlayer().release(); // Optional: VLC-Ressourcen freigeben
                Controller.getInstance().getControlPanel().resetPlayPauseButton();
            }
        });

        cardLayout = new CardLayout();
        stackPanel = new JPanel(cardLayout);

        imageLabel = new JLabel("", SwingConstants.CENTER);
        imageLabel.setBackground(Color.BLACK);
        imageLabel.setOpaque(true);
        stackPanel.add(imageLabel, "image");

        mediaPlayerComponent = new CallbackMediaPlayerComponent();
        stackPanel.add(mediaPlayerComponent.videoSurfaceComponent(), "video");

        frame.add(stackPanel, BorderLayout.CENTER);

        initPlayerListener();
        startPositionUpdateTimer();
    }

    private void initPlayerListener() {
        mediaPlayerComponent.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void finished(MediaPlayer mediaPlayer) {
                if (currentFile != null) {
                    if (range != null && !Controller.getInstance().isIgnoreTimerange()) {
                        mediaPlayer.controls().setTime((long) (range.start * 1000));
                        mediaPlayer.controls().play();
                    } else {
                        mediaPlayer.media().play(currentFile.getAbsolutePath());
                    }
                }
            }
        });
    }

    private void startPositionUpdateTimer() {
        Timer timer = new Timer(500, e -> {
            MediaPlayer player = mediaPlayerComponent.mediaPlayer();
            long millis = player.status().time();
            long total = player.status().length();

            if (range != null && !Controller.getInstance().isIgnoreTimerange()) {
                long startMillis = (long) (range.start * 1000);
                long endMillis = (long) (range.end * 1000);

                if (millis < startMillis || millis > endMillis) {
                    player.controls().setTime(startMillis);
                    return;
                }
            }

            Controller.getInstance().showCurrentPlayPosMillis(millis, total);
        });
        timer.start();
    }

    public void display(File file, boolean autostart) {
        if (file == null || !file.exists()) return;

        currentFile = file;
        stop();
        range = new RangeHandler().getRangeForFile(file);



        if (Controller.isImageFile(file)) {
            frame.setVisible(true);
            showImage(file);
        } else if (Controller.isVideoFile(file)) {
            showVideo(file, autostart);
        }
    }

    private void showImage(File file) {
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

    private void showVideo(File file, boolean autostart) {
        cardLayout.show(stackPanel, "video");
        if (autostart) {
            frame.setVisible(true);
            playVideoFile(file);
        }
    }

    private void playVideoFile(File file) {
        SwingUtilities.invokeLater(() -> {
            MediaPlayer player = mediaPlayerComponent.mediaPlayer();
            if (range != null && !Controller.getInstance().isIgnoreTimerange()) {
                player.media().startPaused(file.getAbsolutePath());
                player.controls().setTime((long) (range.start * 1000));
                player.controls().play();
            } else {
                player.media().play(file.getAbsolutePath());
            }
        });
    }

    public void play() {
        MediaPlayer player = mediaPlayerComponent.mediaPlayer();
        if (player.status().isPlayable() && player.status().state() == State.PAUSED) {
            player.controls().play();
        } else if (currentFile != null) {
            frame.setVisible(true);
            playVideoFile(currentFile);
        }
    }

    public void stop() {
        SwingUtilities.invokeLater(() -> mediaPlayerComponent.mediaPlayer().controls().stop());
        frame.dispose();
    }

    public void pause() {
        SwingUtilities.invokeLater(() -> mediaPlayerComponent.mediaPlayer().controls().pause());
    }

    public void fullscreen(boolean fullscreen) {
//        SwingUtilities.invokeLater(() -> mediaPlayerComponent.mediaPlayer().fullScreen());
    }

    public void setPlayPos(float playPosInPercentage) {
        SwingUtilities.invokeLater(() -> mediaPlayerComponent.mediaPlayer().controls().setPosition(playPosInPercentage));
    }
}
