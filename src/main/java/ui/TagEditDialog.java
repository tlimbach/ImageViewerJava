package ui;

import model.AppState;
import service.Controller;
import service.TagHandler;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class TagEditDialog extends JDialog {

    private File file;
    private final List<JCheckBox> checkBoxes = new ArrayList<>();
    private final JTextField newTagsField = new JTextField();
    private final JPanel tagsPanel = new JPanel();
    private final JPanel centerPanel = new JPanel(new BorderLayout());

    public TagEditDialog() {
        super((Frame) null, "Tags bearbeiten", false); // <- nicht modal!

        setLayout(new BorderLayout(10, 10));
        setDefaultCloseOperation(HIDE_ON_CLOSE); // nicht DISPOSE_ON_CLOSE

        tagsPanel.setLayout(new BoxLayout(tagsPanel, BoxLayout.Y_AXIS));
        tagsPanel.setBorder(BorderFactory.createTitledBorder("Existierende Tags"));

        JPanel newTagPanel = new JPanel(new BorderLayout());
        newTagPanel.setBorder(BorderFactory.createTitledBorder("Neue Tags (durch Leerzeichen getrennt)"));
        newTagPanel.add(newTagsField, BorderLayout.CENTER);

        JButton saveButton = new JButton("Speichern");
        saveButton.addActionListener(e -> saveTags());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(saveButton);
        JScrollPane sp = new JScrollPane(tagsPanel);
        sp.getVerticalScrollBar().setUnitIncrement(8);
        centerPanel.add(sp, BorderLayout.CENTER);
        centerPanel.add(newTagPanel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(400, 400);
        setLocationRelativeTo(null);
    }

    public void setFile(File file, boolean forceShow) {
        this.file = file;
        updateContent();

        if (forceShow || AppState.get().isAutoOpenTagsDialog())
        setVisible(true);
    }

    private void updateContent() {
        tagsPanel.removeAll();
        checkBoxes.clear();
        newTagsField.setText("");

        List<String> currentTags = TagHandler.getInstance().getTagsForFile(file.getAbsolutePath());
        List<String> allTags = new ArrayList<>(TagHandler.getInstance().allTags().keySet());
        Collections.sort(allTags, String.CASE_INSENSITIVE_ORDER);

        for (String tag : allTags) {
            JCheckBox box = new JCheckBox(tag);
            box.setSelected(currentTags.contains(tag));
            checkBoxes.add(box);
            tagsPanel.add(box);
        }

        tagsPanel.revalidate();
        tagsPanel.repaint();
    }

    private void saveTags() {
        List<String> selectedTags = checkBoxes.stream()
                .filter(AbstractButton::isSelected)
                .map(AbstractButton::getText)
                .collect(Collectors.toList());

        String newTags = newTagsField.getText().trim();
        if (!newTags.isEmpty()) {
            selectedTags.addAll(Arrays.asList(newTags.split("\\s+")));
        }

        String joined = String.join(" ", selectedTags);
        TagHandler.getInstance().setTagsToFile(joined, file.getAbsolutePath());

        Controller.getInstance().getControlPanel().reloadTagSelectinPanel();
    }
}