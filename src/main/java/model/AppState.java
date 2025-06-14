package model;

import lombok.Data;

import java.io.File;
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

}
