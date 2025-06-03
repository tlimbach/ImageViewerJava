package ui;

import service.Controller;
import service.TagHandler;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TagSelectionPanel extends JPanel {

    private final JPanel checkboxPanel;

    private TagHandler handler = new TagHandler();
    private final List<JCheckBox> checkboxes = new ArrayList<>();

    public TagSelectionPanel() {
        setLayout(new BorderLayout());

        checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(checkboxPanel);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.setPreferredSize(new Dimension(200, 300)); // anpassbar

        add(scrollPane, BorderLayout.CENTER);


        setTags(handler.allTags());
    }

    public void setTags(Map<String, Integer> tags) {
        checkboxPanel.removeAll();
        checkboxes.clear();

        for (String tag : tags.keySet()) {
            int count = tags.get(tag);
            JCheckBox checkbox = new JCheckBox(tag + " (" + count + ")");
            checkbox.addActionListener(e -> {
                List<String> selectedTags = getSelectedTags();
                List<String> files = handler.getFilesForSelectedTags(selectedTags);
                Controller.getInstance().setSelectedFiles(files);
            });
            checkboxes.add(checkbox);
            checkboxPanel.add(checkbox);
        }

        revalidate();
        repaint();
    }

    public List<String> getSelectedTags() {
        List<String> selected = new ArrayList<>();
        for (JCheckBox checkbox : checkboxes) {
            if (checkbox.isSelected()) {
                String label = checkbox.getText();
                int index = label.lastIndexOf(" (");
                if (index > 0) {
                    label = label.substring(0, index); // schneidet " (12)" ab
                }
                selected.add(label);
            }
        }
        return selected;
    }
}