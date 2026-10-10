import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Logger thread: the only thread that touches ecommerce.log. It also keeps the most
 * recent lines in memory so the web dashboard can show a live activity log.
 */
public class LoggerService implements Runnable {
    private static final int KEEP = 200;

    private final LogQueue logQueue;
    private final String fileName;
    private final LinkedList<String> recent = new LinkedList<>();

    public LoggerService(LogQueue logQueue, String fileName) {
        this.logQueue = logQueue;
        this.fileName = fileName;
    }

    /** Oldest first. */
    public List<String> getRecent(int n) {
        synchronized (recent) {
            int from = Math.max(0, recent.size() - n);
            return new ArrayList<>(recent.subList(from, recent.size()));
        }
    }

    @Override
    public void run() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, false))) {
            String line;
            while ((line = logQueue.take()) != null) {
                System.out.println(line);
                writer.write(line);
                writer.newLine();
                writer.flush(); // keep the file up to date while the server runs
                synchronized (recent) {
                    recent.add(line);
                    if (recent.size() > KEEP) recent.removeFirst();
                }
            }
        } catch (IOException e) {
            System.err.println("Logger failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
