package ui;

import event.CurrentDirectoryChangedEvent;
import event.MediaFileDeletedEvent;
import model.AppState;
import service.AppIcon;
import service.Controller;
import service.DuplicateImageFinder;
import service.EventBus;
import service.SettingsService;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ImageViewer {

    ControlPanel controlPanel;
    ThumbnailPanel thumbnailPanel;
    MediaView mediaView;

    Controller controller = Controller.getInstance();
    private final ScheduledExecutorService directoryMonitorExecutor = Executors.newSingleThreadScheduledExecutor();
    private Map<String, MediaFileState> lastMediaSnapshot = new HashMap<>();
    private long lastDetectedDirectoryChangeMillis = 0;
    private boolean directoryReloadPending = false;
    private final List<Path> desktopDirectories = findDesktopDirectories();
    private final Map<Path, Map<String, MediaFileState>> lastDesktopSnapshots = new HashMap<>();
    private final Map<Path, MediaFileState> pendingDesktopImports = new LinkedHashMap<>();
    private long lastDetectedDesktopChangeMillis = 0;
    private boolean desktopImportPending = false;

    public ImageViewer() {

        Path def = SettingsService.getIntance().loadDefaultDirectoryFromSettingsJson();
        AppState.get().setCurrentDirectory(def);
        AppState.get().setCurrentFile(SettingsService.getIntance().loadLastSelectedFileForDirectory(def));

        thumbnailPanel = new ThumbnailPanel();
        mediaView = MediaView.getInstance();
        controlPanel = new ControlPanel();

        controller.setControlPanel(controlPanel);
        controller.setThumbnailPanel(thumbnailPanel);
        controller.setMediaPanel(mediaView);

        JFrame frame = new JFrame("Image Viewer");
        AppIcon.applyTo(frame);
        frame.setLayout(new BorderLayout());
        frame.add(controlPanel, BorderLayout.WEST);
        frame.add(thumbnailPanel, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        // Zweiten Monitor finden (sofern vorhanden)
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        GraphicsDevice targetDevice = devices.length > 1 ? devices[1] : devices[0];

        Rectangle bounds = targetDevice.getDefaultConfiguration().getBounds();

        // Fenster dorthin verschieben und maximieren
        frame.setLocation(bounds.x, bounds.y);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        frame.setVisible(true);

        EventBus.get().register(CurrentDirectoryChangedEvent.class, c->{
            frame.setTitle("Image Viewer - " + AppState.get().getCurrentDirectory());
            lastMediaSnapshot = createMediaSnapshot();
            directoryReloadPending = false;
        });

        EventBus.get().register(MediaFileDeletedEvent.class, e -> {
            lastMediaSnapshot = createMediaSnapshot();
            directoryReloadPending = false;
        });

        frame.setTitle("Image Viewer - " + AppState.get().getCurrentDirectory());

//        Controller.getInstance().getExecutorService().submit(()->thumbnailPanel.reloadDirectory());

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            thumbnailPanel.reloadDirectory(); // Achtung: darf hier kein UI-Zugriff enthalten sein!
            lastMediaSnapshot = createMediaSnapshot();
            initializeDesktopSnapshots();
        }, 500, TimeUnit.MILLISECONDS);

        directoryMonitorExecutor.scheduleAtFixedRate(this::watchCurrentDirectory, 2, 1, TimeUnit.SECONDS);
        directoryMonitorExecutor.scheduleAtFixedRate(this::watchDesktopInbox, 2, 1, TimeUnit.SECONDS);
    }

    private void watchCurrentDirectory() {
        try {
            Map<String, MediaFileState> currentSnapshot = createMediaSnapshot();
            if (!currentSnapshot.equals(lastMediaSnapshot)) {
                lastMediaSnapshot = currentSnapshot;
                lastDetectedDirectoryChangeMillis = System.currentTimeMillis();
                directoryReloadPending = true;
                System.out.println("[DirectoryMonitor] Änderung im Medienverzeichnis erkannt");
                return;
            }

            if (directoryReloadPending && System.currentTimeMillis() - lastDetectedDirectoryChangeMillis > 1000) {
                directoryReloadPending = false;
                System.out.println("[DirectoryMonitor] Medienverzeichnis stabil, lade Thumbnails neu");
                thumbnailPanel.reloadDirectory();
            }
        } catch (Exception e) {
            System.err.println("[DirectoryMonitor] Fehler: " + e.getMessage());
        }
    }

    private Map<String, MediaFileState> createMediaSnapshot() {
        Path currentDirectory = AppState.get().getCurrentDirectory();
        if (currentDirectory == null) return new HashMap<>();

        return createMediaSnapshot(currentDirectory);
    }

    private Map<String, MediaFileState> createMediaSnapshot(Path directory) {
        File[] files = directory.toFile().listFiles();
        if (files == null) {
            System.err.println("[DesktopInbox] Ordner kann nicht gelesen werden: " + directory.toAbsolutePath());
            return new HashMap<>();
        }

        Map<String, MediaFileState> snapshot = new HashMap<>();
        for (File file : files) {
            if (file.isFile() && (Controller.isImageFile(file) || Controller.isVideoFile(file))) {
                snapshot.put(file.getName(), new MediaFileState(file.length(), file.lastModified()));
            }
        }
        return snapshot;
    }

    private void watchDesktopInbox() {
        try {
            if (desktopDirectories.isEmpty()) return;

            boolean changed = false;
            for (Path desktopDirectory : desktopDirectories) {
                Map<String, MediaFileState> previousSnapshot = lastDesktopSnapshots.getOrDefault(desktopDirectory, new HashMap<>());
                Map<String, MediaFileState> currentSnapshot = createMediaSnapshot(desktopDirectory);
                if (!currentSnapshot.equals(previousSnapshot)) {
                    Map<Path, MediaFileState> newDesktopImages = findNewDesktopImages(desktopDirectory, previousSnapshot, currentSnapshot);
                    pendingDesktopImports.putAll(newDesktopImages);
                    lastDesktopSnapshots.put(desktopDirectory, currentSnapshot);
                    changed = true;
                    if (!newDesktopImages.isEmpty()) {
                        System.out.println("[DesktopInbox] Neue Desktop-Bilder gefunden: " + newDesktopImages.size()
                                + " in " + desktopDirectory.toAbsolutePath());
                    }
                }
            }

            if (changed) {
                lastDetectedDesktopChangeMillis = System.currentTimeMillis();
                desktopImportPending = true;
                System.out.println("[DesktopInbox] Änderung auf Desktop-Pfad erkannt");
            }

            if (desktopImportPending && System.currentTimeMillis() - lastDetectedDesktopChangeMillis > 1000) {
                desktopImportPending = false;
                importPendingDesktopImages();
            }
        } catch (Exception e) {
            System.err.println("[DesktopInbox] Fehler: " + e.getMessage());
        }
    }

    private Map<Path, MediaFileState> findNewDesktopImages(Path desktopDirectory, Map<String, MediaFileState> previousSnapshot, Map<String, MediaFileState> currentSnapshot) {
        Map<Path, MediaFileState> result = new LinkedHashMap<>();
        for (Map.Entry<String, MediaFileState> entry : currentSnapshot.entrySet()) {
            String fileName = entry.getKey();
            Path source = desktopDirectory.resolve(fileName);
            if (Controller.isImageFile(source.toFile()) && !previousSnapshot.containsKey(fileName)) {
                result.put(source, entry.getValue());
            }
        }
        return result;
    }

    private void importPendingDesktopImages() {
        Path targetDirectory = AppState.get().getCurrentDirectory();
        if (targetDirectory == null) return;

        Path normalizedTarget = targetDirectory.toAbsolutePath().normalize();
        if (desktopDirectories.stream().anyMatch(desktopDirectory -> desktopDirectory.toAbsolutePath().normalize().equals(normalizedTarget))) {
            pendingDesktopImports.clear();
            return;
        }

        int copied = 0;
        File firstCopiedFile = null;
        List<String> duplicates = new ArrayList<>();
        for (Path source : new LinkedHashMap<>(pendingDesktopImports).keySet()) {
            if (!Files.isRegularFile(source) || !Controller.isImageFile(source.toFile())) continue;

            try {
                Path duplicate = DuplicateImageFinder.findExistingDuplicate(targetDirectory, source).orElse(null);
                if (duplicate != null) {
                    duplicates.add(source.getFileName() + " ist bereits vorhanden als " + duplicate.getFileName());
                    Files.delete(source);
                    System.out.println("[DesktopInbox] Duplikat nicht importiert und vom Desktop geloescht: " + source + " == " + duplicate);
                    continue;
                }

                Path target = uniqueTargetPath(targetDirectory, source.getFileName().toString());
                Files.copy(source, target);
                Files.delete(source);
                if (firstCopiedFile == null) {
                    firstCopiedFile = target.toFile();
                }
                copied++;
                System.out.println("[DesktopInbox] Desktop-Bild kopiert und vom Desktop geloescht: " + source + " -> " + target);
            } catch (IOException e) {
                System.err.println("[DesktopInbox] Import fehlgeschlagen fuer " + source + ": " + e.getMessage());
            }
        }

        pendingDesktopImports.clear();

        if (copied > 0) {
            lastMediaSnapshot = createMediaSnapshot();
            thumbnailPanel.reloadDirectoryAndSelect(firstCopiedFile);
        }

        if (!duplicates.isEmpty()) {
            showDesktopDuplicateMessage(duplicates);
        }
    }

    private void showDesktopDuplicateMessage(List<String> duplicates) {
        SwingUtilities.invokeLater(() -> showAutoClosingMessage(
                "Desktop-Import",
                "Bereits vorhandene Bilder wurden nicht importiert und vom Desktop geloescht:\n" + String.join("\n", duplicates)
        ));
    }

    private void showAutoClosingMessage(String title, String text) {
        JDialog dialog = new JDialog((Window) null, title, Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JTextArea message = new JTextArea(text);
        message.setEditable(false);
        message.setFocusable(false);
        message.setOpaque(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setColumns(48);

        JPanel content = new JPanel(new BorderLayout(12, 0));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        content.add(new JLabel(UIManager.getIcon("OptionPane.informationIcon")), BorderLayout.WEST);
        content.add(message, BorderLayout.CENTER);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);

        Thread closeThread = new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            SwingUtilities.invokeLater(dialog::dispose);
        }, "desktop-duplicate-message-auto-close");
        closeThread.setDaemon(true);
        closeThread.start();
    }

    private List<Path> findDesktopDirectories() {
        List<Path> candidates = new ArrayList<>();
        String home = System.getProperty("user.home");
        candidates.add(Path.of(home, "Library", "CloudStorage", "iCloud Drive", "Desktop"));
        candidates.add(Path.of(home, "Library", "CloudStorage", "iCloud Drive", "Schreibtisch"));
        candidates.add(Path.of(home, "Library", "Mobile Documents", "com~apple~CloudDocs", "Desktop"));
        candidates.add(Path.of(home, "Library", "Mobile Documents", "com~apple~CloudDocs", "Schreibtisch"));
        candidates.add(Path.of(home, "iCloud Drive", "Desktop"));
        candidates.add(Path.of(home, "iCloud Drive", "Schreibtisch"));
        candidates.add(Path.of(home, "Desktop"));
        candidates.add(FileSystemView.getFileSystemView().getHomeDirectory().toPath().resolve("Desktop"));
        candidates.add(FileSystemView.getFileSystemView().getHomeDirectory().toPath().resolve("Schreibtisch"));

        Map<Path, Path> existingDirectoriesByRealPath = new LinkedHashMap<>();
        boolean foundUnreadableDesktopDirectory = false;
        for (Path candidate : candidates) {
            try {
                if (!Files.isDirectory(candidate)) {
                    System.out.println("[DesktopInbox] Desktop-Kandidat nicht gefunden: " + candidate.toAbsolutePath());
                    continue;
                }
                if (!canReadDirectoryEntries(candidate)) {
                    foundUnreadableDesktopDirectory = true;
                    System.err.println("[DesktopInbox] Desktop-Kandidat nicht lesbar: " + candidate.toAbsolutePath());
                    continue;
                }
                Path realPath = candidate.toRealPath();
                existingDirectoriesByRealPath.putIfAbsent(realPath, candidate);
                System.out.println("[DesktopInbox] Desktop-Kandidat gefunden: " + candidate.toAbsolutePath()
                        + " -> " + realPath);
            } catch (IOException e) {
                System.err.println("[DesktopInbox] Desktop-Kandidat nicht nutzbar: " + candidate + " (" + e.getMessage() + ")");
            }
        }

        List<Path> result = new ArrayList<>(existingDirectoriesByRealPath.values());
        if (result.isEmpty()) {
            System.err.println("[DesktopInbox] Kein Desktop-Verzeichnis gefunden");
            if (foundUnreadableDesktopDirectory) {
                System.err.println("[DesktopInbox] macOS verweigert der Java-App den Zugriff auf den Schreibtisch. "
                        + "Erlaube der startenden App den Zugriff unter Systemeinstellungen > Datenschutz & Sicherheit > Dateien und Ordner oder Festplattenvollzugriff.");
            }
        }
        return result;
    }

    private boolean canReadDirectoryEntries(Path directory) {
        return directory.toFile().listFiles() != null;
    }

    private void initializeDesktopSnapshots() {
        lastDesktopSnapshots.clear();
        for (Path desktopDirectory : desktopDirectories) {
            Map<String, MediaFileState> snapshot = createMediaSnapshot(desktopDirectory);
            lastDesktopSnapshots.put(desktopDirectory, snapshot);
            System.out.println("[DesktopInbox] Beobachte Desktop-Pfad: " + desktopDirectory.toAbsolutePath()
                    + " (" + snapshot.size() + " Medien, " + countDirectoryEntries(desktopDirectory) + " Eintraege)");
        }
    }

    private int countDirectoryEntries(Path directory) {
        File[] files = directory.toFile().listFiles();
        return files == null ? -1 : files.length;
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

    private static class MediaFileState {
        final long size;
        final long lastModified;

        MediaFileState(long size, long lastModified) {
            this.size = size;
            this.lastModified = lastModified;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MediaFileState that)) return false;
            return size == that.size && lastModified == that.lastModified;
        }

        @Override
        public int hashCode() {
            return Objects.hash(size, lastModified);
        }
    }
}
