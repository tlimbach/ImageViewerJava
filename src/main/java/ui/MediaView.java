package ui;

import event.*;
import model.AppState;
import service.*;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.base.State;
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class MediaView {

    private static final MediaView instance = new MediaView();
    private File currentFile;
    private RangeHandler.Range range;

    private final JFrame frame;
    private final CardLayout cardLayout;
    private final JPanel stackPanel;
    private final JLabel imageLabel;
    private final CallbackMediaPlayerComponent mediaPlayerComponent;

    private final LeftBar leftBar = new LeftBar();

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

        frame.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (currentFile == null) return;
                int rot = RotationHandler.getInstance().getRotation(currentFile);
                if (e.getKeyChar() == 'l') {
                    RotationHandler.getInstance().setRotation(currentFile, rot - 90);
                } else if (e.getKeyChar() == 'r') {
                    RotationHandler.getInstance().setRotation(currentFile, rot + 90);
                }
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
        JLayeredPane layeredPane = frame.getLayeredPane();
        leftBar.setBounds(0, 0, 10, frame.getHeight()); // Initialgröße
        layeredPane.add(leftBar, JLayeredPane.PALETTE_LAYER);

        initPlayerListener();
        startPositionUpdateTimer();

        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                leftBar.setBounds(0, 0, 10, frame.getHeight());
            }
        });

        EventBus.get().register(MediaviewPlayEvent.class, e -> {
            if (e.selected()) {
                play();
            } else {
                pause();
            }
        });

        EventBus.get().register(MediaViewStopEvent.class, e -> {
            stop();
            fullscreen(false);
            hideFrame();
        });

        EventBus.get().register(CurrentPlaybackSliderPosEvent.class, e -> {
            SwingUtilities.invokeLater(() -> mediaPlayerComponent.mediaPlayer().controls().setPosition(e.value()));
        });

        EventBus.get().register(MediaViewFullscreenEvent.class, e -> {
            fullscreen(e.selected());
        });

        EventBus.get().register(VolumeChangedEvent.class, e -> {
            setVolume(e.volume());
        });

        EventBus.get().register(RangeChangedEvent.class, r -> {
            range = RangeHandler.getInstance().getRangeForFile(currentFile);
        });

        EventBus.get().register(RotationChangedEvent.class, e -> {
            if (currentFile != null && currentFile.equals(e.file())) {
                showImage(currentFile);
            }
        });
    }

    private void initPlayerListener() {
        mediaPlayerComponent.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void finished(MediaPlayer mediaPlayer) {
                if (currentFile == null) return;

                SwingUtilities.invokeLater(() -> {
                    MediaPlayer player = mediaPlayerComponent.mediaPlayer();

                    if (range != null && !AppState.get().isIgnoreTimerange()) {
                        player.media().startPaused(currentFile.getAbsolutePath());
                        player.controls().setTime((long) (range.start * 1000));
                        player.controls().play();
                    } else {
                        player.media().play(currentFile.getAbsolutePath());
                    }
                });
            }
        });
    }

    private void startPositionUpdateTimer() {
        Timer timer = new Timer(500, e -> {
            MediaPlayer player = mediaPlayerComponent.mediaPlayer();
            long millis = player.status().time();
            long total = player.status().length();

            if (range != null && !AppState.get().isIgnoreTimerange()) {
                long startMillis = (long) (range.start * 1000);
                long endMillis = (long) (range.end * 1000);

                if (millis < startMillis || millis > endMillis) {
                    player.controls().setTime(startMillis);
                    return;
                }
            }

            EventBus.get().publish(new CurrentPlaybackPosEvent(millis, total));
        });
        timer.start();
    }

    boolean isFirstAufruf = true;

    public void display(File file, boolean autostart) {
        if (file == null || !file.exists()) return;

        currentFile = file;
        stop();
        range = RangeHandler.getInstance().getRangeForFile(file);

        if (Controller.isImageFile(file)) {
            if (isFirstAufruf) {
                fullscreen(AppState.get().isMediaviewFullscreen());
                isFirstAufruf = false;
                // Wichtig: wir warten auf Validierung
                SwingUtilities.invokeLater(() -> {
                    frame.setVisible(true);
                    showImage(file);
                });
                return;
            }
            frame.setVisible(true);
            showImage(file);
        } else if (Controller.isVideoFile(file)) {
            showVideo(file, autostart);
        }
    }

    private void showImage(File file) {

        BufferedImage image = AppState.get().getPreloadedImage();

        if (image == null) {
            try {
                image = ImageIO.read(file);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        } else {
            H.out("preload hat gezogen");
        }

        AppState.get().setPreloadedImage(null);

        if (image == null) {
            System.err.println("Konnte Bild nicht laden: " + file);
            return;
        }
        int rotation = RotationHandler.getInstance().getRotation(file);

        BufferedImage rotatedImage = H.rotate(image, rotation);

        int maxWidth = frame.getWidth();
        int maxHeight = frame.getHeight();
        double scale = Math.min((double) maxWidth / rotatedImage.getWidth(), (double) maxHeight / rotatedImage.getHeight());

        int newWidth = (int) (rotatedImage.getWidth() * scale);
        int newHeight = (int) (rotatedImage.getHeight() * scale);

        if (Controller.getInstance().getControlPanel().getSlideshowManager().isRunning()) {
            for (Component comp : stackPanel.getComponents()) {
                if (comp instanceof AnimatedImagePanel) {
                    stackPanel.remove(comp);
                    break;
                }
            }
            AnimatedImagePanel animatedPanel = new AnimatedImagePanel(rotatedImage, newWidth, newHeight);
            stackPanel.add(animatedPanel, "animated");
            cardLayout.show(stackPanel, "animated");
        } else {
            imageLabel.setIcon(new ImageIcon(rotatedImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)));
            cardLayout.show(stackPanel, "image");
        }

        if (Controller.getInstance().getControlPanel().getSlideshowManager().isRunning()) {
            leftBar.start(Controller.getInstance().getControlPanel().getSlideshowManager().getDurationMillis());
        } else {
            leftBar.setVisible(false);
        }
    }

    private void showVideo(File file, boolean autostart) {
        cardLayout.show(stackPanel, "video");
        if (autostart) {
            frame.setVisible(true);
            playVideoFile(file);
        }
    }

    private void playVideoFile(File file) {
        int vol = VolumeHandler.getInstance().getVolumeForFile(file.getAbsolutePath());
        SwingUtilities.invokeLater(() -> {
            MediaPlayer player = mediaPlayerComponent.mediaPlayer();

            if (range != null && !AppState.get().isIgnoreTimerange()) {
                player.media().startPaused(file.getAbsolutePath());
                player.controls().setTime((long) (range.start * 1000));
                player.controls().play();
                setVolume(vol);
            } else {
                player.media().play(file.getAbsolutePath());
                setVolume(vol);
            }

        });
        Timer t = new Timer(30, e -> setVolume(vol));
        t.setRepeats(false);
        t.start();

    }

    public void setVolume(int vol) {
        SwingUtilities.invokeLater(() -> {
            MediaPlayer player = mediaPlayerComponent.mediaPlayer();
            player.audio().setVolume(vol);
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
    }

    public void pause() {
        SwingUtilities.invokeLater(() -> mediaPlayerComponent.mediaPlayer().controls().pause());
    }

    private boolean isFullscreen = false;
    private Rectangle windowedBounds;

    private GraphicsDevice getCurrentScreenDeviceForFrame(JFrame frame) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = ge.getScreenDevices();

        Rectangle frameBounds = frame.getBounds();
        for (GraphicsDevice device : devices) {
            GraphicsConfiguration config = device.getDefaultConfiguration();
            Rectangle screenBounds = config.getBounds();
            if (screenBounds.intersects(frameBounds)) {
                return device;
            }
        }

        // Fallback: primäres Gerät
        return ge.getDefaultScreenDevice();
    }


    public void fullscreen(boolean fullscreen) {
        if (fullscreen == isFullscreen) return;

        SwingUtilities.invokeLater(() -> {
            GraphicsDevice device = getCurrentScreenDeviceForFrame(frame);

            if (fullscreen) {
                windowedBounds = frame.getBounds();
                frame.dispose();
                frame.setUndecorated(true);
                device.setFullScreenWindow(frame);
                frame.setVisible(true);
            } else {
                device.setFullScreenWindow(null);
                frame.dispose();
                frame.setUndecorated(false);
                frame.setBounds(windowedBounds);
                frame.setVisible(true);
            }

            frame.validate();
            frame.repaint();
            mediaPlayerComponent.videoSurfaceComponent().revalidate();
            mediaPlayerComponent.videoSurfaceComponent().repaint();

            isFullscreen = fullscreen;
        });
    }


    public boolean isVideoPlaying() {
        return mediaPlayerComponent.mediaPlayer().status().isPlaying();
    }

    public LeftBar getLeftBar() {
        return leftBar;
    }

    public void hideFrame() {
        frame.setVisible(false);
    }
}
