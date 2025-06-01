package ui;

import service.Controller;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;

public class ControlPanel extends JPanel {

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
    }

}
