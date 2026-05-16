package ui;

import service.BookmarkService;
import service.Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class BookmarkDialog extends JDialog {

    private static final int THUMBNAIL_WIDTH = ThumbnailPanel.THUMBNAIL_WIDTH;
    private static final int THUMBNAIL_HEIGHT = ThumbnailPanel.THUMBNAIL_HEIGHT;

    private final DefaultListModel<BookmarkService.Bookmark> listModel = new DefaultListModel<>();
    private final JList<BookmarkService.Bookmark> bookmarkList = new JList<>(listModel);
    private final JPanel thumbnailPanel = new JPanel(new GridLayout(0, 3, 6, 6));
    private final JCheckBox syncMainViewCheckbox = new JCheckBox("synchronisieren mit Hauptansicht");
    private final Map<String, JLabel> labelsByFilename = new HashMap<>();
    private final Consumer<File> okHandler;
    private final Runnable closeHandler;
    private boolean updatingSelection;
    private File selectedFile;
    private boolean closed;

    public BookmarkDialog(Window owner, Consumer<File> okHandler, Runnable closeHandler) {
        super(owner, "Bookmarks", ModalityType.MODELESS);
        this.okHandler = okHandler;
        this.closeHandler = closeHandler;

        setLayout(new BorderLayout(8, 8));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeWithoutOk();
            }
        });

        bookmarkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookmarkList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectFromList();
            }
        });

        JScrollPane listScrollPane = new JScrollPane(bookmarkList);
        listScrollPane.setPreferredSize(new Dimension(220, 360));

        JButton renameButton = new JButton("Umbenennen");
        renameButton.addActionListener(e -> renameSelectedBookmark());
        JButton deleteButton = new JButton("Löschen");
        deleteButton.addActionListener(e -> removeSelectedBookmark());

        JPanel leftPanel = new JPanel(new BorderLayout(4, 4));
        leftPanel.add(listScrollPane, BorderLayout.CENTER);

        JPanel leftActions = new JPanel();
        leftActions.setLayout(new BoxLayout(leftActions, BoxLayout.Y_AXIS));
        syncMainViewCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        renameButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        deleteButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftActions.add(syncMainViewCheckbox);
        leftActions.add(Box.createVerticalStrut(4));
        leftActions.add(renameButton);
        leftActions.add(Box.createVerticalStrut(4));
        leftActions.add(deleteButton);
        leftPanel.add(leftActions, BorderLayout.SOUTH);

        JScrollPane thumbnailScrollPane = new JScrollPane(thumbnailPanel);
        thumbnailScrollPane.getVerticalScrollBar().setUnitIncrement(12);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> closeWithOk());
        JButton cancelButton = new JButton("Abbrechen");
        cancelButton.addActionListener(e -> closeWithoutOk());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        add(leftPanel, BorderLayout.WEST);
        add(thumbnailScrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okButton);
        getRootPane().registerKeyboardAction(
                e -> closeWithoutOk(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        placeLikeMediaView();
    }

    private void placeLikeMediaView() {
        Rectangle screenBounds = MediaView.getInstance().getPlacementScreenBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        setBounds(
                screenBounds.x + insets.left,
                screenBounds.y + insets.top,
                screenBounds.width - insets.left - insets.right,
                screenBounds.height - insets.top - insets.bottom
        );
    }

    public void showDialog() {
        closed = false;
        selectedFile = null;
        reloadBookmarks();
        setVisible(true);
    }

    private void reloadBookmarks() {
        List<BookmarkService.Bookmark> bookmarks = BookmarkService.getInstance().getBookmarks();

        selectedFile = null;
        listModel.clear();
        labelsByFilename.clear();
        thumbnailPanel.removeAll();

        for (BookmarkService.Bookmark bookmark : bookmarks) {
            listModel.addElement(bookmark);
            addBookmarkThumbnail(bookmark);
        }

        thumbnailPanel.revalidate();
        thumbnailPanel.repaint();

        if (!bookmarks.isEmpty()) {
            bookmarkList.setSelectedIndex(0);
        }
    }

    private void addBookmarkThumbnail(BookmarkService.Bookmark bookmark) {
        JLabel label = new JLabel("", SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
        label.setOpaque(true);
        label.setBackground(Color.DARK_GRAY);
        label.setForeground(Color.WHITE);
        label.setFocusable(true);
        label.setToolTipText(bookmark.label());
        label.putClientProperty("bookmark", bookmark);
        label.putClientProperty("file", bookmark.file());

        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showThumbnailPopup(label, e);
                    return;
                }
                selectFromThumbnail(label);
                label.requestFocusInWindow();
            }
        });
        installThumbnailKeyBindings(label);

        labelsByFilename.put(bookmark.file().getName(), label);
        thumbnailPanel.add(label);
        loadThumbnailAsync(label, bookmark.file());
    }

    private void loadThumbnailAsync(JLabel label, File file) {
        CompletableFuture
                .supplyAsync(() -> createThumbnailIcon(file), Controller.getInstance().getExecutorService())
                .thenAccept(icon -> {
                    if (icon != null) {
                        SwingUtilities.invokeLater(() -> label.setIcon(icon));
                    }
                });
    }

    private Icon createThumbnailIcon(File file) {
        ThumbnailPanel thumbnailPanel = Controller.getInstance().getThumbnailPanel();
        if (thumbnailPanel == null) return null;
        if (!Controller.isImageFile(file)) return createVideoPlaceholder();

        ThumbnailPanel.ThumbnailRenderResult result = thumbnailPanel.createImageThumbnailIcon(file, ThumbnailZoomMode.ZOOMED);
        return result == null ? null : result.icon();
    }

    private Icon createVideoPlaceholder() {
        BufferedImage image = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        g.setColor(Color.WHITE);
        g.drawString("VIDEO", THUMBNAIL_WIDTH / 2 - 18, THUMBNAIL_HEIGHT / 2);
        g.dispose();
        return new ImageIcon(image);
    }

    private void selectFromList() {
        if (updatingSelection) return;

        BookmarkService.Bookmark bookmark = bookmarkList.getSelectedValue();
        if (bookmark == null) return;

        updatingSelection = true;
        selectFile(bookmark.file());
        JLabel label = labelsByFilename.get(bookmark.file().getName());
        if (label != null) {
            scrollThumbnailToVisible(label);
        }
        updatingSelection = false;
    }

    private void selectFromThumbnail(JLabel label) {
        Object fileObject = label.getClientProperty("file");
        if (!(fileObject instanceof File file)) return;

        updatingSelection = true;
        selectFile(file);
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).file().getName().equals(file.getName())) {
                bookmarkList.setSelectedIndex(i);
                bookmarkList.ensureIndexIsVisible(i);
                break;
            }
        }
        updatingSelection = false;
    }

    private void selectFile(File file) {
        selectedFile = file;
        for (JLabel label : labelsByFilename.values()) {
            label.setBorder(null);
        }

        JLabel selectedLabel = labelsByFilename.get(file.getName());
        if (selectedLabel != null) {
            selectedLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 3));
        }

        syncMainViewSelection(file);
    }

    private void syncMainViewSelection(File file) {
        if (!syncMainViewCheckbox.isSelected() || file == null) return;

        SwingUtilities.invokeLater(() -> Controller.getInstance().getThumbnailPanel().selectFileThumbnail(file));
    }

    private void installThumbnailKeyBindings(JComponent component) {
        registerNavigationKey(component, "LEFT", -1);
        registerNavigationKey(component, "RIGHT", 1);
        registerNavigationKey(component, "UP", -3);
        registerNavigationKey(component, "DOWN", 3);
    }

    private void registerNavigationKey(JComponent component, String keyStroke, int delta) {
        String actionKey = "bookmarkMove" + keyStroke;
        component.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyStroke), actionKey);
        component.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                moveThumbnailSelection(delta);
            }
        });
    }

    private void moveThumbnailSelection(int delta) {
        int index = selectedBookmarkIndex();
        if (index < 0) return;

        int nextIndex = Math.max(0, Math.min(listModel.size() - 1, index + delta));
        if (nextIndex == index) return;

        BookmarkService.Bookmark next = listModel.get(nextIndex);
        JLabel label = labelsByFilename.get(next.file().getName());
        if (label != null) {
            selectFromThumbnail(label);
            scrollThumbnailToVisible(label);
            label.requestFocusInWindow();
        }
    }

    private int selectedBookmarkIndex() {
        if (selectedFile == null) return bookmarkList.getSelectedIndex();

        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).file().getName().equals(selectedFile.getName())) {
                return i;
            }
        }
        return -1;
    }

    private void scrollThumbnailToVisible(JLabel label) {
        Rectangle bounds = label.getBounds();
        bounds.grow(12, 12);
        thumbnailPanel.scrollRectToVisible(bounds);
    }

    private void showThumbnailPopup(JLabel label, MouseEvent e) {
        JMenuItem renameItem = new JMenuItem("Bookmark umbenennen");
        renameItem.addActionListener(a -> {
            selectFromThumbnail(label);
            renameSelectedBookmark();
        });
        JMenuItem deleteItem = new JMenuItem("Bookmark löschen");
        deleteItem.addActionListener(a -> {
            selectFromThumbnail(label);
            removeSelectedBookmark();
        });

        JPopupMenu popup = new JPopupMenu();
        popup.add(renameItem);
        popup.addSeparator();
        popup.add(deleteItem);
        popup.show(label, e.getX(), e.getY());
    }

    private void renameSelectedBookmark() {
        BookmarkService.Bookmark bookmark = selectedBookmark();
        if (bookmark == null) return;

        String newLabel = JOptionPane.showInputDialog(
                this,
                "Neue Bezeichnung für Bookmark:",
                bookmark.label()
        );
        if (newLabel == null || newLabel.trim().isEmpty()) return;

        File file = bookmark.file();
        BookmarkService.getInstance().setBookmark(file, newLabel);
        reloadBookmarks();
        selectBookmarkFile(file);
    }

    private void removeSelectedBookmark() {
        File file = selectedFile;
        if (file == null && bookmarkList.getSelectedValue() != null) {
            file = bookmarkList.getSelectedValue().file();
        }
        if (file == null) return;

        BookmarkService.getInstance().removeBookmark(file);
        reloadBookmarks();
    }

    private BookmarkService.Bookmark selectedBookmark() {
        if (selectedFile != null) {
            for (int i = 0; i < listModel.size(); i++) {
                BookmarkService.Bookmark bookmark = listModel.get(i);
                if (bookmark.file().getName().equals(selectedFile.getName())) {
                    return bookmark;
                }
            }
        }
        return bookmarkList.getSelectedValue();
    }

    private void selectBookmarkFile(File file) {
        if (file == null) return;

        JLabel label = labelsByFilename.get(file.getName());
        if (label != null) {
            selectFromThumbnail(label);
            scrollThumbnailToVisible(label);
        }
    }

    private void closeWithOk() {
        if (closed) return;
        closed = true;
        File file = selectedFile;
        dispose();
        if (closeHandler != null) {
            closeHandler.run();
        }
        if (file != null && okHandler != null) {
            okHandler.accept(file);
        }
    }

    private void closeWithoutOk() {
        if (closed) return;
        closed = true;
        selectedFile = null;
        dispose();
        if (closeHandler != null) {
            closeHandler.run();
        }
    }
}
