import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Starts the order pipeline threads and the web server. Run from the project folder. */
public class Main {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        Path webRoot = Path.of("web");
        if (!Files.isDirectory(webRoot)) webRoot = Path.of("..", "web");
        if (!Files.isDirectory(webRoot)) {
            System.err.println("Cannot find the 'web' folder. Run this from the MiniECommerceWeb folder.");
            return;
        }

        OrderPipeline pipeline = new OrderPipeline("ecommerce.log");
        pipeline.start();

        ApiServer server = new ApiServer(pipeline, webRoot, port);
        server.start();

        // Ctrl+C: stop accepting requests, then drain the pipeline and flush the log file
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            pipeline.stop();
        }, "Shutdown-Hook"));

        String url = "http://localhost:" + port;
        System.out.println("Server running at " + url + "  (press Ctrl+C to stop)");
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {
            // browser auto-open is optional
        }
    }
}
