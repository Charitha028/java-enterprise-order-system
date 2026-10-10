import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.Queue;

/** Shared queue of log lines. Every thread produces; the Logger thread consumes. */
public class LogQueue {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Queue<String> queue = new LinkedList<>();
    private boolean closed = false;

    /** Timestamp is taken when the event happens, not when it is written to disk. */
    public synchronized void log(String message) {
        if (closed) return;
        queue.add(LocalTime.now().format(FMT) + " " + message);
        notifyAll();
    }

    public synchronized String take() throws InterruptedException {
        while (queue.isEmpty() && !closed) {
            wait();
        }
        return queue.poll();
    }

    public synchronized void close() {
        closed = true;
        notifyAll();
    }
}
