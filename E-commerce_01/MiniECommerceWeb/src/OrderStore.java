import java.util.ArrayList;
import java.util.List;

/** Thread-safe list of every order ever placed. Read by the REST API, written by many threads. */
public class OrderStore {
    public record Stats(int total, int shipped, int rejected, int paymentFailed, double revenue) {
        public int inProgress() { return total - shipped - rejected - paymentFailed; }
    }

    private final List<Order> orders = new ArrayList<>();

    public synchronized void add(Order o) { orders.add(o); }

    /** Newest first. */
    public synchronized List<Order> latest(int n) {
        List<Order> out = new ArrayList<>();
        for (int i = orders.size() - 1; i >= 0 && out.size() < n; i--) out.add(orders.get(i));
        return out;
    }

    public synchronized Stats stats() {
        int shipped = 0, rejected = 0, failed = 0;
        double revenue = 0;
        for (Order o : orders) {
            switch (o.getStatus()) {
                case SHIPPED -> { shipped++; revenue += o.getTotal(); }
                case REJECTED_OUT_OF_STOCK -> rejected++;
                case PAYMENT_FAILED -> failed++;
                default -> { }
            }
        }
        return new Stats(orders.size(), shipped, rejected, failed, revenue);
    }
}
