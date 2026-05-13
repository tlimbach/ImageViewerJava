package ui;

import event.*;
import model.AppState;
import service.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThumbnailPanel extends JPanel {

    private final static int THUMBNAIL_WIDTH = 420;
    private final static int THUMBNAIL_HEIGHT = (int) (THUMBNAIL_WIDTH * 9.0 / 16);  // ≈ 265
    private final static int PREVIEW_IMAGE_WIDTH = THUMBNAIL_WIDTH;
    private final static int PREVIEW_IMAGE_HEIGHT = THUMBNAIL_HEIGHT;

    private final static int ANIMATION_FRAMES_PER_THUMBNAIL = 50;
    public final static int ANIMATION_DELAY_PLAYBACK = (int) (33 * 2.5);
    private final static int ANIMATION_DELAY_RECORD = 33;
    public static final int N_THREADS = 4;
    private JLabel selectedLabel = null;

    private final JScrollPane scrollPane;

    private final MouseListener mouseListener;

    private volatile File currentHoverFile;

    private JLabel myLabel;
    private static final int INITIAL_VIDEO_THUMBNAIL_FRAMES = 1;
    private static final int PRELOAD_FRAMES_FOR_NEW_VIDEO_THUMBNAIL = 3;
    private static final int PROGRESS_UPDATE_STEP = 25;
    private JPanel pendingRefreshPanel;
    private final Timer thumbnailUiRefreshTimer = new Timer(80, e -> flushThumbnailUiRefresh());
    private final TransferHandler fileDropTransferHandler = createFileDropTransferHandler();
    private static final Pattern HTML_IMAGE_SRC_PATTERN = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);


    public ThumbnailPanel() {


        mouseListener = createMouseListener();
        thumbnailUiRefreshTimer.setRepeats(false);

        setLayout(new BorderLayout());
        scrollPane = new JScrollPane();
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.getViewport().addChangeListener(e -> updateVisibleThumbnails());
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);
        installFileDropHandler(this);
        installFileDropHandler(scrollPane);
        installFileDropHandler(scrollPane.getViewport());

        EventBus.get().register(CurrentDirectoryChangedEvent.class, e -> {
            Controller.getInstance().getExecutorService().submit(() -> {
                List<File> files = MediaService.getInstance().loadFilesFromDirectory();
                populate(files);
            });
        });

        EventBus.get().register(RangeChangedEvent.class, e -> {
            invalidateThumbnails(e.file());

            SwingUtilities.invokeLater(() -> {
                AnimatedThumbnail match = animatedThumbnails.stream().filter(a -> a.filename.equals(e.file().getName())).findFirst().orElse(null);

                if (match != null) {
                    CompletableFuture.supplyAsync(() -> loadThumbnails(e.file(), ANIMATION_FRAMES_PER_THUMBNAIL), Controller.getInstance().getExecutorService()).thenAccept(thumbFiles -> {
                        if (thumbFiles != null && !thumbFiles.isEmpty()) {
                            SwingUtilities.invokeLater(() -> {
                                if (!animatedThumbnails.contains(match)) return;

                                boolean wasRunning = match.isRunning;
                                if (wasRunning) {
                                    match.stop();
                                }

                                match.imageFiles = thumbFiles;

                                if (wasRunning) {
                                    match.start();
                                    match.preload(PRELOAD_FRAMES_FOR_NEW_VIDEO_THUMBNAIL);
                                }
                            });
                        }
                    });
                }
            });
        });

        EventBus.get().register(ImageZoomChangedEvent.class, e -> refreshImageThumbnail(e.file()));
        EventBus.get().register(MediaFileDeletedEvent.class, e -> removeThumbnailForFile(e.file()));

        EventBus.get().register(RotationChangedEvent.class, e -> {
            CompletableFuture.supplyAsync(() -> {
                try {
                    BufferedImage original = ImageIO.read(e.file());
                    if (original == null) return null;
                    BufferedImage rotated = H.rotate(original, e.degrees());
                    return getScaledImagePreserveRatio(rotated, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    return null;
                }
            }, Controller.getInstance().getExecutorService()).thenAccept(scaled -> {
                if (scaled == null) return;
                SwingUtilities.invokeLater(() -> {
                    for (AnimatedThumbnail thumb : animatedThumbnails) {
                        if (thumb.filename.equals(e.file().getName())) {
                            thumb.label.setIcon(new ImageIcon(scaled));
                            break;
                        }
                    }
                });
            });
        });

        EventBus.get().register(UserKeyboardEvent.class, e -> {
            SwingUtilities.invokeLater(() -> handleKeyboardCommand(e.command()));
        });

        EventBus.get().register(MediaviewPlayEvent.class, e -> {
            SwingUtilities.invokeLater(this::selectCurrentFileThumbnail);
        });
    }

    private void handleKeyboardCommand(UserCommand command) {
        if (myLabel == null) return;  // Falls noch nie eins geklickt

        // Aktuellen Index suchen
        int index = -1;
        for (int i = 0; i < animatedThumbnails.size(); i++) {
            if (animatedThumbnails.get(i).label == myLabel) {
                index = i;
                break;
            }
        }

        int nextIndex = index; // Default: unverändert

        switch (command) {
            case RIGHT:
                if (index != -1 && index + 1 < animatedThumbnails.size()) {
                    nextIndex = index + 1;
                }
                break;
            case LEFT:
                if (index > 0) {
                    nextIndex = index - 1;
                }
                break;
            case UP:
                if (index - 3 >= 0) {
                    nextIndex = index - 3;
                }
                break;
            case DOWN:
                if (index + 3 < animatedThumbnails.size()) {
                    nextIndex = index + 3;
                }
                break;
        }


        if (nextIndex != index) {
            JLabel next = animatedThumbnails.get(nextIndex).label;

            if (selectedLabel != null) {
                selectedLabel.setBorder(null);
            }

            selectedLabel = next;
            selectedLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 4));

            File file = (File) next.getClientProperty("file");
            AppState.get().setCurrentFile(file);
            Controller.getInstance().handleMedia(file, false);

            myLabel = selectedLabel;

            Rectangle r = selectedLabel.getBounds();
            Rectangle viewRect = SwingUtilities.convertRectangle(
                    selectedLabel.getParent(), r, scrollPane.getViewport());
            scrollPane.getViewport().scrollRectToVisible(viewRect);


        }
        Rectangle r = myLabel.getBounds();
        Rectangle viewRect = SwingUtilities.convertRectangle(myLabel.getParent(), r, scrollPane.getViewport());
        viewRect.y = Math.max(viewRect.y - 50, 0);
        viewRect.height += 100;
        scrollPane.scrollRectToVisible(viewRect);
    }

    private void selectCurrentFileThumbnail() {
        File current = AppState.get().getCurrentFile();
        if (current == null) return;

        for (AnimatedThumbnail thumb : animatedThumbnails) {
            File thumbFile = (File) thumb.label.getClientProperty("file");
            if (thumbFile != null && thumbFile.equals(current)) {

                if (selectedLabel != null) {
                    selectedLabel.setBorder(null);
                }

                selectedLabel = thumb.label;
                selectedLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 4));
                myLabel = selectedLabel;

                Rectangle r = selectedLabel.getBounds();
                Rectangle viewRect = SwingUtilities.convertRectangle(selectedLabel.getParent(), r, scrollPane.getViewport());
                scrollPane.getViewport().scrollRectToVisible(viewRect);

                break;
            }
        }
    }

    private MouseAdapter createMouseListener() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                JLabel label = (JLabel) e.getSource();
                myLabel = label;
                File file = (File) label.getClientProperty("file");

                if (SwingUtilities.isRightMouseButton(e)) {
                    // Kontextmenü wie gehabt
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem deleteItem = new JMenuItem("Bild löschen");

                    deleteItem.addActionListener(ev -> {
                        int result = JOptionPane.showConfirmDialog(label, "Bild wirklich löschen?\n" + file.getName(), "Löschen bestätigen", JOptionPane.YES_NO_OPTION);

                        if (result == JOptionPane.YES_OPTION) {
                            if (file.delete()) {
                                removeThumbnail(label);
                                EventBus.get().publish(new MediaFileDeletedEvent(file));
                                EventBus.get().publish(new TagsChangedEvent());
                            } else {
                                JOptionPane.showMessageDialog(label, "Datei konnte nicht gelöscht werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    });

                    popup.add(deleteItem);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                    return; // Rechtsklick fertig
                }

                if (selectedLabel != null) {
                    selectedLabel.setBorder(null);
                }
                selectedLabel = label;
                selectedLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 4));

                // Single + Double: Zähler über ClickCount
                if (e.getClickCount() == 1) {
                    H.out("once pressed " + file.getName());
                    if (Controller.isImageFile(file) && MediaView.getInstance().isShowingImage(file)) {
                        MediaView.getInstance().hideFrame();
                        return;
                    }
                    AppState.get().setCurrentFile(file);
                    Controller.getInstance().handleMedia(file, false);
                } else if (e.getClickCount() == 2) {
                    AppState.get().setCurrentFile(file);
                    Controller.getInstance().handleMedia(file, true);
                }
            }
        };
    }

    private void removeThumbnail(JLabel label) {
        int removedIndex = -1;
        AnimatedThumbnail removedThumbnail = null;
        for (int i = 0; i < animatedThumbnails.size(); i++) {
            AnimatedThumbnail thumbnail = animatedThumbnails.get(i);
            if (thumbnail.label == label) {
                removedIndex = i;
                removedThumbnail = thumbnail;
                break;
            }
        }

        if (removedIndex == -1) return;

        removedThumbnail.stop();
        animatedThumbnails.remove(removedIndex);

        Container parent = label.getParent();
        if (parent != null) {
            parent.remove(label);
            parent.revalidate();
            parent.repaint();
        }

        if (selectedLabel == label || myLabel == label) {
            selectedLabel = null;
            myLabel = null;

            if (!animatedThumbnails.isEmpty()) {
                int nextIndex = Math.min(removedIndex, animatedThumbnails.size() - 1);
                JLabel next = animatedThumbnails.get(nextIndex).label;
                selectedLabel = next;
                myLabel = next;
                selectedLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 4));

                File nextFile = (File) next.getClientProperty("file");
                if (nextFile != null) {
                    AppState.get().setCurrentFile(nextFile);
                    Controller.getInstance().handleMedia(nextFile, false);
                }
            }
        }

        updateVisibleThumbnails();
    }

    private void removeThumbnailForFile(File file) {
        if (file == null) return;
        runOnEdt(() -> {
            for (AnimatedThumbnail thumbnail : new ArrayList<>(animatedThumbnails)) {
                if (file.getName().equals(thumbnail.filename)) {
                    removeThumbnail(thumbnail.label);
                    return;
                }
            }
        });
    }

    private List<File> loadImageThumbnail(File imageFile) {
        List<File> result = new ArrayList<>();
        File thumbDir = getThumbnailCacheDir();
        String thumbName = "thumb_" + imageFile.getName() + ".jpg";
        File thumbFile = new File(thumbDir, thumbName);

        if (!thumbFile.exists()) {
            try {
                BufferedImage original;
                if (imageFile.getName().toLowerCase().endsWith(".mpo")) {
                    original = MpoReader.getLeftFrame(imageFile);
                } else {
                    original = ImageIO.read(imageFile);
                }
                if (original == null) return result;

                Image scaled = getScaledImagePreserveRatio(original, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
                BufferedImage resultImg = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = resultImg.createGraphics();
                g2.setColor(Color.DARK_GRAY);
                g2.fillRect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
                g2.drawImage(scaled, 0, 0, null);
                g2.dispose();

                ImageIO.write(resultImg, "jpg", thumbFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        result.add(thumbFile);
        return result;
    }

    private void refreshImageThumbnail(File file) {
        if (file == null) return;

        for (AnimatedThumbnail thumbnail : new ArrayList<>(animatedThumbnails)) {
            if (!file.getName().equals(thumbnail.filename)) continue;
            JLabel label = thumbnail.label;
            if (label == null) continue;

            CompletableFuture
                    .supplyAsync(() -> createImageThumbnailIcon(file), Controller.getInstance().getExecutorService())
                    .thenAccept(icon -> {
                        if (icon == null) return;
                        SwingUtilities.invokeLater(() -> {
                            if (animatedThumbnails.contains(thumbnail)) {
                                label.setIcon(icon);
                            }
                        });
                    });
        }
    }

    public void reloadDirectory() {
        List<File> mediaFiles = MediaService.getInstance().loadFilesFromDirectory();
        populate(mediaFiles);
    }

    void updateVisibleThumbnails() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::updateVisibleThumbnails);
            return;
        }

        Rectangle view = scrollPane.getViewport().getViewRect();
        for (AnimatedThumbnail thumb : new ArrayList<>(animatedThumbnails)) {
            boolean visible = view.intersects(thumb.label.getBounds());
            if (visible && !thumb.isRunning) {
                thumb.start();
            } else if (!visible && thumb.isRunning) {
                thumb.stop();
            }
        }
    }

    public List<AnimatedThumbnail> animatedThumbnails = new ArrayList<>();
    int thumbnailsLoadedCount = 0;
    int totalFramesLoaded = 0;
    int framesFromCache = 0;

    private volatile long currentGenerationId = 0;
    int processed = 0;

    public void populate(List<File> _mediaFiles) {
        if (_mediaFiles == null) {
            runOnEdt(() -> scrollPane.setViewportView(new JPanel(new GridLayout(0, 3, 5, 5))));
            return;
        }

        List<File> sortedMediaFiles = new ArrayList<>(_mediaFiles);
        MediaService.sortByCreationDateDescending(sortedMediaFiles);
        final List<File> mediaFiles = new ArrayList<>(MediaService.applyCurrentMediaLimit(sortedMediaFiles));
        Map<String, Integer> displayOrder = new HashMap<>();
        for (int i = 0; i < mediaFiles.size(); i++) {
            displayOrder.put(mediaFiles.get(i).getName(), i);
        }

        long generation = ++currentGenerationId;

        thumbnailsLoadedCount = 0;
        totalFramesLoaded = 0;
        framesFromCache = 0;

        // Neues GridPanel erzeugen
        JPanel newGridPanel = new JPanel(new GridLayout(0, 3, 5, 5));
        installFileDropHandler(newGridPanel);
        JLabel loadingLabel = new JLabel("Lade Medien ...", SwingConstants.CENTER);
        installFileDropHandler(loadingLabel);
        loadingLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        runOnEdtAndWait(() -> {
            long now = System.currentTimeMillis();
            animatedThumbnails.forEach(AnimatedThumbnail::stop);
            animatedThumbnails.clear();
            selectedLabel = null;
            myLabel = null;
            scrollPane.setViewportView(loadingLabel);
            System.out.println("thumbnail ui reset took " + (System.currentTimeMillis() - now));
        });

        EventBus.get().publish(new ThumbnailsLoadedEvent(0, mediaFiles.size()));
        AtomicInteger processedFiles = new AtomicInteger();

        for (File file : mediaFiles) {
            MEDIA_TYPE type = Controller.isImageFile(file) ? MEDIA_TYPE.IMAGE : Controller.isVideoFile(file) ? MEDIA_TYPE.VIDEO : null;
            if (type == null) {
                publishProgress(processedFiles.incrementAndGet(), mediaFiles.size());
                continue;
            }

            try {
                if (type == MEDIA_TYPE.IMAGE) {
                    if (RangeHandler.getInstance().getTotalLength(file) > 0) {
                        publishProgress(processedFiles.incrementAndGet(), mediaFiles.size());
                        continue;
                    }
                } else {
                    try {
                        int minDuration = AppState.get().getMinimunDuration();
                        int actualDuration = RangeHandler.getInstance().getTotalLength(file);

                        if (actualDuration > 0 && actualDuration < minDuration) {
                            publishProgress(processedFiles.incrementAndGet(), mediaFiles.size());
                            continue;
                        }


                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception es) {
                es.printStackTrace();
            }

            int initialFrameCount = type == MEDIA_TYPE.IMAGE ? ANIMATION_FRAMES_PER_THUMBNAIL : INITIAL_VIDEO_THUMBNAIL_FRAMES;
            CompletableFuture.supplyAsync(() -> type == MEDIA_TYPE.IMAGE ? loadImageThumbnail(file) : loadThumbnails(file, initialFrameCount), Controller.getInstance().getExecutorService()).thenAccept(thumbFiles -> {
                if (generation != currentGenerationId) return;
                int done = processedFiles.incrementAndGet();
                publishProgress(done, mediaFiles.size());
                if (thumbFiles != null && !thumbFiles.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        if (generation != currentGenerationId) return;
                        if (scrollPane.getViewport().getView() == loadingLabel) {
                            scrollPane.setViewportView(newGridPanel);
                        }
                        addThumbnailLabelTo(newGridPanel, type, thumbFiles, file, displayOrder);
                        thumbnailsLoadedCount++;
                    });
                }
            });
        }

    }

    private void installFileDropHandler(JComponent component) {
        component.setTransferHandler(fileDropTransferHandler);
    }

    private TransferHandler createFileDropTransferHandler() {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                boolean canImport = AppState.get().getCurrentDirectory() != null
                        && (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || support.isDataFlavorSupported(DataFlavor.imageFlavor)
                        || support.isDataFlavorSupported(DataFlavor.stringFlavor)
                        || hasTextFlavor(support.getDataFlavors())
                        || hasUrlFlavor(support.getDataFlavors()));

                if (canImport) {
                    logDrop("canImport=true, action=" + support.getDropAction());
                    logTransferFlavors("[ThumbnailPanel DnD]", support.getDataFlavors());
                }

                return canImport;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;

                try {
                    Transferable transferable = support.getTransferable();
                    logDrop("importData gestartet");
                    logTransferFlavors("[ThumbnailPanel DnD]", transferable.getTransferDataFlavors());

                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> droppedFiles = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        List<File> imageFiles = droppedFiles.stream()
                                .filter(File::isFile)
                                .filter(Controller::isImageFile)
                                .toList();

                        logDrop("Lokale Dateien empfangen: " + droppedFiles.size() + ", Bilder: " + imageFiles.size());

                        if (imageFiles.isEmpty()) {
                            JOptionPane.showMessageDialog(thisComponent(), "Keine unterstützten Bilddateien gefunden.", "Drag and Drop", JOptionPane.INFORMATION_MESSAGE);
                            return false;
                        }

                        Controller.getInstance().getExecutorService().submit(() -> copyDroppedImages(imageFiles));
                        return true;
                    }

                    URL url = extractUrl(transferable);
                    if (url != null) {
                        logDrop("URL empfangen: " + url);
                        Controller.getInstance().getExecutorService().submit(() -> downloadDroppedImage(url));
                        return true;
                    }

                    if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                        logDrop("Direktes Browser-Bild empfangen, aber nicht gespeichert: Das waere eine neu kodierte grosse PNG-Datei ohne Originalnamen.");
                        JOptionPane.showMessageDialog(
                                thisComponent(),
                                "Der Browser hat nur ein gerendertes Bild geliefert, keine Originaldatei/URL.\n"
                                        + "Das Bild wurde nicht gespeichert, damit keine grosse neu kodierte PNG-Datei entsteht.",
                                "Drag and Drop",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                        return false;
                    }

                    logDrop("Kein verwertbares Bildformat im Drop gefunden");
                    JOptionPane.showMessageDialog(thisComponent(), "Keine unterstützten Bilddaten gefunden.", "Drag and Drop", JOptionPane.INFORMATION_MESSAGE);
                    return false;
                } catch (UnsupportedFlavorException | IOException e) {
                    showDropError("Dateien konnten nicht gelesen werden.", e);
                    return false;
                }
            }

            private Component thisComponent() {
                return ThumbnailPanel.this;
            }
        };
    }

    private boolean hasUrlFlavor(DataFlavor[] flavors) {
        for (DataFlavor flavor : flavors) {
            if (URL.class.isAssignableFrom(flavor.getRepresentationClass())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTextFlavor(DataFlavor[] flavors) {
        return DataFlavor.selectBestTextFlavor(flavors) != null;
    }

    private URL extractUrl(Transferable transferable) throws UnsupportedFlavorException, IOException {
        for (DataFlavor flavor : transferable.getTransferDataFlavors()) {
            if (URL.class.isAssignableFrom(flavor.getRepresentationClass())) {
                Object data = transferable.getTransferData(flavor);
                if (data instanceof URL url) {
                    return url;
                }
            }
        }

        String text = null;
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
        } else {
            DataFlavor textFlavor = DataFlavor.selectBestTextFlavor(transferable.getTransferDataFlavors());
            if (textFlavor != null) {
                text = readTextFlavor(transferable, textFlavor);
            }
        }

        if (text == null || text.isBlank()) return null;

        logDrop("String-Drop-Inhalt: " + abbreviate(text));

        Matcher imageSrcMatcher = HTML_IMAGE_SRC_PATTERN.matcher(text);
        if (imageSrcMatcher.find()) {
            return toUrl(imageSrcMatcher.group(1));
        }

        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            URL url = toUrl(trimmed);
            if (url != null) {
                return url;
            }
        }

        return null;
    }

    private String readTextFlavor(Transferable transferable, DataFlavor textFlavor) throws UnsupportedFlavorException, IOException {
        StringBuilder text = new StringBuilder();
        try (Reader reader = textFlavor.getReaderForText(transferable)) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                text.append(buffer, 0, read);
            }
        }
        logDrop("Text-Flavor gelesen: " + textFlavor.getMimeType());
        return text.toString();
    }

    private URL toUrl(String text) {
        try {
            URI uri = new URI(text);
            if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                return uri.toURL();
            }
        } catch (URISyntaxException | MalformedURLException e) {
            logDrop("Keine gueltige URL: " + text);
        }
        return null;
    }

    private void copyDroppedImages(List<File> imageFiles) {
        Path targetDirectory = AppState.get().getCurrentDirectory();
        if (targetDirectory == null) return;

        int copied = 0;
        List<String> failed = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();

        for (File source : imageFiles) {
            try {
                Path sourcePath = source.toPath().toAbsolutePath().normalize();
                Path duplicate = DuplicateImageFinder.findExistingDuplicate(targetDirectory, sourcePath).orElse(null);

                if (duplicate != null) {
                    duplicates.add(source.getName() + " ist bereits vorhanden als " + duplicate.getFileName());
                    logDrop("Duplikat nicht kopiert: " + sourcePath + " == " + duplicate);
                    continue;
                }

                Path targetPath = uniqueTargetPath(targetDirectory, source.getName());
                Files.copy(sourcePath, targetPath);
                copied++;
                logDrop("Kopiert: " + sourcePath + " -> " + targetPath);
            } catch (IOException e) {
                logDrop("Kopieren fehlgeschlagen fuer " + source.getAbsolutePath() + ": " + e.getMessage());
                failed.add(source.getName());
            }
        }

        if (copied > 0) {
            reloadDirectory();
        }

        if (!failed.isEmpty()) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    this,
                    "Einige Bilder konnten nicht kopiert werden:\n" + String.join("\n", failed),
                    "Drag and Drop",
                    JOptionPane.ERROR_MESSAGE
            ));
        }

        if (!duplicates.isEmpty()) {
            showDuplicateMessage("Drag and Drop", duplicates);
        }
    }

    private void downloadDroppedImage(URL url) {
        Path targetDirectory = AppState.get().getCurrentDirectory();
        if (targetDirectory == null) return;

        Path tempPath = null;
        try {
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 ImageViewer");
            String contentDisposition = connection.getHeaderField("Content-Disposition");
            String contentType = connection.getContentType();
            String fileName = fileNameFromDownloadMetadata(url, contentDisposition, contentType);
            tempPath = Files.createTempFile(targetDirectory, ".download-", ".tmp");

            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Path duplicate = DuplicateImageFinder.findExistingDuplicate(targetDirectory, tempPath).orElse(null);
            if (duplicate != null) {
                Files.deleteIfExists(tempPath);
                logDrop("URL-Bild ist Duplikat und wurde nicht gespeichert: " + url + " == " + duplicate);
                showDuplicateMessage("Drag and Drop", List.of(fileName + " ist bereits vorhanden als " + duplicate.getFileName()));
                return;
            }

            Path targetPath = uniqueTargetPath(targetDirectory, fileName);
            Files.move(tempPath, targetPath);

            logDrop("Bild von URL gespeichert: " + url
                    + ", contentType=" + contentType
                    + ", contentDisposition=" + contentDisposition
                    + " -> " + targetPath);
            reloadDirectory();
        } catch (IOException e) {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException cleanupException) {
                    logDrop("Temporaere Download-Datei konnte nicht geloescht werden: " + tempPath);
                }
            }
            showDropError("Bild von URL konnte nicht gespeichert werden.", e);
        }
    }

    private void showDuplicateMessage(String title, List<String> duplicates) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                this,
                "Bereits vorhandene Bilder wurden nicht importiert:\n" + String.join("\n", duplicates),
                title,
                JOptionPane.INFORMATION_MESSAGE
        ));
    }

    private String fileNameFromDownloadMetadata(URL url, String contentDisposition, String contentType) {
        String fileName = fileNameFromContentDisposition(contentDisposition);
        if (fileName == null || fileName.isBlank()) {
            fileName = fileNameFromUrl(url);
        }

        if (!Controller.isImageFile(new File(fileName))) {
            fileName = fileName + extensionForContentType(contentType);
        }

        if (!Controller.isImageFile(new File(fileName))) {
            fileName = generatedImageFileName(contentType);
        }

        return fileName;
    }

    private String fileNameFromContentDisposition(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isBlank()) {
            return null;
        }

        Matcher utf8Matcher = Pattern.compile("filename\\*=UTF-8''([^;]+)", Pattern.CASE_INSENSITIVE).matcher(contentDisposition);
        if (utf8Matcher.find()) {
            return sanitizeFileName(URLDecoder.decode(utf8Matcher.group(1).trim(), StandardCharsets.UTF_8));
        }

        Matcher matcher = Pattern.compile("filename=\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE).matcher(contentDisposition);
        if (matcher.find()) {
            return sanitizeFileName(matcher.group(1).trim());
        }

        return null;
    }

    private String fileNameFromUrl(URL url) {
        String path = url.getPath();
        if (path == null || path.isBlank()) {
            return generatedImageFileName(null);
        }

        int lastSlash = path.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
        fileName = sanitizeFileName(fileName);

        if (fileName.isBlank()) {
            return generatedImageFileName(null);
        }
        return fileName;
    }

    private String extensionForContentType(String contentType) {
        if (contentType == null) return "";
        String normalized = contentType.toLowerCase();
        if (normalized.contains("jpeg") || normalized.contains("jpg")) return ".jpg";
        if (normalized.contains("png")) return ".png";
        if (normalized.contains("gif")) return ".gif";
        if (normalized.contains("webp")) return ".webp";
        if (normalized.contains("bmp")) return ".bmp";
        return "";
    }

    private String generatedImageFileName(String contentType) {
        String extension = extensionForContentType(contentType);
        if (extension.isBlank()) {
            extension = ".jpg";
        }
        return UUID.randomUUID().toString().toUpperCase() + extension;
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private Path uniqueTargetPath(Path targetDirectory, String fileName) {
        Path targetPath = targetDirectory.resolve(fileName);
        if (!Files.exists(targetPath)) {
            return targetPath;
        }

        String baseName = fileName;
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        int counter = 1;
        do {
            targetPath = targetDirectory.resolve(baseName + " " + counter + extension);
            counter++;
        } while (Files.exists(targetPath));

        return targetPath;
    }

    private void showDropError(String message, Exception e) {
        e.printStackTrace();
        logDrop(message + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message, "Drag and Drop", JOptionPane.ERROR_MESSAGE));
    }

    private void logTransferFlavors(String prefix, DataFlavor[] flavors) {
        for (DataFlavor flavor : flavors) {
            System.out.println(prefix + " Flavor: mime=" + flavor.getMimeType()
                    + ", human=" + flavor.getHumanPresentableName()
                    + ", class=" + flavor.getRepresentationClass().getName());
        }
    }

    private void logDrop(String message) {
        System.out.println("[ThumbnailPanel DnD] " + message);
    }

    private String abbreviate(String text) {
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= 300) return normalized;
        return normalized.substring(0, 300) + "...";
    }

    private void publishProgress(int loaded, int total) {
        if (loaded == total || loaded % PROGRESS_UPDATE_STEP == 0) {
            EventBus.get().publish(new ThumbnailsLoadedEvent(loaded, total));
        }
    }

    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void runOnEdtAndWait(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(task);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void addThumbnailLabelTo(JPanel panel, MEDIA_TYPE type, List<File> thumbnailFiles, File file, Map<String, Integer> displayOrder) {
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
        label.setOpaque(true);
        label.setBackground(Color.DARK_GRAY);
        label.putClientProperty("file", file);
        label.addMouseListener(mouseListener);

//        in slideshow angezeigtes bild soll in thumbailview angezeigt werden


        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {

                if (type == MEDIA_TYPE.IMAGE) {
                    currentHoverFile = file;

                    Controller.getInstance().getExecutorService().submit(() -> {
                        // Kurze künstliche Verzögerung, optional:
                        H.sleep(50);

                        // Bin ich noch der aktuelle Hover?
                        if (currentHoverFile != file) return;

                        // Aufwendig laden:
                        try {
                            BufferedImage image;
                            if (file.getName().toLowerCase().endsWith(".mpo")) {
                                MpoReader.preloadFrames(file);
//                                List<BufferedImage> images = new JPGExtractor().createBufferdImageFromMpo(file);
//                                double p = ParallaxHandler.getInstance().getParallaxForFile(file);
//                                image = AnaglyphUtils.createSimpleAnaglyphVarianteC(images.get(0), images.get(1), p, 0.8f, 1.0f);
//                                AppState.get().setPreloadedImage(image);
                            } else {
                                File resolved = AppState.get().getFileForCurrentDirectory(file);
                                image = ImageIO.read(resolved);
                                H.out("setting preloaded image " + file.getName());
                                AppState.get().setPreloadedImage(image);
                                AppState.get().setPreloadedImageFile(resolved);
                            }

                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }

                    });
                }
            }
        });

        boolean imagedOK = true;

        if (type == MEDIA_TYPE.IMAGE) {
            int rotation = RotationHandler.getInstance().getRotation(file);
            ThumbnailZoomMode mode = Controller.getInstance().getControlPanel().getThumbnailZoomMode();
            boolean needsDynamicThumbnail = rotation != 0
                    || (mode != ThumbnailZoomMode.STANDARD && ImageZoomHandler.getInstance().getZoomForFile(file) != null);
            if (!needsDynamicThumbnail && !thumbnailFiles.isEmpty()) {
                label.setIcon(new ImageIcon(thumbnailFiles.get(0).getAbsolutePath()));
            } else {
                CompletableFuture.runAsync(() -> {
                    ImageIcon icon = createImageThumbnailIcon(file);
                    if (icon != null) {
                        SwingUtilities.invokeLater(() -> label.setIcon(icon));
                    }
                }, Controller.getInstance().getExecutorService());
            }
        } else {
            Image image = Toolkit.getDefaultToolkit().getImage(thumbnailFiles.get(0).getAbsolutePath());
            label.setIcon(new ImageIcon(image));
        }

        if (imagedOK) {
            AnimatedThumbnail aNail = new AnimatedThumbnail();
            aNail.imageFiles = thumbnailFiles;
            aNail.animationTimer = null;
            aNail.label = label;
            aNail.isRunning = false;
            aNail.type = type;
            aNail.filename = file.getName();

            int insertAt = findThumbnailInsertIndex(file, displayOrder);
            panel.add(label, insertAt);
            animatedThumbnails.add(insertAt, aNail);
            requestThumbnailUiRefresh(panel);

            if (animatedThumbnails.size() < 20) {
                aNail.start();
                aNail.preload(PRELOAD_FRAMES_FOR_NEW_VIDEO_THUMBNAIL);
            }

            if (type == MEDIA_TYPE.VIDEO) {
                loadRemainingVideoFramesAsync(aNail, file);
            }
        }
    }

    private int findThumbnailInsertIndex(File file, Map<String, Integer> displayOrder) {
        int fileOrder = displayOrder.getOrDefault(file.getName(), Integer.MAX_VALUE);
        for (int i = 0; i < animatedThumbnails.size(); i++) {
            int existingOrder = displayOrder.getOrDefault(animatedThumbnails.get(i).filename, Integer.MAX_VALUE);
            if (existingOrder > fileOrder) {
                return i;
            }
        }
        return animatedThumbnails.size();
    }

    private void requestThumbnailUiRefresh(JPanel panel) {
        pendingRefreshPanel = panel;
        if (!thumbnailUiRefreshTimer.isRunning()) {
            thumbnailUiRefreshTimer.start();
        }
    }

    private void flushThumbnailUiRefresh() {
        JPanel panel = pendingRefreshPanel;
        if (panel == null) return;

        panel.revalidate();
        panel.repaint();
        updateVisibleThumbnails();
    }

    private void loadRemainingVideoFramesAsync(AnimatedThumbnail thumbnail, File file) {
        CompletableFuture
                .supplyAsync(() -> loadThumbnails(file, ANIMATION_FRAMES_PER_THUMBNAIL), Controller.getInstance().getExecutorService())
                .thenAccept(thumbFiles -> {
                    if (thumbFiles == null || thumbFiles.isEmpty()) return;

                    SwingUtilities.invokeLater(() -> {
                        if (!animatedThumbnails.contains(thumbnail)) return;

                        boolean wasRunning = thumbnail.isRunning;
                        if (wasRunning) {
                            thumbnail.stop();
                        }

                        thumbnail.imageFiles = thumbFiles;

                        if (wasRunning) {
                            thumbnail.start();
                            thumbnail.preload(PRELOAD_FRAMES_FOR_NEW_VIDEO_THUMBNAIL);
                        }
                    });
                });
    }

    public void invalidateThumbnails(File videoFile) {
        File[] cachedFiles = getThumbnailCacheDir().listFiles((dir, name) -> name.endsWith(videoFile.getName() + ".jpg"));
        if (cachedFiles != null) {
            for (File f : cachedFiles) {
                f.delete();
            }
        }
    }

    private ImageIcon createImageThumbnailIcon(File file) {
        try {
            File resolved = AppState.get().getFileForCurrentDirectory(file);
            BufferedImage original;

            if (file.getName().toLowerCase().endsWith(".mpo")) {
                original = MpoReader.getLeftFrame(resolved);
            } else {
                original = ImageIO.read(resolved);
            }

            if (original == null) {
                H.out("Problems remain " + file.getAbsoluteFile().toPath());
                return null;
            }

            int rotation = RotationHandler.getInstance().getRotation(file);
            BufferedImage rotated = H.rotate(original, rotation);
            ThumbnailZoomMode mode = Controller.getInstance().getControlPanel().getThumbnailZoomMode();
            BufferedImage rendered = renderImageThumbnail(rotated, file, mode);
            return new ImageIcon(rendered);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private BufferedImage renderImageThumbnail(BufferedImage image, File file, ThumbnailZoomMode mode) {
        ImageZoomHandler.ZoomSelection zoom = ImageZoomHandler.getInstance().getZoomForFile(file);
        if (mode == ThumbnailZoomMode.ZOOMED && zoom != null) {
            return renderZoomedThumbnail(image, zoom);
        }

        BufferedImage standard = renderStandardThumbnail(image);
        if (mode == ThumbnailZoomMode.GRAYED_OUT && zoom != null) {
            paintGrayedOutZoomMask(standard, image, zoom);
        }
        return standard;
    }

    private BufferedImage renderStandardThumbnail(BufferedImage image) {
        BufferedImage result = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        applyThumbnailRenderingHints(g);
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);

        Rectangle2D bounds = getStandardRenderedImageBounds(image);
        g.drawImage(
                image,
                (int) Math.round(bounds.getX()),
                (int) Math.round(bounds.getY()),
                (int) Math.round(bounds.getWidth()),
                (int) Math.round(bounds.getHeight()),
                null
        );
        g.dispose();
        return result;
    }

    private BufferedImage renderZoomedThumbnail(BufferedImage image, ImageZoomHandler.ZoomSelection zoom) {
        BufferedImage result = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        applyThumbnailRenderingHints(g);
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);

        Rectangle2D viewRect = getZoomViewRect(image, zoom);
        double scale = Math.max(THUMBNAIL_WIDTH / viewRect.getWidth(), THUMBNAIL_HEIGHT / viewRect.getHeight());
        int width = (int) Math.round(image.getWidth() * scale);
        int height = (int) Math.round(image.getHeight() * scale);
        int x = (int) Math.round(-viewRect.getX() * scale);
        int y = (int) Math.round(-viewRect.getY() * scale);

        g.drawImage(image, x, y, width, height, null);
        paintZoomMarker(g);
        g.dispose();
        return result;
    }

    private void paintGrayedOutZoomMask(BufferedImage thumbnail, BufferedImage image, ImageZoomHandler.ZoomSelection zoom) {
        Graphics2D g = thumbnail.createGraphics();
        applyThumbnailRenderingHints(g);

        Rectangle2D imageBounds = getStandardRenderedImageBounds(image);
        Rectangle2D viewRect = getZoomViewRect(image, zoom);
        double scaleX = imageBounds.getWidth() / image.getWidth();
        double scaleY = imageBounds.getHeight() / image.getHeight();

        Rectangle2D visibleRect = new Rectangle2D.Double(
                imageBounds.getX() + viewRect.getX() * scaleX,
                imageBounds.getY() + viewRect.getY() * scaleY,
                viewRect.getWidth() * scaleX,
                viewRect.getHeight() * scaleY
        );

        Area outside = new Area(new Rectangle(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
        outside.subtract(new Area(visibleRect));
        g.setColor(new Color(0, 0, 0, 150));
        g.fill(outside);
        g.setColor(new Color(255, 255, 255, 180));
        g.setStroke(new BasicStroke(2f));
        g.draw(visibleRect);
        g.dispose();
    }

    private Rectangle2D getStandardRenderedImageBounds(BufferedImage image) {
        double widthRatio = (double) THUMBNAIL_WIDTH / image.getWidth();
        double heightRatio = (double) THUMBNAIL_HEIGHT / image.getHeight();
        double scale = Math.max(widthRatio, heightRatio);

        double width = image.getWidth() * scale;
        double height = image.getHeight() * scale;
        double x = (THUMBNAIL_WIDTH - width) / 2.0;
        double overflowY = Math.max(0, height - THUMBNAIL_HEIGHT);
        double y = -(overflowY / 3.0);

        return new Rectangle2D.Double(x, y, width, height);
    }

    private Rectangle2D getZoomViewRect(BufferedImage image, ImageZoomHandler.ZoomSelection zoom) {
        double selectedX = clamp(zoom.x(), 0, 1) * image.getWidth();
        double selectedY = clamp(zoom.y(), 0, 1) * image.getHeight();
        double selectedWidth = clamp(zoom.width(), 0, 1) * image.getWidth();
        double selectedHeight = clamp(zoom.height(), 0, 1) * image.getHeight();

        if (selectedWidth <= 0 || selectedHeight <= 0) {
            return new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight());
        }

        double viewportAspect = (double) THUMBNAIL_WIDTH / THUMBNAIL_HEIGHT;
        double selectedAspect = selectedWidth / selectedHeight;
        double viewWidth = selectedWidth;
        double viewHeight = selectedHeight;

        if (selectedAspect < viewportAspect) {
            viewWidth = selectedHeight * viewportAspect;
        } else {
            viewHeight = selectedWidth / viewportAspect;
        }

        viewWidth = Math.min(viewWidth, image.getWidth());
        viewHeight = Math.min(viewHeight, image.getHeight());

        double centerX = selectedX + selectedWidth / 2.0;
        double centerY = selectedY + selectedHeight / 2.0;
        double x = clamp(centerX - viewWidth / 2.0, 0, image.getWidth() - viewWidth);
        double y = clamp(centerY - viewHeight / 2.0, 0, image.getHeight() - viewHeight);

        return new Rectangle2D.Double(x, y, viewWidth, viewHeight);
    }

    private void paintZoomMarker(Graphics2D g) {
        int size = 18;
        Polygon marker = new Polygon(
                new int[]{THUMBNAIL_WIDTH, THUMBNAIL_WIDTH, THUMBNAIL_WIDTH - size},
                new int[]{THUMBNAIL_HEIGHT, THUMBNAIL_HEIGHT - size, THUMBNAIL_HEIGHT},
                3
        );
        g.setColor(new Color(255, 221, 0, 230));
        g.fillPolygon(marker);
    }

    private void applyThumbnailRenderingHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Image getScaledImagePreserveRatio(Image srcImg, int maxWidth, int maxHeight) {
        int srcWidth = srcImg.getWidth(null);
        int srcHeight = srcImg.getHeight(null);

        if (srcWidth <= 0 || srcHeight <= 0) return srcImg;

        double widthRatio = (double) maxWidth / srcWidth;
        double heightRatio = (double) maxHeight / srcHeight;
        double scale = Math.max(widthRatio, heightRatio);

        int newWidth = (int) Math.round(srcWidth * scale);
        int newHeight = (int) Math.round(srcHeight * scale);
        int x = (maxWidth - newWidth) / 2;
        int overflowY = Math.max(0, newHeight - maxHeight);
        int y = -(overflowY / 3);

        BufferedImage result = new BufferedImage(maxWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, maxWidth, maxHeight);
        g.drawImage(srcImg, x, y, newWidth, newHeight, null);
        g.dispose();

        return result;
    }

    private List<File> loadThumbnails(File videoFile, int count) {
        int millis = 0;
        RangeHandler.Range range = RangeHandler.getInstance().getRangeForFile(videoFile);

        if (range != null) {
            millis = (int) (range.start * 1000);
        }

        List<File> files = new ArrayList<>();
        for (int t = 0; t < count; t++) {
            File thumb = fetchVideoThumbnail(videoFile, millis);
            if (thumb != null) {
                files.add(thumb);
            }
            millis += ANIMATION_DELAY_RECORD;
        }
        return files;
    }

    private File fetchVideoThumbnail(File videoFile, int milli) {
        if (!getThumbnailCacheDir().exists()) {
            getThumbnailCacheDir().mkdirs();
        }

        String nameInCache = milli + "_" + videoFile.getName() + ".jpg";
        totalFramesLoaded++;
        File file = new File(getThumbnailCacheDir(), nameInCache);
        if (file.exists()) {
            framesFromCache++;
            return file;
        }

        file = extractVideoThumbnail(videoFile, milli);
        return file;
    }

    private File getThumbnailCacheDir() {
        Path currentDir = AppState.get().getCurrentDirectory();
        if (currentDir == null) return new File("thumbnails"); // Fallback für Notfall
        Path thumbsDir = currentDir.resolve("thumbnails");
        if (!Files.exists(thumbsDir)) {
            try {
                Files.createDirectories(thumbsDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return thumbsDir.toFile();
    }

    private File extractVideoThumbnail(File videoFile, int milli) {
        try {
            String nameInCache = milli + "_" + videoFile.getName() + ".jpg";
            File file = new File(getThumbnailCacheDir(), nameInCache);

            int totalSeconds = milli / 1000;
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int seconds = totalSeconds % 60;
            int ms = milli % 1000;
            String timestamp = String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);

            String[] cmd = {"ffmpeg", "-y", "-loglevel", "error", "-ss", timestamp, "-i", videoFile.getAbsolutePath(), "-vframes", "1", "-vf", "scale='min(" + PREVIEW_IMAGE_WIDTH + "\\,iw)':min(" + PREVIEW_IMAGE_HEIGHT + "\\,ih):force_original_aspect_ratio=decrease,pad=" + PREVIEW_IMAGE_WIDTH + ":" + PREVIEW_IMAGE_HEIGHT + ":(ow-iw)/2:(oh-ih)/2", file.getAbsolutePath()};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.start().waitFor();

            return file.exists() ? file : null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
