package ui;

import event.TagsChangedEvent;
import model.AppState;
import service.Controller;
import service.EventBus;
import service.TagHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private final JSlider opacitySlider = new JSlider(35, 100, 100);
    private Point dragOffset;

    public TagEditDialog(Window owner) {
        super(owner, "Tags bearbeiten", ModalityType.MODELESS);

        setUndecorated(true);
        setLayout(new BorderLayout(10, 10));
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setAlwaysOnTop(true);

        tagsPanel.setLayout(new BoxLayout(tagsPanel, BoxLayout.Y_AXIS));
        tagsPanel.setBorder(BorderFactory.createTitledBorder("Existierende Tags"));

        JPanel newTagPanel = new JPanel(new BorderLayout());
        newTagPanel.setBorder(BorderFactory.createTitledBorder("Neue Tags (durch Komma getrennt)"));
        newTagPanel.add(newTagsField, BorderLayout.CENTER);

        JButton saveButton = new JButton("Speichern");
        saveButton.addActionListener(e -> saveTags());

        opacitySlider.setToolTipText("Durchsichtigkeit");
        opacitySlider.setPreferredSize(new Dimension(90, opacitySlider.getPreferredSize().height));
        opacitySlider.addChangeListener(e -> applyOpacity(opacitySlider.getValue() / 100f));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(new JLabel("Deckkraft"));
        buttonPanel.add(opacitySlider);
        buttonPanel.add(saveButton);
        JScrollPane sp = new JScrollPane(tagsPanel);
        sp.getVerticalScrollBar().setUnitIncrement(8);
        centerPanel.add(sp, BorderLayout.CENTER);
        centerPanel.add(newTagPanel, BorderLayout.SOUTH);

        add(createTitleBar(), BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(400, 400);
        setLocationRelativeTo(null);
    }

    private JComponent createTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));
        titleBar.setBackground(new Color(220, 220, 220));

        JLabel title = new JLabel("Tags bearbeiten");
        JButton closeButton = new JButton("x");
        closeButton.setMargin(new Insets(1, 6, 1, 6));
        closeButton.addActionListener(e -> setVisible(false));

        MouseAdapter dragListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOffset = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point screen = e.getLocationOnScreen();
                setLocation(screen.x - dragOffset.x, screen.y - dragOffset.y);
            }
        };
        titleBar.addMouseListener(dragListener);
        titleBar.addMouseMotionListener(dragListener);

        titleBar.add(title, BorderLayout.CENTER);
        titleBar.add(closeButton, BorderLayout.EAST);
        return titleBar;
    }

    private void applyOpacity(float opacity) {
        try {
            setOpacity(opacity);
        } catch (UnsupportedOperationException | IllegalComponentStateException ex) {
            opacitySlider.setEnabled(false);
            opacitySlider.setToolTipText("Durchsichtigkeit wird auf diesem System nicht unterstützt");
        }
    }

    public void setFile(File file, boolean forceShow) {
        this.file = file;
        updateContent();

        if (forceShow || AppState.get().isAutoOpenTagsDialog()) {
            if (!isVisible()) {
                setVisible(true);
            } else {
                requestFocus();
            }
        }
    }

    private void updateContent() {
        tagsPanel.removeAll();
        checkBoxes.clear();
        newTagsField.setText("");

        List<String> currentTags = TagHandler.getInstance().getTagsForFile(file.getName());

        List<String> allTags = new ArrayList<>(TagHandler.getInstance().allTags().keySet());
        Collections.sort(allTags, String.CASE_INSENSITIVE_ORDER);

        for (String tag : allTags) {
            JCheckBox box = new JCheckBox(tag);
            box.addActionListener(a -> saveTags());
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

        String newTagsInput = newTagsField.getText().trim();
        if (!newTagsInput.isEmpty()) {
            String[] newTags = newTagsInput.split("\\s*,\\s*");
            selectedTags.addAll(Arrays.asList(newTags));
        }

        TagHandler.getInstance().setTagsToFile(selectedTags, file.getName());
        EventBus.get().publish(new TagsChangedEvent());
        updateContent();
    }
}
