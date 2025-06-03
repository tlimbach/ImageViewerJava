package service;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class H {

    public static Component makeHorizontalPanel(JComponent... components) {
        JPanel panel = new JPanel(new FlowLayout());
        Arrays.stream(components).forEach(p->panel.add(p));
        return panel;
    }

    public static void out(String string) {
        System.out.println(string);
    }

    public static void isUiThread(String a) {
        if (SwingUtilities.isEventDispatchThread()) {
            System.out.println("Ich bin im UI-Thread. " + a);
        } else {
            System.out.println("Ich bin NICHT im UI-Thread." + a);
        }
    }
}
