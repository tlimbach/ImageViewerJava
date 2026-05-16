package ui;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class TagFilterDialog extends JDialog {
    private final TagSelectionPanel filterPanel;

    public TagFilterDialog(Window owner, Consumer<Integer> filterResultCountListener) {
        super(owner, "Tags filtern", ModalityType.MODELESS);

        setLayout(new BorderLayout(8, 8));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        filterPanel = new TagSelectionPanel(TagSelectionPanel.Mode.FILTER);
        filterPanel.setFilterResultCountListener(filterResultCountListener);
        add(filterPanel, BorderLayout.CENTER);

        JButton closeButton = new JButton("Schließen");
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(320, 520);
        setLocationRelativeTo(owner);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    public void clearFilterSelection() {
        filterPanel.clearFilterSelection();
    }
}
