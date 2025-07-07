package ui;

import event.*;
import model.AppState;
import service.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class ControlPanel extends JPanel {

    private final Controller controller = Controller.getInstance();
    private final JLabel lblPosition = new JLabel("----");
    private final JLabel lblThumbnailsLoadedCount = new JLabel("---------");
    private final JTextField txtTimerangeStart = new JTextField(5);
    private final JTextField txtTimerangeEnde = new JTextField(5);
    private final JCheckBox cbxIgnoreTimerange = new JCheckBox("Z. ignorieren");

    private final JButton btnCreateAutoTimerane = new JButton("Automatisch erzeugen");
    private final JCheckBox cbxAutostart = new JCheckBox("Autostart");

    private final JSlider sldMoviePosition = new JSlider();
    private JTextField txtDuration;
    private JToggleButton btnPlayPause;
    private boolean isUpdatingFromCode = false;
    private final SlideshowManager slideshowManager = new SlideshowManager();

    private JLabel txtUntaggedCount;

    private JSlider sldVolume;
    private JLabel lblVol;

    private JLabel lblParalaxe;

    private JSlider sldParalaxe;

    private RangeHandler rangeHandler = RangeHandler.getInstance();
    private TagSelectionPanel tagSelectionPanel;
    private TagEditDialog tagEditDialog;
    private long lastSliderEventTime;

    private boolean isUserDraggingSlider = false;
    private long lastUserSliderChange = 0;

    public ControlPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        addFileChooserButton();
        addSlideshowControls();
        addPlaybackControls();
        addRangeControls();
        addSliderPositionControl();
        addVolumeControl();
        addTagControls();
//        addBrillenSetup();
        addParalaxeControl();

        add(H.makeHorizontalPanel(lblThumbnailsLoadedCount));

        EventBus.get().register(CurrentPlaybackPosEvent.class, e -> {
            setCurrentPlayPosMillis(e.currentMillis(), e.totalMinis());
        });

        EventBus.get().register(CurrentlySelectedFileEvent.class, e -> {
            setSelectedFile(e.file());
        });

        EventBus.get().register(ThumbnailsLoadedEvent.class, e -> {
            setThumbnailsLoaded(e.loaded(), e.total());
        });

        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> {
            updateUntaggedFilesCount();
            tagSelectionPanel.reloadTags();
        });

        EventBus.get().register(TagsChangedEvent.class, e->{
            updateUntaggedFilesCount();
            tagSelectionPanel.reloadTags();
        });

        EventBus.get().register(UserKeyboardEvent.class, e -> {
            String direction = e.direction();

            // Hier: PAGE_UP und PAGE_DOWN (und weitere) verarbeiten
            if ("PAGE_UP".equals(direction)) {
                int value = sldParalaxe.getValue();
                sldParalaxe.setValue(Math.min(value + 1, sldParalaxe.getMaximum()));
            } else if ("PAGE_DOWN".equals(direction)) {
                int value = sldParalaxe.getValue();
                sldParalaxe.setValue(Math.max(value - 1, sldParalaxe.getMinimum()));
            } else if ("HOME".equals(direction)) {
                int value = sldParalaxe.getValue();
                sldParalaxe.setValue(Math.min(value + 5, sldParalaxe.getMaximum()));
            } else if ("END".equals(direction)) {
                int value = sldParalaxe.getValue();
                sldParalaxe.setValue(Math.max(value - 5, sldParalaxe.getMinimum()));
            } else if ("EINFG".equals(direction)) {
                int value = sldParalaxe.getValue();
                sldParalaxe.setValue(Math.min(value + 20, sldParalaxe.getMaximum()));
            } else if ("ENTF".equals(direction)) {
                int value = sldParalaxe.getValue();
                sldParalaxe.setValue(Math.max(value - 20, sldParalaxe.getMinimum()));
            }
        });

        updateUntaggedFilesCount();

    }



    private void addFileChooserButton() {
        JButton btnFileChooser = new JButton("Verzeichnis wählen");
        JLabel lblInfo = new JLabel("Noch nichts gewählt");

        btnFileChooser.addActionListener(a -> {


            EventBus.get().publish(new MediaViewStopEvent());
            new Timer(10, e -> EventBus.get().publish(new MediaViewStopEvent())) {{
                setRepeats(false);
                start();
            }};


            JFileChooser chooser = new JFileChooser();
            chooser.setPreferredSize(new Dimension(800, 600));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAccessory(lblInfo);

            chooser.addPropertyChangeListener(evt -> {
                if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(evt.getPropertyName())
                        || JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                    File selected = (File) evt.getNewValue();
                    if (selected != null && selected.isDirectory()) {
                        File[] files = selected.listFiles();
                        int imageCount = (int) Arrays.stream(files).filter(Controller::isImageFile).count();
                        int videoCount = (int) Arrays.stream(files).filter(Controller::isVideoFile).count();
                        lblInfo.setText("<html><br>Bilder: " + imageCount + "<br>Videos: " + videoCount + "</html>");
                    } else {
                        lblInfo.setText("Ungültige Auswahl");
                    }
                }
            });

            if (AppState.get().getCurrentDirectory() != null) {
                chooser.setCurrentDirectory(AppState.get().getCurrentDirectory().getParent().toFile());
            }


            JDialog dialog = new JDialog((Frame) null, "Verzeichnis wählen", true);
            dialog.getContentPane().add(chooser);
            dialog.setSize(800, 600);
            dialog.setLocationRelativeTo(null);

            // OK und Abbrechen Buttons selbst machen
            chooser.addActionListener(e -> {
                if (JFileChooser.APPROVE_SELECTION.equals(e.getActionCommand())) {
                    Path directory = chooser.getSelectedFile().toPath();
                    AppState.get().setCurrentDirectory(directory);
                    Controller.getInstance().getExecutorService().submit(() ->
                            EventBus.get().publish(new CurrentDirectoryChangedEvent())
                    );
                    dialog.dispose();
                } else if (JFileChooser.CANCEL_SELECTION.equals(e.getActionCommand())) {
                    dialog.dispose();
                }
            });

            dialog.setVisible(true);
            dialog.setModal(false);
            dialog.toFront();
        });

        add(H.makeHorizontalPanel(btnFileChooser));
    }

    private void addSlideshowControls() {
        txtDuration = new JTextField(3);
        txtDuration.setToolTipText("Anzeigedauer pro Bild (Sekunden)");
        JButton btnStart = new JButton("Start");
        JButton btnStop = new JButton("Stop");
        JCheckBox cbxMoveImage = new JCheckBox("Move..");

        btnStart.addActionListener(e -> {
            try {
                int duration = Integer.parseInt(txtDuration.getText());
                slideshowManager.start(controller.getCurrentlyDisplayedFiles(), duration, cbxMoveImage.isSelected());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Bitte eine gültige Zahl für die Bilddauer eingeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnStop.addActionListener(e -> slideshowManager.stop());

        add(H.makeHorizontalPanel(btnStart, btnStop, new JLabel("Dauer"), txtDuration, cbxMoveImage));
    }

    private void addPlaybackControls() {
        btnPlayPause = new JToggleButton("Play/Pause");
        btnPlayPause.addActionListener(a -> {
            EventBus.get().publish(new MediaviewPlayEvent(btnPlayPause.isSelected()));
        });

        JButton btnStop = new JButton("Stop");
        btnStop.addActionListener(a -> {
            EventBus.get().publish(new MediaViewStopEvent());
        });

        JToggleButton btnFullscreen = new JToggleButton("Vollbild");
        btnFullscreen.setSelected(true);
        AppState.get().setMediaviewFullscreen(true);
        btnFullscreen.addActionListener(a -> EventBus.get().publish(new MediaViewFullscreenEvent(btnFullscreen.isSelected())));
        btnFullscreen.addActionListener(a-> AppState.get().setMediaviewFullscreen(btnFullscreen.isSelected()));

        add(H.makeHorizontalPanel(btnPlayPause, btnStop));
        add(H.makeHorizontalPanel(cbxAutostart, btnFullscreen));
    }

    private void addRangeControls() {
        JButton btnSaveRange = new JButton("übernehmen");
        add(H.makeHorizontalPanel(new JLabel("von"), txtTimerangeStart, new JLabel("bis"), txtTimerangeEnde));
        add(H.makeHorizontalPanel(btnSaveRange, cbxIgnoreTimerange, btnCreateAutoTimerane));
        btnSaveRange.addActionListener(a -> {
            try {
                double start = Double.parseDouble(txtTimerangeStart.getText());
                double end = Double.parseDouble(txtTimerangeEnde.getText());
                if (AppState.get().getCurrentFile() != null) {
                    rangeHandler.setRangeForFile(start, end, AppState.get().getCurrentFile());
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Bitte gültige Zahlen für Start und Ende eingeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnCreateAutoTimerane.addActionListener(a -> {
            List<File> files = Controller.getInstance().getCurrentlyDisplayedFiles();

            for (File file : files) {
                if (RangeHandler.getInstance().getRangeForFile(file) == null) {

                    double duration = RangeHandler.getInstance().getDuration(file);
                    int start = (int) Math.round(duration * 0.2);
                    int end = (int) Math.round(duration * 0.8);

                    H.out("setting range for file ... " + file.getName() + " duration: " + duration);
                    RangeHandler.getInstance().setRangeForFile(start, end, file);
                }
            }

            updateUntaggedFilesCount();
        });


        cbxIgnoreTimerange.addActionListener(l -> {
            AppState.get().setIgnoreTimerange(cbxIgnoreTimerange.isSelected());
        });

    }

    private void addSliderPositionControl() {
        sldMoviePosition.addChangeListener(c -> {
            if (sldMoviePosition.getValueIsAdjusting()) {
                isUserDraggingSlider = true;
            }
        });

        sldMoviePosition.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                isUserDraggingSlider = true;
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                isUserDraggingSlider = false;
                lastUserSliderChange = System.currentTimeMillis();

                // Jetzt explizit als vom Benutzer gesetzt markieren
                isUpdatingFromCode = true;
                EventBus.get().publish(new CurrentPlaybackSliderPosEvent(
                        (float) sldMoviePosition.getValue() / (float) sldMoviePosition.getMaximum()));
                isUpdatingFromCode = false;
            }
        });

        add(H.makeHorizontalPanel(new JLabel("Pos"), sldMoviePosition, lblPosition));
    }
    private void addVolumeControl() {
        lblVol = new JLabel("---");
        sldVolume = new JSlider();
        sldVolume.addChangeListener(l -> {
            VolumeHandler.getInstance().setVolumeForCurrentFile(sldVolume.getValue());
            lblVol.setText("" + sldVolume.getValue());
        });
        add(H.makeHorizontalPanel(new JLabel("Lautstärke"), sldVolume, lblVol));
    }

    private void addParalaxeControl() {
        lblParalaxe = new JLabel("---");

        sldParalaxe = new JSlider(-100, 100, 0);
        sldParalaxe.setMajorTickSpacing(10);   // große Ticks alle 10 (entspricht 1%)

        sldParalaxe.addChangeListener(l -> {
            double parallaxX = sldParalaxe.getValue() / 1000.0;
            H.out("pa + " + parallaxX);
            ParallaxHandler.getInstance().setParallaxForCurrentFile(parallaxX);
            lblParalaxe.setText(String.format("%.2f%%", parallaxX * 100));
        });

        add(H.makeHorizontalPanel(new JLabel("PLX"), sldParalaxe, lblParalaxe));
    }

    private void addTagControls() {
        JButton btnShowUntagged = new JButton("Untagged anzeigen");

        btnShowUntagged.addActionListener(a -> {
            List<File> files = TagHandler.getInstance().getUntaggedFiles();
            Controller.getInstance().setSelectedFiles(files.stream().map(File::getAbsolutePath).toList());
        });

        txtUntaggedCount = new JLabel("(0)");
        JButton btnSetTags = new JButton("Tags setzen");
        btnSetTags.addActionListener(a -> {
            if (AppState.get().getCurrentFile() != null) {

                Window parent = SwingUtilities.getWindowAncestor(Controller.getInstance().getThumbnailPanel());

                if (tagEditDialog == null) {
                    tagEditDialog = new TagEditDialog(parent);

                    GraphicsDevice[] screens = GraphicsEnvironment
                            .getLocalGraphicsEnvironment()
                            .getScreenDevices();

                    GraphicsDevice rightScreen = screens[screens.length - 1];
                    Rectangle bounds = rightScreen.getDefaultConfiguration().getBounds();


                    int dialogWidth = 280;
                    int dialogHeight = 400;
                    tagEditDialog.setSize(dialogWidth, dialogHeight);
                    tagEditDialog.setLocation(
                            bounds.x + 20,
                            bounds.y + bounds.height - dialogHeight - 50
                    );
                }

                tagEditDialog.setFile(AppState.get().getCurrentFile(), true);
            }
        });
        JCheckBox cbxAutoOpenTagsDialog = new JCheckBox("automatisch öffnen");
        cbxAutoOpenTagsDialog.addActionListener(l -> {
            AppState.get().setAutoOpenTagsDialog(cbxAutoOpenTagsDialog.isSelected());
        });

        add(H.makeHorizontalPanel(btnShowUntagged, txtUntaggedCount));
        add(H.makeHorizontalPanel(btnSetTags, cbxAutoOpenTagsDialog));
        tagSelectionPanel = new TagSelectionPanel();
        add(tagSelectionPanel);
    }

    public void setCurrentPlayPosMillis(long millis, long total) {



//        H.out("cppp " + millis);
        if (total <= 0) return;
        int pos = (int) (sldMoviePosition.getMaximum() * millis / total);
        long sec = millis / 1000;
        long min = sec / 60;
        sec %= 60;
        String time = String.format("%02d:%02d (%d)", min, sec, millis / 1000);

        SwingUtilities.invokeLater(() -> {
            long now = System.currentTimeMillis();
            if (!isUserDraggingSlider && now - lastUserSliderChange > 1000 && !isUpdatingFromCode) {
                isUpdatingFromCode = true;
                sldMoviePosition.setValue(pos);
                isUpdatingFromCode = false;
            }
            lblPosition.setText(time);
        });
    }

    public void setThumbnailsLoaded(int loaded, int total) {
        SwingUtilities.invokeLater(() -> lblThumbnailsLoadedCount.setText("Thumbnails geladen: " + loaded + " / " + total));
    }

    public void setSelectedFile(File file) {
        AppState.get().setCurrentFile(file);
        if (tagEditDialog != null) {
            tagEditDialog.setFile(AppState.get().getCurrentFile(), false);
        }
        RangeHandler.Range range = rangeHandler.getRangeForFile(file);
        SwingUtilities.invokeLater(() -> {
            if (range != null) {
                txtTimerangeStart.setText(String.valueOf(range.start));
                txtTimerangeEnde.setText(String.valueOf(range.end));
            } else {
                txtTimerangeStart.setText(null);
                txtTimerangeEnde.setText(null);
            }
        });
        int vol = VolumeHandler.getInstance().getVolumeForFile(file);
        SwingUtilities.invokeLater(() -> {
            lblVol.setText("" + vol);
            sldVolume.setValue(vol);
        });
        double parallax = ParallaxHandler.getInstance().getParallaxForFile(file);
        SwingUtilities.invokeLater(() -> {
            lblParalaxe.setText(String.format("%.2f%%", parallax * 100));
            sldParalaxe.setValue((int) (parallax * 1000));
        });
    }

    public boolean isAutostart() {
        return cbxAutostart.isSelected();
    }

    public void resetPlayPauseButton() {
        btnPlayPause.setSelected(false);
    }

    public SlideshowManager getSlideshowManager() {
        return slideshowManager;
    }

    private void updateUntaggedFilesCount() {
        List<File> untagged = TagHandler.getInstance().getUntaggedFiles();
        txtUntaggedCount.setText("(" + untagged.size() + ")");
    }



}