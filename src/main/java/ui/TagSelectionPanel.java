package ui;

import service.Controller;
import service.TagHandler;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

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

    public void setTags(List<String> tags) {
        checkboxPanel.removeAll();
        checkboxes.clear();

        for (String tag : tags) {
            JCheckBox checkbox = new JCheckBox(tag);
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
                selected.add(checkbox.getText());
            }
        }
        return selected;
    }
}