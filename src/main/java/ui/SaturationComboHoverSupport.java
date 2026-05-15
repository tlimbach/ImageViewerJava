package ui;

import service.ImageSaturationHandler;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.util.function.Consumer;

final class SaturationComboHoverSupport {
    private static final String LISTENER_INSTALLED_PROPERTY = "saturationHoverListenerInstalled";
    private static final String LAST_HOVER_LEVEL_PROPERTY = "saturationLastHoverLevel";
    private static final String INITIAL_LEVEL_PROPERTY = "saturationInitialLevel";
    private static final String COMMITTED_PROPERTY = "saturationCommitted";
    private static final int AUTO_OPEN_DELAY_MS = 500;

    private SaturationComboHoverSupport() {
    }

    static void install(JComboBox<ImageSaturationHandler.SaturationLevel> combo,
                        Consumer<ImageSaturationHandler.SaturationLevel> onPreview,
                        Consumer<ImageSaturationHandler.SaturationLevel> onCommit,
                        Consumer<ImageSaturationHandler.SaturationLevel> onCancel) {
        suppressMouseClickPopup(combo);
        installHoverAutoOpen(combo);
        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                combo.putClientProperty(LAST_HOVER_LEVEL_PROPERTY, null);
                combo.putClientProperty(INITIAL_LEVEL_PROPERTY, combo.getSelectedItem());
                combo.putClientProperty(COMMITTED_PROPERTY, false);
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
                        onPreview.accept(level);
                    }
                });
                list.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        ImageSaturationHandler.SaturationLevel level = levelAt(list, e);
                        if (level == null) return;

                        combo.putClientProperty(COMMITTED_PROPERTY, true);
                        onCommit.accept(level);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        if (!isPointerInside(combo) && !isPointerInside(list)) {
                            combo.hidePopup();
                        }
                    }
                });
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                restorePreviewIfNeeded(combo, onCancel);
                combo.putClientProperty(LAST_HOVER_LEVEL_PROPERTY, null);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                restorePreviewIfNeeded(combo, onCancel);
                combo.putClientProperty(LAST_HOVER_LEVEL_PROPERTY, null);
            }
        });
    }

    private static void restorePreviewIfNeeded(JComboBox<ImageSaturationHandler.SaturationLevel> combo,
                                               Consumer<ImageSaturationHandler.SaturationLevel> onCancel) {
        if (Boolean.TRUE.equals(combo.getClientProperty(COMMITTED_PROPERTY))) return;

        Object initial = combo.getClientProperty(INITIAL_LEVEL_PROPERTY);
        if (initial instanceof ImageSaturationHandler.SaturationLevel level) {
            onCancel.accept(level);
        }
    }

    private static ImageSaturationHandler.SaturationLevel levelAt(JList<?> list, MouseEvent e) {
        int index = list.locationToIndex(e.getPoint());
        if (index < 0) return null;

        Rectangle cellBounds = list.getCellBounds(index, index);
        if (cellBounds == null || !cellBounds.contains(e.getPoint())) return null;

        Object value = list.getModel().getElementAt(index);
        if (value instanceof ImageSaturationHandler.SaturationLevel level) {
            return level;
        }
        return null;
    }

    private static void suppressMouseClickPopup(JComboBox<ImageSaturationHandler.SaturationLevel> combo) {
        removeMouseListeners(combo);
        for (Component child : combo.getComponents()) {
            removeMouseListeners(child);
        }
    }

    private static void removeMouseListeners(Component component) {
        for (MouseListener listener : component.getMouseListeners()) {
            component.removeMouseListener(listener);
        }
    }

    private static void installHoverAutoOpen(JComboBox<ImageSaturationHandler.SaturationLevel> combo) {
        Timer hoverTimer = new Timer(AUTO_OPEN_DELAY_MS, e -> {
            if (combo.isShowing() && combo.isEnabled() && combo.isVisible() && !combo.isPopupVisible()) {
                combo.showPopup();
            }
        });
        hoverTimer.setRepeats(false);

        MouseAdapter hoverHandler = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hoverTimer.restart();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!isPointerInside(combo)) {
                    hoverTimer.stop();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                e.consume();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                e.consume();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                e.consume();
            }
        };

        combo.addMouseListener(hoverHandler);
        for (Component child : combo.getComponents()) {
            child.addMouseListener(hoverHandler);
        }
    }

    private static boolean isPointerInside(Component component) {
        java.awt.Point pointer = java.awt.MouseInfo.getPointerInfo() == null
                ? null
                : java.awt.MouseInfo.getPointerInfo().getLocation();
        if (pointer == null) return false;

        SwingUtilities.convertPointFromScreen(pointer, component);
        return component.contains(pointer);
    }

    private static JList<?> popupList(JComboBox<?> combo) {
        Object child = combo.getAccessibleContext().getAccessibleChild(0);
        if (child instanceof BasicComboPopup popup) {
            return popup.getList();
        }
        return null;
    }
}
