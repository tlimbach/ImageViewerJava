package ui;

import service.Controller;
import service.H;
import service.RangeHandler;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;

public class ControlPanel extends JPanel {

    private final Controller controller = Controller.getInstance();
    private final JLabel lblPosition = new JLabel("----");
    private final JLabel lblThumbnailsLoadedCount = new JLabel("---------");
    private final JTextField txtTimerangeStart = new JTextField(5);
    private final JTextField txtTimerangeEnde = new JTextField(5);
    private final JCheckBox chxIgnoreTimerange = new JCheckBox("Zeitbereich ignorieren");
    private final JCheckBox cbxAutostart = new JCheckBox("Autostart");

    private final JSlider sldMoviePosition = new JSlider();
    private boolean isUpdatingFromCode = false;

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

        add(lblThumbnailsLoadedCount);
    }

    private void addFileChooserButton() {
        JButton btnFileChooser = new JButton("Verzeichnis wählen");
        btnFileChooser.addActionListener(a -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                controller.handleDirectory(chooser.getSelectedFile().toPath());
            }
        });
        add(btnFileChooser);
    }

    private void addSlideshowControls() {
        JTextField txtDuration = new JTextField(20);
        txtDuration.setToolTipText("Anzeigedauer pro Bild");
        JButton btnStart = new JButton("Start");
        JButton btnStop = new JButton("Stop");
        add(H.makeHorizontalPanel(txtDuration, btnStart, btnStop));
    }

    private void addPlaybackControls() {
        JToggleButton btnPlayPause = new JToggleButton("Play/Pause");
        btnPlayPause.addActionListener(a -> controller.playPause(btnPlayPause.isSelected()));

        JToggleButton btnFullscreen = new JToggleButton("Vollbild umschalten");
        btnFullscreen.addActionListener(a -> controller.setFullscreen(btnFullscreen.isSelected()));

        add(H.makeHorizontalPanel(btnPlayPause, cbxAutostart));
        add(btnFullscreen);
    }

    private void addRangeControls() {
        JButton btnSaveRange = new JButton("Bereich übernehmen");
        add(H.makeHorizontalPanel(txtTimerangeStart, txtTimerangeEnde, chxIgnoreTimerange));
        add(btnSaveRange);
    }

    private void addSliderPositionControl() {
        sldMoviePosition.addChangeListener(c -> {
            if (!isUpdatingFromCode && !sldMoviePosition.getValueIsAdjusting()) {
                controller.setPlayPos((float) sldMoviePosition.getValue() / sldMoviePosition.getMaximum());
            }
        });
        add(H.makeHorizontalPanel(sldMoviePosition, lblPosition));
    }

    private void addVolumeControl() {
        JSlider sldVolume = new JSlider();
        add(H.makeHorizontalPanel(new JLabel("Lautstärke"), sldVolume));
    }

    private void addTagControls() {
        JButton btnShowUntagged = new JButton("Untagged anzeigen");
        JTextField txtUntaggedCount = new JTextField(5);
        JButton btnSetTags = new JButton("Tags setzen");
        JCheckBox cbxAutoOpenTagsDialog = new JCheckBox("automatisch öffnen");

        add(H.makeHorizontalPanel(btnShowUntagged, txtUntaggedCount));
        add(btnSetTags);
        add(cbxAutoOpenTagsDialog);
        add(new TagSelectionPanel());
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
        RangeHandler.Range range = new RangeHandler().getRangeForFile(file);
        SwingUtilities.invokeLater(() -> {
            if (range != null) {
                txtTimerangeStart.setText(String.valueOf(range.start));
                txtTimerangeEnde.setText(String.valueOf(range.end));
            } else {
                txtTimerangeStart.setText(null);
                txtTimerangeEnde.setText(null);
            }
        });
    }

    public boolean isAutostart() {
        return cbxAutostart.isSelected();
    }

    public boolean isIgnoreTimerange() {
        return chxIgnoreTimerange.isSelected();
    }
}