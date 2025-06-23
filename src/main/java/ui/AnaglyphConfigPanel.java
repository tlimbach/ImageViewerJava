package ui;

import service.AnaglyphUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

@SuppressWarnings("serial")
public class AnaglyphConfigPanel extends JPanel {

    private final JSlider redSlider = new JSlider(-50, 50, 0);
    private final JSlider cyanSlider = new JSlider(-50, 50, 0);
    private final JButton resetButton = new JButton("Zurücksetzen");

    private final JLabel previewLabel = new JLabel();

    // Testbilder: linkes & rechtes Original
    private final BufferedImage leftTestImage;
    private final BufferedImage rightTestImage;

    public AnaglyphConfigPanel() {
        setLayout(new BorderLayout());

        int width = 1200;
        int height = 600;

        // Testbilder erzeugen
        leftTestImage = createLeftTestPattern(width, height);
        rightTestImage = createRightTestPattern(width, height);

        JPanel controls = new JPanel(new GridLayout(0, 1));
        controls.add(new JLabel("Rot-Korrektur"));
        controls.add(redSlider);
        controls.add(new JLabel("Cyan-Korrektur"));
        controls.add(cyanSlider);
        controls.add(resetButton);

        add(controls, BorderLayout.NORTH);
        add(previewLabel, BorderLayout.CENTER);

        redSlider.addChangeListener(e -> updatePreview());
        cyanSlider.addChangeListener(e -> updatePreview());
        resetButton.addActionListener(e -> {
            redSlider.setValue(0);
            cyanSlider.setValue(0);
            updatePreview();
        });

        updatePreview();
    }

    private void updatePreview() {
        float redGain = 1.0f + redSlider.getValue() / 100.0f;
        float cyanGain = 1.0f + cyanSlider.getValue() / 100.0f;

        // Neue Matrix für PREVIEW erzeugen
        float[][] newMatrix = {
                { 0.437f * redGain, 0.449f * redGain, 0.164f * redGain },   // L1
                { -0.062f * redGain, -0.062f * redGain, -0.024f * redGain }, // R1

                { -0.048f * cyanGain, -0.050f * cyanGain, -0.017f * cyanGain }, // L2
                { 0.378f * cyanGain,  0.733f * cyanGain,  0.088f * cyanGain },  // R2

                { -0.086f * cyanGain, -0.089f * cyanGain, -0.034f * cyanGain }, // L3
                { -0.016f * cyanGain, -0.017f * cyanGain, -0.006f * cyanGain }  // R3
        };

        // Nur für das Preview verwenden:
        AnaglyphUtils.setDuboisMatrix(newMatrix);
        BufferedImage preview = AnaglyphUtils.createDuboisAnaglyph(leftTestImage, rightTestImage);
        previewLabel.setIcon(new ImageIcon(preview));
    }

    private BufferedImage createLeftTestPattern(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Hintergrund: dunkelgrau statt tiefschwarz → besserer Kontrast
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, width, height);

        // Linker Block: reines Rot
        g.setColor(Color.RED); // = (255,0,0)
        g.fillRect(50, 50, width / 2 - 60, height - 100);

        // Graustufen-Balken unten
        for (int i = 0; i < 10; i++) {
            int gray = i * 25;
            g.setColor(new Color(gray, gray, gray));
            g.fillRect(50 + i * 30, height - 50, 30, 40);
        }

        // Weißes Fadenkreuz in der Mitte
        g.setColor(Color.WHITE);
        int cx = width / 2;
        int cy = height / 2;
        g.fillRect(cx - 1, cy - height / 4, 2, height / 2);
        g.fillRect(cx - width / 4, cy - 1, width / 2, 2);

        g.dispose();
        return img;
    }

    private BufferedImage createRightTestPattern(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Hintergrund: dunkelgrau
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, width, height);

        // Rechter Block: reines Cyan
        g.setColor(Color.CYAN); // = (0,255,255)
        g.fillRect(width / 2 + 10, 50, width / 2 - 60, height - 100);

        // Graustufen-Balken unten
        for (int i = 0; i < 10; i++) {
            int gray = i * 25;
            g.setColor(new Color(gray, gray, gray));
            g.fillRect(width / 2 + 10 + i * 30, height - 50, 30, 40);
        }

        // Weißes Fadenkreuz in der Mitte
        g.setColor(Color.WHITE);
        int cx = width / 2;
        int cy = height / 2;
        g.fillRect(cx - 1, cy - height / 4, 2, height / 2);
        g.fillRect(cx - width / 4, cy - 1, width / 2, 2);

        g.dispose();
        return img;
    }
}