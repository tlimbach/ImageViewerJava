package ui;

import event.CurrentDirectoryChangedEvent;
import event.TagsChangedEvent;
import model.AppState;
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
import java.util.function.Consumer;

public class TagSelectionPanel extends JPanel {
    public enum Mode {
        EDIT_CURRENT_FILE,
        FILTER
    }

    private final Mode mode;
    private final JPanel checkboxPanel;
    private final JScrollPane scrollPane;
    private final TagHandler handler = TagHandler.getInstance();
    private final List<JCheckBox> checkboxes = new ArrayList<>();

    private final JCheckBox cbxAnyMatch = new JCheckBox("Any");


    private final JTextField txtMinduration = new JTextField(5);

    private JButton btnApplyMinDuration = new JButton("ok");

    private boolean updatingFromCode;
    private Consumer<Integer> filterResultCountListener;

    public TagSelectionPanel() {
        this(Mode.EDIT_CURRENT_FILE);
    }

    public TagSelectionPanel(Mode mode) {
        this.mode = mode;
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

        txtMinduration.setToolTipText("Minimale Dauer in Sekunden");

        if (mode == Mode.FILTER) {
            add(H.makeHorizontalPanel(cbxAnyMatch, new JPopupMenu.Separator(), new JLabel("Mindestdauer"), txtMinduration, btnApplyMinDuration), BorderLayout.SOUTH);
        }
        // Initialer Load
        reloadTags();

        btnApplyMinDuration.addActionListener(a->{
            try {
                AppState.get().setMinimunDuration(Integer.valueOf(txtMinduration.getText()));
            }catch (Exception ex){
                //egal
            }
            fireTagSelectionChanged();
        });



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
        sortedTags.sort((left, right) -> {
            int countCompare = Integer.compare(tags.getOrDefault(right, 0), tags.getOrDefault(left, 0));
            if (countCompare != 0) return countCompare;
            return String.CASE_INSENSITIVE_ORDER.compare(left, right);
        });

        for (String tag : sortedTags) {
            int count = tags.get(tag);
            JCheckBox checkbox = new JCheckBox(tag + " (" + count + ")");
            checkbox.addActionListener(e -> {
                if (updatingFromCode) return;
                if (mode == Mode.FILTER) {
                    fireTagSelectionChanged();
                } else {
                    saveTagsForCurrentFile();
                }
            });

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

        updateSelectionForCurrentFile();
        revalidate();
        repaint();
    }

    public void updateSelectionForCurrentFile() {
        if (mode != Mode.EDIT_CURRENT_FILE) return;

        updatingFromCode = true;
        try {
            for (JCheckBox checkbox : checkboxes) {
                checkbox.setSelected(false);
            }

            if (AppState.get().getCurrentFile() == null) {
                return;
            }

            List<String> fileTags = handler.getTagsForFile(AppState.get().getCurrentFile().getName());
            for (JCheckBox checkbox : checkboxes) {
                checkbox.setSelected(fileTags.contains(tagFromCheckbox(checkbox)));
            }
        } finally {
            updatingFromCode = false;
        }
    }

    /** Gibt Liste der aktuell ausgewählten Tags zurück. */
    public List<String> getSelectedTags() {
        List<String> selected = new ArrayList<>();
        for (JCheckBox checkbox : checkboxes) {
            if (checkbox.isSelected()) {
                String label = checkbox.getText();
                selected.add(tagFromCheckbox(checkbox));
            }
        }
        return selected;
    }

    private String tagFromCheckbox(JCheckBox checkbox) {
        String label = checkbox.getText();
        int index = label.lastIndexOf(" (");
        return index > 0 ? label.substring(0, index) : label;
    }

    private void saveTagsForCurrentFile() {
        if (AppState.get().getCurrentFile() == null) return;

        handler.setTagsToFile(getSelectedTags(), AppState.get().getCurrentFile().getName());
        EventBus.get().publish(new TagsChangedEvent());
    }

    /** Informiert Controller über neue Selektion. */
    private void fireTagSelectionChanged() {
        List<String> selectedTags = getSelectedTags();
        List<String> files = handler.getFilesForSelectedTags(selectedTags, cbxAnyMatch.isSelected());
        Controller.getInstance().setSelectedFiles(files);
        if (filterResultCountListener != null) {
            filterResultCountListener.accept(files == null ? null : files.size());
        }
    }

    public void setFilterResultCountListener(Consumer<Integer> filterResultCountListener) {
        this.filterResultCountListener = filterResultCountListener;
    }

    public void clearFilterSelection() {
        if (mode != Mode.FILTER) return;

        updatingFromCode = true;
        try {
            for (JCheckBox checkbox : checkboxes) {
                checkbox.setSelected(false);
            }
            cbxAnyMatch.setSelected(true);
            txtMinduration.setText("");
        } finally {
            updatingFromCode = false;
        }
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
            EventBus.get().publish(new TagsChangedEvent());
        }
    }
}
