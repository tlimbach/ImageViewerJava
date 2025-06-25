package ui;

import event.CurrentDirectoryChangedEvent;
import service.Controller;
import service.EventBus;
import service.H;
import service.TagHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TagSelectionPanel extends JPanel {

    private final JPanel checkboxPanel;
    private final JScrollPane scrollPane;
    private final TagHandler handler = TagHandler.getInstance();
    private final List<JCheckBox> checkboxes = new ArrayList<>();

    private final JCheckBox cbxAnyMatch = new JCheckBox("Any");

    public TagSelectionPanel() {
        setLayout(new BorderLayout());

        checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));

        scrollPane = new JScrollPane(checkboxPanel);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.setPreferredSize(new Dimension(200, 300)); // anpassbar

        cbxAnyMatch.setSelected(true);
        cbxAnyMatch.addActionListener(e -> fireTagSelectionChanged());

        add(scrollPane, BorderLayout.CENTER);

        add(cbxAnyMatch, BorderLayout.SOUTH);
        // Initialer Load
        reloadTags();

        EventBus.get().register(CurrentDirectoryChangedEvent.class, e->{
            handler.load();
            reloadTags();
        });
    }

    /** Lädt aktuelle Tags neu aus dem Handler. */
    public void reloadTags() {
        setTags(handler.allTags());
    }

    /** Baut die Checkboxen neu auf. */
    public synchronized  void setTags(Map<String, Integer> tags) {
        checkboxPanel.removeAll();
        checkboxes.clear();
        H.out("keyset size " + tags.keySet().size());
        List<String> sortedTags = new ArrayList<>(tags.keySet());
        sortedTags.sort(String.CASE_INSENSITIVE_ORDER);

        for (String tag : sortedTags) {
            int count = tags.get(tag);
            JCheckBox checkbox = new JCheckBox(tag + " (" + count + ")");
            checkbox.addActionListener(e -> fireTagSelectionChanged());

            // Rechtsklick-Listener für Umbenennen
            checkbox.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        showRenameDialog(tag);
                    }
                }
            });

            checkboxes.add(checkbox);
            checkboxPanel.add(checkbox);
        }

        revalidate();
        repaint();
    }

    /** Gibt Liste der aktuell ausgewählten Tags zurück. */
    public List<String> getSelectedTags() {
        List<String> selected = new ArrayList<>();
        for (JCheckBox checkbox : checkboxes) {
            if (checkbox.isSelected()) {
                String label = checkbox.getText();
                int index = label.lastIndexOf(" (");
                selected.add(index > 0 ? label.substring(0, index) : label);
            }
        }
        return selected;
    }

    /** Informiert Controller über neue Selektion. */
    private void fireTagSelectionChanged() {
        List<String> selectedTags = getSelectedTags();
        List<String> files = handler.getFilesForSelectedTags(selectedTags, cbxAnyMatch.isSelected());
        Controller.getInstance().setSelectedFiles(files);
    }

    /** Öffnet Umbenennen-Dialog für ein Tag. */
    private void showRenameDialog(String oldTag) {
        String newTag = JOptionPane.showInputDialog(this,
                "Neuer Name für Tag: \"" + oldTag + "\"",
                oldTag);

        if (newTag != null && !newTag.trim().isEmpty() && !newTag.equals(oldTag)) {
            newTag = newTag.trim();

            if (handler.allTags().containsKey(newTag)) {
                int result = JOptionPane.showConfirmDialog(
                        this,
                        "Das Tag \"" + newTag + "\" existiert bereits.\nMöchten Sie wirklich zusammenführen?",
                        "Tag existiert bereits",
                        JOptionPane.YES_NO_OPTION
                );
                if (result != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            handler.renameTag(oldTag, newTag);
            reloadTags();
        }
    }
}