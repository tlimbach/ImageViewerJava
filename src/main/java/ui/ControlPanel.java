package ui;

import event.*;
import model.AppState;
import service.*;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class ControlPanel extends JPanel {

    private final Controller controller = Controller.getInstance();
    private final JLabel lblPosition = new JLabel("----");
    private final JLabel lblThumbnailsLoadedCount = new JLabel("---------");
    private final JTextField txtTimerangeStart = new JTextField(5);
    private final JTextField txtTimerangeEnde = new JTextField(5);
    private final JCheckBox cbxIgnoreTimerange = new JCheckBox("Z. ignorieren");
    private final JCheckBox cbxAutostart = new JCheckBox("Autostart");

    private final JSlider sldMoviePosition = new JSlider();
    private JTextField txtDuration;
    private JToggleButton btnPlayPause;
    private boolean isUpdatingFromCode = false;
    private final SlideshowManager slideshowManager = new SlideshowManager();

    private JLabel txtUntaggedCount;
    private File currentFile;

    private JSlider sldVolume;
    private JLabel lblVol;

     private  RangeHandler rangeHandler = new RangeHandler();
    private TagSelectionPanel tagSelectionPanel;
    private TagEditDialog tagEditDialog;

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

        add(H.makeHorizontalPanel(lblThumbnailsLoadedCount));

        EventBus.get().register(CurrentPlaybackPosEvent.class, e->{
            setCurrentPlayPosMillis(e.currentMillis(), e.totalMinis());
        });

        EventBus.get().register(SlideshowCurrentFile.class, e->{
            setSelectedFile(e.file());
        });
    }

    private void addFileChooserButton() {
        JButton btnFileChooser = new JButton("Verzeichnis wählen");
        btnFileChooser.addActionListener(a -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                MediaService.getInstance().setDirectory(chooser.getSelectedFile().toPath());
            }
        });
        add(H.makeHorizontalPanel(btnFileChooser));
    }

    private void addSlideshowControls() {
        txtDuration = new JTextField(3);
        txtDuration.setToolTipText("Anzeigedauer pro Bild (Sekunden)");
        JButton btnStart = new JButton("Start");
        JButton btnStop = new JButton("Stop");

        btnStart.addActionListener(e -> {
            try {
                int duration = Integer.parseInt(txtDuration.getText());
                slideshowManager.start(controller.getCurrentlyDisplayedFiles(), duration);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Bitte eine gültige Zahl für die Bilddauer eingeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnStop.addActionListener(e -> slideshowManager.stop());

        add(H.makeHorizontalPanel(btnStart, btnStop, new JLabel("Dauer"), txtDuration));
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
        btnFullscreen.addActionListener(a -> EventBus.get().publish(new MediaViewFullscreenEvent(btnFullscreen.isSelected())));

        add(H.makeHorizontalPanel(btnPlayPause, btnStop));
        add(H.makeHorizontalPanel( cbxAutostart, btnFullscreen));
    }

    private void addRangeControls() {
        JButton btnSaveRange = new JButton("übernehmen");
        add(H.makeHorizontalPanel(new JLabel("von"), txtTimerangeStart, new JLabel("bis"), txtTimerangeEnde));
        add(H.makeHorizontalPanel(btnSaveRange, cbxIgnoreTimerange));
        btnSaveRange.addActionListener(a -> {
            try {
                double start = Double.parseDouble(txtTimerangeStart.getText());
                double end = Double.parseDouble(txtTimerangeEnde.getText());
                if (currentFile != null) {
                    rangeHandler.setRangeForFile(start, end, currentFile);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Bitte gültige Zahlen für Start und Ende eingeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        });

        cbxIgnoreTimerange.addActionListener(l->{
            AppState.get().setIgnoreTimerange(cbxIgnoreTimerange.isSelected());
        });

    }

    private void addSliderPositionControl() {
        sldMoviePosition.addChangeListener(c -> {
            if (!isUpdatingFromCode && !sldMoviePosition.getValueIsAdjusting()) {
                EventBus.get().publish(new CurrentPlaybackSliderPosEvent((float) sldMoviePosition.getValue() / (float)sldMoviePosition.getMaximum()));

            }
        });
        add(H.makeHorizontalPanel(new JLabel("Pos"), sldMoviePosition, lblPosition));
    }

    private void addVolumeControl() {
        lblVol = new JLabel("---");
        sldVolume = new JSlider();
        sldVolume.addChangeListener(l-> {
            VolumeHandler.getInstance().setVolumeForCurrentFile(sldVolume.getValue());
            lblVol.setText(""+sldVolume.getValue());
        });
        add(H.makeHorizontalPanel(new JLabel("Lautstärke"), sldVolume, lblVol));
    }

    private void addTagControls() {
        JButton btnShowUntagged = new JButton("Untagged anzeigen");

        btnShowUntagged.addActionListener(a->{
            List<File> files = TagHandler.getInstance().getUntaggedFiles();
            Controller.getInstance().setSelectedFiles(files.stream().map(File::getAbsolutePath).toList());
        });

        txtUntaggedCount = new JLabel("(0)");
        JButton btnSetTags = new JButton("Tags setzen");
        btnSetTags.addActionListener(a -> {

            if (currentFile != null) {
                tagEditDialog = new TagEditDialog();
                tagEditDialog.setFile(currentFile);
            }
        });
        JCheckBox cbxAutoOpenTagsDialog = new JCheckBox("automatisch öffnen");

        add(H.makeHorizontalPanel(btnShowUntagged, txtUntaggedCount));
        add(H.makeHorizontalPanel(btnSetTags, cbxAutoOpenTagsDialog));
        tagSelectionPanel = new TagSelectionPanel();
        add(tagSelectionPanel);
    }

    public void setCurrentPlayPosMillis(long millis, long total) {
        if (total <= 0) return;
        int pos = (int) (sldMoviePosition.getMaximum() * millis / total);
        long sec = millis / 1000;
        long min = sec / 60;
        sec %= 60;
        String time = String.format("%02d:%02d (%d)", min, sec, millis / 1000);

        SwingUtilities.invokeLater(() -> {
            isUpdatingFromCode = true;
            sldMoviePosition.setValue(pos);
            isUpdatingFromCode = false;
            lblPosition.setText(time);
        });
    }

    public void setThumbnailsLoaded(int loaded, int total) {
        SwingUtilities.invokeLater(() -> lblThumbnailsLoadedCount.setText("Thumbnails geladen: " + loaded + " / " + total));
    }

    public void setSelectedFile(File file) {
        currentFile = file;
        if (tagEditDialog != null) {
            tagEditDialog.setFile(currentFile);
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
        int vol = VolumeHandler.getInstance().getVolumeForFile(file.getAbsolutePath());
        SwingUtilities.invokeLater(()->{
            lblVol.setText(""+vol);
            sldVolume.setValue(vol);
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

    public void setUntaggedCount(int size) {
        txtUntaggedCount.setText("("+size+")");
    }

    public void reloadTagSelectinPanel() {
        tagSelectionPanel.revalidateTags();
    }
}