package ui;

import event.*;
import model.AppState;
import service.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ControlPanel extends JPanel {
    private static final int DEFAULT_SLIDESHOW_MEDIA_LIMIT = 20;
    private static final int DEFAULT_SLIDESHOW_DURATION_SECONDS = 30;
    private static final int DEFAULT_SLIDESHOW_TOTAL_MINUTES = 10;

    private final Controller controller = Controller.getInstance();
    private final JLabel lblPosition = new JLabel("----");
    private final JTextField txtTimerangeStart = new JTextField(5);
    private final JTextField txtTimerangeEnde = new JTextField(5);
    private final JTextField txtMediaLimit = new JTextField(5);
    private final JCheckBox cbxIgnoreTimerange = new JCheckBox("Z. ignorieren");
    private final JComboBox<ThumbnailZoomMode> cmbThumbnailZoomMode = new JComboBox<>(ThumbnailZoomMode.values());


    private final JCheckBox cbxAutostart = new JCheckBox("Autostart", true);

    private final JSlider sldMoviePosition = new JSlider();
    private JTextField txtDuration;
    private JTextField txtSlideshowTotalMinutes;
    private JToggleButton btnPlayPause;
    private boolean isUpdatingFromCode = false;
    private final SlideshowManager slideshowManager = new SlideshowManager();

    private final JCheckBox cbxShowUntaggedOnly = new JCheckBox();
    private final JLabel lblTagFilterStatus = new JLabel();
    private int tagFilterCount = -1;

    private JSlider sldVolume;
    private JLabel lblVol;

    private JLabel lblParalaxe;

    private JSlider sldParalaxe;

    private RangeHandler rangeHandler = RangeHandler.getInstance();
    private TagSelectionPanel tagSelectionPanel;
    private TagEditDialog tagEditDialog;
    private BookmarkDialog bookmarkDialog;
    private TagFilterDialog tagFilterDialog;
    private Rectangle tagEditDialogBounds;
    private long lastSliderEventTime;

    private boolean isUserDraggingSlider = false;
    private long lastUserSliderChange = 0;

    public ControlPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        cmbThumbnailZoomMode.setSelectedItem(ThumbnailZoomMode.GRAYED_OUT);

        addFileChooserButton();
        addSlideshowControls();
        addPlaybackControls();
        addRangeControls();
        addSliderPositionControl();
        addVolumeControl();
        addTagControls();
//        addBrillenSetup();
        addParalaxeControl();

        EventBus.get().register(CurrentPlaybackPosEvent.class, e -> {
            setCurrentPlayPosMillis(e.currentMillis(), e.totalMinis());
        });

        EventBus.get().register(CurrentlySelectedFileEvent.class, e -> {
            setSelectedFile(e.file());
        });

        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> {
            cbxShowUntaggedOnly.setSelected(false);
            tagFilterCount = -1;
            updateUntaggedFilterCheckbox();
            updateTagFilterStatus();
            tagSelectionPanel.reloadTags();
        });

        EventBus.get().register(TagsChangedEvent.class, e->{
            updateUntaggedFilterCheckbox();
            if (cbxShowUntaggedOnly.isSelected()) {
                keepOnlyUntaggedFilesInCurrentView();
            }
            tagSelectionPanel.reloadTags();
            updateTagFilterStatus();
            if (tagEditDialog != null) {
                tagEditDialog.refreshCurrentFile();
            }
        });

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

        updateUntaggedFilterCheckbox();

    }



    private void addFileChooserButton() {
        JButton btnFileChooser = new JButton("Verzeichnis wählen");
        JButton btnOpenFinder = new JButton("Finder");

        btnFileChooser.addActionListener(a -> {
            MediaView.getInstance().stopAndHide();
            showDirectorySelectionDialog();
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

        Integer slideshowMediaLimit = AppState.get().getSlideshowMediaLimit();
        if (slideshowMediaLimit == null) {
            slideshowMediaLimit = DEFAULT_SLIDESHOW_MEDIA_LIMIT;
            AppState.get().setSlideshowMediaLimit(slideshowMediaLimit);
        }
        txtMediaLimit.setText(slideshowMediaLimit == null ? "" : slideshowMediaLimit.toString());
        txtMediaLimit.setToolTipText("Maximale Anzahl Medien für die Slideshow um das aktuelle Thumbnail herum. Leer = alle.");
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

    private void showDirectorySelectionDialog() {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Verzeichnis wählen", Dialog.ModalityType.APPLICATION_MODAL);
        DefaultListModel<Path> model = new DefaultListModel<>();
        SettingsService.getIntance().loadDirectoryHistory().forEach(model::addElement);

        JList<Path> historyList = new JList<>(model);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setVisibleRowCount(10);
        historyList.setFixedCellHeight(34);
        historyList.setSelectionBackground(new Color(25, 105, 210));
        historyList.setSelectionForeground(Color.WHITE);
        historyList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.toString());
            label.setOpaque(true);
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)
            ));
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(index % 2 == 0 ? Color.WHITE : new Color(245, 245, 245));
                label.setForeground(list.getForeground());
            }
            return label;
        });
        historyList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    Path selected = historyList.getSelectedValue();
                    if (selected != null && loadDirectoryFromHistory(selected, dialog)) {
                        dialog.dispose();
                    }
                }
            }
        });

        JButton btnOpenSelected = new JButton("Öffnen");
        btnOpenSelected.addActionListener(e -> {
            Path selected = historyList.getSelectedValue();
            if (selected != null && loadDirectoryFromHistory(selected, dialog)) {
                dialog.dispose();
            }
        });

        JButton btnChooseOther = new JButton("Anderes Verzeichnis wählen...");
        btnChooseOther.addActionListener(e -> {
            Path selected = chooseDirectoryWithFileChooser(dialog);
            if (selected != null) {
                loadDirectory(selected);
                dialog.dispose();
            }
        });

        JButton btnRemove = new JButton("Aus Liste entfernen");
        btnRemove.addActionListener(e -> {
            Path selected = historyList.getSelectedValue();
            if (selected == null) return;
            SettingsService.getIntance().removeDirectoryFromHistory(selected);
            model.removeElement(selected);
        });

        JButton btnClose = new JButton("Schließen");
        btnClose.addActionListener(e -> dialog.dispose());

        Component buttonPanel = H.makeHorizontalPanel(btnOpenSelected, btnChooseOther, btnRemove, btnClose);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(new JScrollPane(historyList), BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setPreferredSize(new Dimension(760, 360));
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private boolean loadDirectoryFromHistory(Path directory, Component parent) {
        if (!Files.isDirectory(directory)) {
            JOptionPane.showMessageDialog(parent, "Das Verzeichnis existiert nicht mehr.", "Verzeichnis wählen", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        loadDirectory(directory);
        return true;
    }

    private Path chooseDirectoryWithFileChooser(Component parent) {
        JLabel lblInfo = new JLabel("Noch nichts gewählt");
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

        int result = chooser.showOpenDialog(parent);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }

    private void loadDirectory(Path directory) {
        AppState.get().setCurrentDirectory(directory);
        EventBus.get().publish(new CurrentDirectoryChangedEvent());
    }

    private boolean applyMediaLimitFromField() {
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
                return false;
            }
        }

        Integer oldLimit = AppState.get().getSlideshowMediaLimit();
        if (oldLimit == null ? newLimit == null : oldLimit.equals(newLimit)) {
            return true;
        }

        AppState.get().setSlideshowMediaLimit(newLimit);
        return true;
    }

    private void addSlideshowControls() {
        txtDuration = new JTextField(3);
        txtDuration.setText(Integer.toString(DEFAULT_SLIDESHOW_DURATION_SECONDS));
        txtDuration.setToolTipText("Anzeigedauer pro Bild (Sekunden)");
        txtSlideshowTotalMinutes = new JTextField(3);
        txtSlideshowTotalMinutes.setText(Integer.toString(DEFAULT_SLIDESHOW_TOTAL_MINUTES));
        txtSlideshowTotalMinutes.setToolTipText("Gesamtdauer der Diashow (Minuten)");
        JButton btnStart = new JButton("Start");
        JButton btnStop = new JButton("Stop");

        btnStart.addActionListener(e -> {
            try {
                int duration = Integer.parseInt(txtDuration.getText());
                int totalMinutes = Integer.parseInt(txtSlideshowTotalMinutes.getText());
                if (duration <= 0 || totalMinutes <= 0) {
                    throw new NumberFormatException();
                }
                if (!applyMediaLimitFromField()) {
                    return;
                }
                Integer mediaLimit = AppState.get().getSlideshowMediaLimit();
                slideshowManager.start(controller.getSlideshowFilesAroundCurrentSelection(mediaLimit), duration, totalMinutes);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Bitte gültige Zahlen für Bilddauer und Gesamtdauer eingeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnStop.addActionListener(e -> slideshowManager.stop());

        add(H.makeHorizontalPanel(btnStart, btnStop));
        add(H.makeHorizontalPanel(new JLabel("Dauer"), txtDuration, new JLabel("Gesamt"), txtSlideshowTotalMinutes));
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

            updateUntaggedFilterCheckbox();
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
        cbxShowUntaggedOnly.setName("nur untagged anzeigen");
        cbxShowUntaggedOnly.addActionListener(a -> applyUntaggedFilterCheckbox());

        JButton btnFilterTags = new JButton("Tags filtern");
        btnFilterTags.addActionListener(a -> openTagFilterDialog());
        JButton btnClearTagFilter = new JButton("löschen");
        btnClearTagFilter.addActionListener(a -> clearTagFilter());

        add(H.makeHorizontalPanel(cbxShowUntaggedOnly));
        JButton btnBookmarks = new JButton("Bookmarks");
        btnBookmarks.addActionListener(a -> openBookmarkDialog());
        add(H.makeHorizontalPanel(btnBookmarks));
        updateTagFilterStatus();
        add(H.makeHorizontalPanel(btnFilterTags, btnClearTagFilter, lblTagFilterStatus));
        tagSelectionPanel = new TagSelectionPanel();
        add(tagSelectionPanel);
        updateUntaggedFilterCheckbox();
        updateTagFilterStatus();
    }

    private void openTagFilterDialog() {
        if (tagFilterDialog != null && tagFilterDialog.isDisplayable()) {
            tagFilterDialog.toFront();
            tagFilterDialog.requestFocus();
            return;
        }

        Window parent = SwingUtilities.getWindowAncestor(Controller.getInstance().getThumbnailPanel());
        tagFilterDialog = new TagFilterDialog(parent, count -> {
            tagFilterCount = count == null ? -1 : count;
            updateTagFilterStatus();
        });
        tagFilterDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                tagFilterDialog = null;
            }
        });
        tagFilterDialog.setVisible(true);
    }

    private void clearTagFilter() {
        tagFilterCount = -1;
        AppState.get().setMinimunDuration(0);
        if (tagFilterDialog != null && tagFilterDialog.isDisplayable()) {
            tagFilterDialog.clearFilterSelection();
        }
        Controller.getInstance().setSelectedFiles(null);
        updateTagFilterStatus();
    }

    private void updateTagFilterStatus() {
        if (lblTagFilterStatus == null) return;
        if (tagFilterCount < 0) {
            lblTagFilterStatus.setText("ungefiltert: " + getAllMediaFiles().size());
        } else {
            lblTagFilterStatus.setText("Filter aktiv: " + tagFilterCount);
        }
    }

    private void openBookmarkDialog() {
        if (bookmarkDialog != null && bookmarkDialog.isDisplayable()) {
            bookmarkDialog.closeDialog();
            return;
        }

        Window parent = SwingUtilities.getWindowAncestor(Controller.getInstance().getThumbnailPanel());
        slideshowManager.stop();
        MediaView.getInstance().setDisplayBlocked(true);
        bookmarkDialog = new BookmarkDialog(
                parent,
                () -> {
                    MediaView.getInstance().setDisplayBlocked(false);
                    bookmarkDialog = null;
                }
        );
        bookmarkDialog.showDialog();
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

    public void setSelectedFile(File file) {
        AppState.get().setCurrentFile(file);
        if (tagEditDialog != null) {
            tagEditDialog.setFile(AppState.get().getCurrentFile(), false);
        }
        if (tagSelectionPanel != null) {
            tagSelectionPanel.updateSelectionForCurrentFile();
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

    private void updateUntaggedFilterCheckbox() {
        if (cbxShowUntaggedOnly == null) return;
        boolean hasDirectory = AppState.get().getCurrentDirectory() != null;
        int untaggedCount = getUntaggedFiles().size();
        int allCount = getAllMediaFiles().size();

        cbxShowUntaggedOnly.setText("nur untagged anzeigen (" + untaggedCount + "/" + allCount + ")");
        cbxShowUntaggedOnly.setToolTipText(cbxShowUntaggedOnly.isSelected()
                ? untaggedCount + " ungetaggte Medien werden angezeigt"
                : allCount + " Medien werden angezeigt");
        cbxShowUntaggedOnly.setEnabled(hasDirectory);
    }

    private void applyUntaggedFilterCheckbox() {
        if (cbxShowUntaggedOnly.isSelected()) {
            showUntaggedFiles();
        } else {
            Controller.getInstance().setSelectedFiles(null);
        }
        updateUntaggedFilterCheckbox();
    }

    private void showUntaggedFiles() {
        List<File> files = getUntaggedFiles();
        Controller.getInstance().setSelectedFiles(files.stream().map(File::getAbsolutePath).toList());
    }

    private void keepOnlyUntaggedFilesInCurrentView() {
        Controller.getInstance().getThumbnailPanel().retainDisplayedFiles(getUntaggedFiles());
    }

    private List<File> getUntaggedFiles() {
        return TagHandler.getInstance().getUntaggedFiles();
    }

    private List<File> getAllMediaFiles() {
        if (AppState.get().getCurrentDirectory() == null) return List.of();
        List<File> files = MediaService.getInstance().loadFilesFromDirectory();
        return files == null ? List.of() : files;
    }

}
