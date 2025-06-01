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
}
