import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared resource. Every method that reads or writes stock is synchronized, so the
 * check-then-update in reserve() is one atomic critical section (no race condition,
 * no overselling).
 */
public class Inventory {
    private final Map<String, Product> products = new LinkedHashMap<>();

    public synchronized void addProduct(Product p) { products.put(p.getName(), p); }

    public synchronized Product getProduct(String name) { return products.get(name); }

    public synchronized List<Product> getAllProducts() { return new ArrayList<>(products.values()); }

    public synchronized int getStock(Product p) { return p.getStock(); }

    /** Critical section: check stock and reserve atomically. */
    public synchronized boolean reserve(Product p, int qty) {
        if (p.getStock() >= qty) {
            p.setStock(p.getStock() - qty);
            return true;
        }
        return false;
    }

    /** Put stock back (payment failed) or add new stock (restock). */
    public synchronized void release(Product p, int qty) { p.setStock(p.getStock() + qty); }
}
