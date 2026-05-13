package ui;

import event.*;
import model.AppState;
import service.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class ControlPanel extends JPanel {

    private final Controller controller = Controller.getInstance();
    private final JLabel lblPosition = new JLabel("----");
    private final JLabel lblThumbnailsLoadedCount = new JLabel("---------");
    private final JTextField txtTimerangeStart = new JTextField(5);
    private final JTextField txtTimerangeEnde = new JTextField(5);
    private final JTextField txtMediaLimit = new JTextField(5);
    private final JCheckBox cbxIgnoreTimerange = new JCheckBox("Z. ignorieren");
    private final JComboBox<ThumbnailZoomMode> cmbThumbnailZoomMode = new JComboBox<>(ThumbnailZoomMode.values());


    private final JCheckBox cbxAutostart = new JCheckBox("Autostart");

    private final JSlider sldMoviePosition = new JSlider();
    private JTextField txtDuration;
    private JTextField txtSlideshowTotalMinutes;
    private JToggleButton btnPlayPause;
    private boolean isUpdatingFromCode = false;
    private final SlideshowManager slideshowManager = new SlideshowManager();

    private JLabel txtUntaggedCount;
    private JLabel txtUnzoomedCount;

    private JSlider sldVolume;
    private JLabel lblVol;

    private JLabel lblParalaxe;

    private JSlider sldParalaxe;

    private RangeHandler rangeHandler = RangeHandler.getInstance();
    private TagSelectionPanel tagSelectionPanel;
    private TagEditDialog tagEditDialog;
    private Rectangle tagEditDialogBounds;
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
            updateUnzoomedFilesCount();
            tagSelectionPanel.reloadTags();
        });

        EventBus.get().register(TagsChangedEvent.class, e->{
            updateUntaggedFilesCount();
            tagSelectionPanel.reloadTags();
        });

        EventBus.get().register(ImageZoomChangedEvent.class, e -> updateUnzoomedFilesCount());

        EventBus.get().register(UserKeyboardEvent.class, e -> {
            // Hier: PAGE_UP und PAGE_DOWN (und weitere) verarbeiten
            if (e.command() == UserCommand.PAGE_UP) {
                int value = sldParalaxe.getValue();
                sldParalaxe.setValue(Math.min(value + 1, sldParalaxe.getMaximum()));
            } else if (e.command() == UserCommand.PAGE_DOWN) {
                int value = sldParalaxe.getValue();
                sldParalaxe.setValue(Math.max(value - 1, sldParalaxe.getMinimum()));
            } else if (e.command() == UserCommand.HOME) {
                int value = sldParalaxe.getValue();
                sldParalaxe.setValue(Math.min(value + 5, sldParalaxe.getMaximum()));
            } else if (e.command() == UserCommand.END) {
                int value = sldParalaxe.getValue();
                sldParalaxe.setValue(Math.max(value - 5, sldParalaxe.getMinimum()));
            } else if (e.command() == UserCommand.INSERT) {
                int value = sldParalaxe.getValue();
                sldParalaxe.setValue(Math.min(value + 20, sldParalaxe.getMaximum()));
            } else if (e.command() == UserCommand.DELETE) {
                int value = sldParalaxe.getValue();
                sldParalaxe.setValue(Math.max(value - 20, sldParalaxe.getMinimum()));
            }
        });

        updateUntaggedFilesCount();
        updateUnzoomedFilesCount();

    }



    private void addFileChooserButton() {
        JButton btnFileChooser = new JButton("Verzeichnis wählen");
        JButton btnOpenFinder = new JButton("Finder");
        JLabel lblInfo = new JLabel("Noch nichts gewählt");

        btnFileChooser.addActionListener(a -> {
            MediaView.getInstance().stopAndHide();

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
                        int imageCount = files == null ? 0 : (int) Arrays.stream(files).filter(Controller::isImageFile).count();
                        int videoCount = files == null ? 0 : (int) Arrays.stream(files).filter(Controller::isVideoFile).count();
                        lblInfo.setText("<html><br>Bilder: " + imageCount + "<br>Videos: " + videoCount + "</html>");
                    } else {
                        lblInfo.setText("Ungültige Auswahl");
                    }
                }
            });

            if (AppState.get().getCurrentDirectory() != null) {
                chooser.setCurrentDirectory(AppState.get().getCurrentDirectory().toFile());
            }

            int result = chooser.showOpenDialog(this);
            if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                return;
            }

            Path directory = chooser.getSelectedFile().toPath();
            AppState.get().setCurrentDirectory(directory);
            EventBus.get().publish(new CurrentDirectoryChangedEvent());
        });

        btnOpenFinder.addActionListener(a -> {
            Path currentDirectory = AppState.get().getCurrentDirectory();
            if (currentDirectory == null) {
                JOptionPane.showMessageDialog(this, "Bitte zuerst ein Verzeichnis wählen.", "Finder", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            try {
                Desktop.getDesktop().open(currentDirectory.toFile());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Finder konnte nicht geöffnet werden.", "Finder", JOptionPane.ERROR_MESSAGE);
            }
        });

        Integer mediaLoadLimit = AppState.get().getMediaLoadLimit();
        txtMediaLimit.setText(mediaLoadLimit == null ? "" : mediaLoadLimit.toString());
        txtMediaLimit.setToolTipText("Maximale Anzahl geladener Medien. Leer = alle.");
        txtMediaLimit.addActionListener(a -> applyMediaLimitFromField());
        txtMediaLimit.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                applyMediaLimitFromField();
            }
        });

        add(H.makeHorizontalPanel(btnFileChooser, btnOpenFinder));
        add(H.makeHorizontalPanel(new JLabel("Max Medien"), txtMediaLimit));
        cmbThumbnailZoomMode.addActionListener(a -> Controller.getInstance().getExecutorService().submit(
                () -> Controller.getInstance().getThumbnailPanel().reloadDirectory()
        ));
        add(H.makeHorizontalPanel(new JLabel("Thumbs"), cmbThumbnailZoomMode));
    }

    private void applyMediaLimitFromField() {
        String value = txtMediaLimit.getText().trim();
        Integer newLimit = null;
        if (!value.isEmpty()) {
            try {
                newLimit = Integer.parseInt(value);
                if (newLimit <= 0) {
                    throw new NumberFormatException("Limit must be positive");
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Bitte eine positive Zahl eingeben oder leer lassen.", "Max Medien", JOptionPane.ERROR_MESSAGE);
                SwingUtilities.invokeLater(txtMediaLimit::requestFocusInWindow);
                return;
            }
        }

        Integer oldLimit = AppState.get().getMediaLoadLimit();
        if (oldLimit == null ? newLimit == null : oldLimit.equals(newLimit)) {
            return;
        }

        AppState.get().setMediaLoadLimit(newLimit);
        Controller.getInstance().getExecutorService().submit(() -> Controller.getInstance().getThumbnailPanel().reloadDirectory());
    }

    private void addSlideshowControls() {
        txtDuration = new JTextField(3);
        txtDuration.setToolTipText("Anzeigedauer pro Bild (Sekunden)");
        txtSlideshowTotalMinutes = new JTextField(3);
        txtSlideshowTotalMinutes.setToolTipText("Gesamtdauer der Diashow (Minuten)");
        txtSlideshowTotalMinutes.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateDurationFromTotalMinutes();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateDurationFromTotalMinutes();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateDurationFromTotalMinutes();
            }
        });
        JButton btnStart = new JButton("Start");
        JButton btnStop = new JButton("Stop");
        JCheckBox cbxMoveImage = new JCheckBox("Move..");

        btnStart.addActionListener(e -> {
            try {
                int duration = Integer.parseInt(txtDuration.getText());
                int totalMinutes = Integer.parseInt(txtSlideshowTotalMinutes.getText());
                if (duration <= 0 || totalMinutes <= 0) {
                    throw new NumberFormatException();
                }
                slideshowManager.start(controller.getCurrentlyDisplayedFiles(), duration, totalMinutes, cbxMoveImage.isSelected());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Bitte gültige Zahlen für Bilddauer und Gesamtdauer eingeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnStop.addActionListener(e -> slideshowManager.stop());

        add(H.makeHorizontalPanel(btnStart, btnStop, cbxMoveImage));
        add(H.makeHorizontalPanel(new JLabel("Dauer"), txtDuration, new JLabel("Gesamt"), txtSlideshowTotalMinutes));
    }

    private void updateDurationFromTotalMinutes() {
        Integer totalMinutes = parsePositiveInteger(txtSlideshowTotalMinutes.getText());
        Integer mediaCount = getDurationCalculationMediaCount();
        if (totalMinutes == null || mediaCount == null) {
            return;
        }

        long totalSeconds = totalMinutes * 60L;
        int durationSeconds = Math.max(1, (int) Math.round(totalSeconds / (double) mediaCount));
        txtDuration.setText(Integer.toString(durationSeconds));
    }

    private Integer getDurationCalculationMediaCount() {
        Integer mediaLimit = parsePositiveInteger(txtMediaLimit.getText());
        if (mediaLimit != null) {
            return mediaLimit;
        }

        int currentlyDisplayedFiles = controller.getCurrentlyDisplayedFiles().size();
        return currentlyDisplayedFiles > 0 ? currentlyDisplayedFiles : null;
    }

    private Integer parsePositiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
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
        JButton btnCreateAutoTimerane = new JButton("Auto");
        btnCreateAutoTimerane.setToolTipText("Zeitbereich automatisch anlegen");
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
                    int end = (int) Math.round(duration);

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
                    tagEditDialog = createTagEditDialog(parent);
                }

                tagEditDialog.setFile(AppState.get().getCurrentFile(), true);
            }
        });
        JCheckBox cbxAutoOpenTagsDialog = new JCheckBox("automatisch öffnen");
        cbxAutoOpenTagsDialog.addActionListener(l -> {
            AppState.get().setAutoOpenTagsDialog(cbxAutoOpenTagsDialog.isSelected());
        });

        add(H.makeHorizontalPanel(btnShowUntagged, txtUntaggedCount));
        JButton btnShowUnzoomed = new JButton("Unzoomed anzeigen");
        btnShowUnzoomed.addActionListener(a -> {
            List<File> files = ImageZoomHandler.getInstance().getUnzoomedImageFiles();
            Controller.getInstance().setSelectedFiles(files.stream().map(File::getAbsolutePath).toList());
        });
        txtUnzoomedCount = new JLabel("(0)");
        add(H.makeHorizontalPanel(btnShowUnzoomed, txtUnzoomedCount));
        add(H.makeHorizontalPanel(btnSetTags, cbxAutoOpenTagsDialog));
        tagSelectionPanel = new TagSelectionPanel();
        add(tagSelectionPanel);
    }

    private TagEditDialog createTagEditDialog(Window parent) {
        TagEditDialog dialog = new TagEditDialog(parent);

        if (tagEditDialogBounds != null) {
            dialog.setBounds(tagEditDialogBounds);
        } else {
            GraphicsDevice[] screens = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getScreenDevices();

            GraphicsDevice rightScreen = screens[screens.length - 1];
            Rectangle bounds = rightScreen.getDefaultConfiguration().getBounds();

            int dialogWidth = 280;
            int dialogHeight = 400;
            dialog.setSize(dialogWidth, dialogHeight);
            dialog.setLocation(
                    bounds.x + 20,
                    bounds.y + bounds.height - dialogHeight - 50
            );
        }

        dialog.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                tagEditDialogBounds = dialog.getBounds();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                tagEditDialogBounds = dialog.getBounds();
            }
        });

        return dialog;
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

    public ThumbnailZoomMode getThumbnailZoomMode() {
        return (ThumbnailZoomMode) cmbThumbnailZoomMode.getSelectedItem();
    }

    private void updateUntaggedFilesCount() {
        List<File> untagged = TagHandler.getInstance().getUntaggedFiles();
        txtUntaggedCount.setText("(" + untagged.size() + ")");
    }

    private void updateUnzoomedFilesCount() {
        if (txtUnzoomedCount == null) return;
        List<File> unzoomed = ImageZoomHandler.getInstance().getUnzoomedImageFiles();
        txtUnzoomedCount.setText("(" + unzoomed.size() + ")");
    }



}
