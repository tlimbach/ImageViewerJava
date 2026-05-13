package ui;

import javax.swing.*;
import java.awt.*;

class DeleteConfirmationOverlay extends JPanel {

    private final JLabel message = new JLabel("", SwingConstants.CENTER);
    private final JButton confirmButton = new JButton("Löschen");
    private final JButton cancelButton = new JButton("Abbrechen");
    private Runnable onConfirm;
    private Runnable onCancel;

    DeleteConfirmationOverlay() {
        setLayout(new BorderLayout(8, 8));
        setOpaque(true);
        setBackground(new Color(20, 20, 20, 230));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 221, 0), 2),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));

        message.setForeground(Color.WHITE);
        add(message, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancelButton);
        buttons.add(confirmButton);
        add(buttons, BorderLayout.SOUTH);

        confirmButton.addActionListener(e -> {
            if (onConfirm != null) onConfirm.run();
        });
        cancelButton.addActionListener(e -> {
            setVisible(false);
            if (onCancel != null) onCancel.run();
        });

        setVisible(false);
    }

    void showForFile(String fileName, Runnable onConfirm, Runnable onCancel) {
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        message.setText("<html><div style='text-align:center'>Bild wirklich löschen?<br><b>"
                + escapeHtml(fileName)
                + "</b></div></html>");
        setVisible(true);
        revalidate();
        repaint();
    }

    void showError() {
        message.setText("<html><div style='text-align:center'>Datei konnte nicht gelöscht werden.</div></html>");
        confirmButton.setEnabled(false);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            confirmButton.setEnabled(true);
        }
    }

    private String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
