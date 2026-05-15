package ui;

import service.ImageSaturationHandler;

import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.function.Consumer;

final class SaturationComboHoverSupport {
    private static final String LISTENER_INSTALLED_PROPERTY = "saturationHoverListenerInstalled";
    private static final String LAST_HOVER_LEVEL_PROPERTY = "saturationLastHoverLevel";

    private SaturationComboHoverSupport() {
    }

    static void install(JComboBox<ImageSaturationHandler.SaturationLevel> combo,
                        Consumer<ImageSaturationHandler.SaturationLevel> onHover) {
        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                combo.putClientProperty(LAST_HOVER_LEVEL_PROPERTY, null);
                JList<?> list = popupList(combo);
                if (list == null || Boolean.TRUE.equals(list.getClientProperty(LISTENER_INSTALLED_PROPERTY))) {
                    return;
                }

                list.putClientProperty(LISTENER_INSTALLED_PROPERTY, true);
                list.addMouseMotionListener(new MouseMotionAdapter() {
                    @Override
                    public void mouseMoved(MouseEvent e) {
                        int index = list.locationToIndex(e.getPoint());
                        if (index < 0) return;

                        Rectangle cellBounds = list.getCellBounds(index, index);
                        if (cellBounds == null || !cellBounds.contains(e.getPoint())) return;

                        Object value = list.getModel().getElementAt(index);
                        if (!(value instanceof ImageSaturationHandler.SaturationLevel level)) return;
                        if (level == combo.getClientProperty(LAST_HOVER_LEVEL_PROPERTY)) return;

                        combo.putClientProperty(LAST_HOVER_LEVEL_PROPERTY, level);
                        list.setSelectedIndex(index);
                        onHover.accept(level);
                    }
                });
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                combo.putClientProperty(LAST_HOVER_LEVEL_PROPERTY, null);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                combo.putClientProperty(LAST_HOVER_LEVEL_PROPERTY, null);
            }
        });
    }

    private static JList<?> popupList(JComboBox<?> combo) {
        Object child = combo.getAccessibleContext().getAccessibleChild(0);
        if (child instanceof BasicComboPopup popup) {
            return popup.getList();
        }
        return null;
    }
}
