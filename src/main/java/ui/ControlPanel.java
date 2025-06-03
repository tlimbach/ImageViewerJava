package ui;

import service.Controller;
import service.H;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;

public class ControlPanel extends JPanel {
    private boolean isUpdatingFromCode = false;
    private JSlider sldMoviePosition;
    private  JLabel lblPosition;
    private Controller controller = Controller.getInstance();

    private JLabel lblThumbnailsLoadedCount;

    public ControlPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JButton btnFileChooser = new JButton("Verzeichnis wählen");
        add(btnFileChooser);

        btnFileChooser.addActionListener(a -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedDir = chooser.getSelectedFile();
                Path directory = selectedDir.toPath();
                Controller.getInstance().handleDirectory(directory);
            }
        });


        JTextField txtDuiration = new JTextField(20);
        txtDuiration.setToolTipText("Anzeigedauer pro Bild");
        JButton btnSlideshowStart = new JButton("Start");
        JButton btnSlideshowStop = new JButton("Stop");
        add(H.makeHorizontalPanel(txtDuiration, btnSlideshowStart, btnSlideshowStop));


        JToggleButton btnPlayPause = new JToggleButton("Play/Pause");
        JCheckBox cbxAutostart = new JCheckBox("Autostart");
        add(H.makeHorizontalPanel(btnPlayPause, cbxAutostart));
        btnPlayPause.addActionListener(a -> {
            controller.playPause(btnPlayPause.isSelected());
        });

        JToggleButton btnToggleFullscreen = new JToggleButton("Vollbild umschalten");
        btnToggleFullscreen.addActionListener(a -> controller.setFullscreen(btnToggleFullscreen.isSelected()));
        add(btnToggleFullscreen);

        JTextField txtTimerangeStart = new JTextField(5);
        JTextField txtTimerangeEnde = new JTextField(5);
        JCheckBox chxIgnoreTimerange = new JCheckBox("Zeitbereich ignorieren");
        add(H.makeHorizontalPanel(txtTimerangeStart, txtTimerangeEnde, chxIgnoreTimerange));

        JButton btnTimerangeSave = new JButton("Bereich übernehmen");
        add(btnTimerangeSave);

        sldMoviePosition = new JSlider();
        sldMoviePosition.addChangeListener(c->{
            if (isUpdatingFromCode || !sldMoviePosition.getValueIsAdjusting()) return;
            controller.setPlayPos((float) sldMoviePosition.getValue()/ (float) sldMoviePosition.getMaximum());
        });

        lblPosition = new JLabel("----");
        add(H.makeHorizontalPanel(sldMoviePosition, lblPosition));


        JSlider sldVolume = new JSlider();
        add(H.makeHorizontalPanel(new JLabel("Lautstärke"), sldVolume));

        JButton btnShowUntagged = new JButton("Untagged anzeigen");
        JTextField txtUntaggedCount = new JTextField(5);

        add(H.makeHorizontalPanel(btnShowUntagged, txtUntaggedCount));

        JButton btnSetTags = new JButton("Tags setzen");
        JCheckBox cbxAutoOpenTagsDialog = new JCheckBox("automatisch öffnen");

        add(new TagSelectionPanel());

        lblThumbnailsLoadedCount = new JLabel("---------");
        add(lblThumbnailsLoadedCount);
    }



    public void setCurrentPlayPosMillis(long millis, long total) {
        if (total <= 0) return; // Abbruch bei fehlerhaftem Wert
        int currentSliderPos = (int) (sldMoviePosition.getMaximum() * millis / total);
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        String elapsed = String.format("%02d:%02d", minutes, seconds)+ "  ("+(int)(millis/1000)+")";
        SwingUtilities.invokeLater(() -> {
            isUpdatingFromCode = true;
            sldMoviePosition.setValue(currentSliderPos);
            isUpdatingFromCode = false;
            lblPosition.setText(elapsed);
        });

    }

    public void setThumbnailsLoaded(int thumbnailsLoadedCount, int totalThumbnails) {
        SwingUtilities.invokeLater(()->lblThumbnailsLoadedCount.setText("Thumbnails geladen: " + thumbnailsLoadedCount + " / " + totalThumbnails));
    }
}
