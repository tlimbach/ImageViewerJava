package ui;

import javax.swing.*;
import java.awt.*;

class OverlayButton extends JButton {
    private static final Color ENABLED_BACKGROUND = new Color(245, 245, 245, 185);
    private static final Color DISABLED_BACKGROUND = new Color(245, 245, 245, 95);
    private static final Color HOVER_BACKGROUND = new Color(255, 255, 255, 220);
    private static final Color PRESSED_BACKGROUND = new Color(225, 225, 225, 220);
    private static final Color BORDER_COLOR = new Color(0, 0, 0, 80);
    private Color textColor = Color.BLACK;
    private boolean checkboxVisible;
    private boolean checked;

    OverlayButton(String text) {
        super(text);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setForeground(Color.BLACK);
    }

    void setCheckboxVisible(boolean checkboxVisible) {
        this.checkboxVisible = checkboxVisible;
        setMargin(new Insets(2, 8, 2, checkboxVisible ? 24 : 8));
        repaint();
    }

    void setChecked(boolean checked) {
        this.checked = checked;
        repaint();
    }

    void setTextColor(Color textColor) {
        this.textColor = textColor;
        setForeground(textColor);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        ButtonModel model = getModel();
        Color background = isEnabled() ? ENABLED_BACKGROUND : DISABLED_BACKGROUND;
        if (isEnabled() && model.isPressed()) {
            background = PRESSED_BACKGROUND;
        } else if (isEnabled() && model.isRollover()) {
            background = HOVER_BACKGROUND;
        }

        g2.setColor(background);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
        g2.setColor(BORDER_COLOR);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
        g2.dispose();

        setForeground(textColor);
        super.paintComponent(g);

        if (checkboxVisible) {
            paintCheckbox(g);
        }
    }

    private void paintCheckbox(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int size = 12;
        int x = getWidth() - size - 8;
        int y = (getHeight() - size) / 2;

        g2.setColor(new Color(255, 255, 255, 180));
        g2.fillRoundRect(x, y, size, size, 3, 3);
        g2.setColor(textColor);
        g2.drawRoundRect(x, y, size, size, 3, 3);

        if (checked) {
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(x + 3, y + 6, x + 5, y + 9);
            g2.drawLine(x + 5, y + 9, x + 10, y + 3);
        }
        g2.dispose();
    }
}
