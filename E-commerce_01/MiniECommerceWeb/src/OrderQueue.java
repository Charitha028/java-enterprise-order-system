import java.util.LinkedList;
import java.util.Queue;

/**
 * Hand-written blocking queue built with synchronized + wait()/notifyAll().
 * Used for the Order, Payment and Shipping queues (Producer-Consumer pattern).
 *  - put()  : producer adds an order and wakes up waiting consumers.
 *  - take() : consumer waits (WAITING state) until work arrives, or returns null
 *             once the queue is closed AND empty.
 *  - close(): called when no more work will ever arrive (graceful shutdown).
 */
public class OrderQueue {
    private final Queue<Order> queue = new LinkedList<>();
    private final String name;
    private boolean closed = false;

    public OrderQueue(String name) { this.name = name; }

    public synchronized void put(Order order) {
        if (closed) throw new IllegalStateException(name + " is closed");
        queue.add(order);
        notifyAll();
    }

    public synchronized Order take() throws InterruptedException {
        while (queue.isEmpty() && !closed) {
            wait(); // releases the lock and sleeps until notifyAll()
        }
        return queue.poll(); // null => closed and drained
    }

    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    public synchronized int size() { return queue.size(); }
}
