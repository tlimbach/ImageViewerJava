package model;

import lombok.Data;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Data
public class AppState {
   private static AppState appState = new AppState();

   public static AppState get() {
       return appState;
   }

   private Path currentDirectory;

   private File currentFile;

   private boolean isIgnoreTimerange;

   private boolean autoOpenTagsDialog;

   private boolean isMediaviewFullscreen;

   private BufferedImage preloadedImage;

   public Path getSettingsDirectory() {
      Path currentDir = getCurrentDirectory();
      if (currentDir == null) return null;

      Path settingsDir = currentDir.resolve("settings");
      if (!Files.exists(settingsDir)) {
         try {
            Files.createDirectories(settingsDir);
         } catch (IOException e) {
            e.printStackTrace(); // oder Logging
         }
      }
      return settingsDir;
   }

   public File getFileForCurrentDirectory(File file) {
      Path currentDir = AppState.get().getCurrentDirectory();
      File actualFile = currentDir.resolve(file.getName()).toFile();
      return actualFile;
   }
}
