import service.Controller;
import ui.ImageViewer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args){
        new ImageViewer();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            Controller.printMemoryUsage();
        }, 0, 3, TimeUnit.SECONDS);
    }
}