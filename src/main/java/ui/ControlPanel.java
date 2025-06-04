package ui;

import service.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class ControlPanel extends JPanel {

    private final Controller controller = Controller.getInstance();
    private final JLabel lblPosition = new JLabel("----");
    private final JLabel lblThumbnailsLoadedCount = new JLabel("---------");
    private final JTextField txtTimerangeStart = new JTextField(5);
    private final JTextField txtTimerangeEnde = new JTextField(5);
    private final JCheckBox chxIgnoreTimerange = new JCheckBox("Z. ignorieren");
    private final JCheckBox cbxAutostart = new JCheckBox("Autostart");

    private final JSlider sldMoviePosition = new JSlider();
    private JTextField txtDuration;
    private JToggleButton btnPlayPause;
    private boolean isUpdatingFromCode = false;
    private final SlideshowManager slideshowManager = new SlideshowManager();

    private JLabel txtUntaggedCount;
    private File currentFile;

     private  RangeHandler rangeHandler = new RangeHandler();

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
        btnPlayPause.addActionListener(a -> controller.playPause(btnPlayPause.isSelected()));

        JButton btnStop = new JButton("Stop");
        btnStop.addActionListener(a -> {
            controller.stop();
            slideshowManager.stop();
            resetPlayPauseButton();
            controller.hideMediaPanel();
        });

        JToggleButton btnFullscreen = new JToggleButton("Vollbild");
        btnFullscreen.addActionListener(a -> controller.setFullscreen(btnFullscreen.isSelected()));

        add(H.makeHorizontalPanel(btnPlayPause, btnStop));
        add(H.makeHorizontalPanel( cbxAutostart, btnFullscreen));
    }

    private void addRangeControls() {
        JButton btnSaveRange = new JButton("übernehmen");
        add(H.makeHorizontalPanel(new JLabel("von"), txtTimerangeStart, new JLabel("bis"), txtTimerangeEnde));
        add(H.makeHorizontalPanel(btnSaveRange, chxIgnoreTimerange));
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

    }

    private void addSliderPositionControl() {
        sldMoviePosition.addChangeListener(c -> {
            if (!isUpdatingFromCode && !sldMoviePosition.getValueIsAdjusting()) {
                controller.setPlayPos((float) sldMoviePosition.getValue() / sldMoviePosition.getMaximum());
            }
        });
        add(H.makeHorizontalPanel(new JLabel("Pos"), sldMoviePosition, lblPosition));
    }

    private void addVolumeControl() {
        JSlider sldVolume = new JSlider();
        add(H.makeHorizontalPanel(new JLabel("Lautstärke"), sldVolume));
    }

    private void addTagControls() {
        JButton btnShowUntagged = new JButton("Untagged anzeigen");

        btnShowUntagged.addActionListener(a->{
            List<File> files = TagHandler.getInstance().getUntaggedFiles();
            Controller.getInstance().setSelectedFiles(files.stream().map(File::getAbsolutePath).toList());
        });

        txtUntaggedCount = new JLabel("(0)");
        JButton btnSetTags = new JButton("Tags setzen");
        JCheckBox cbxAutoOpenTagsDialog = new JCheckBox("automatisch öffnen");

        add(H.makeHorizontalPanel(btnShowUntagged, txtUntaggedCount));
        add(H.makeHorizontalPanel(btnSetTags, cbxAutoOpenTagsDialog));
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
        currentFile = file;
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
    }

    public boolean isAutostart() {
        return cbxAutostart.isSelected();
    }

    public boolean isIgnoreTimerange() {
        return chxIgnoreTimerange.isSelected();
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
}