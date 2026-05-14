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

    OverlayButton(String text) {
        super(text);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setForeground(Color.BLACK);
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
    }
}
