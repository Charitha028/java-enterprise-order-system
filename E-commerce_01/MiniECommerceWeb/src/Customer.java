import java.util.List;
import java.util.Random;

/** Simulated customer (Producer). Runs inside the customer ExecutorService pool. */
public class Customer implements Runnable {
    private final String name;
    private final int ordersToPlace;
    private final Inventory inventory;
    private final OrderService orderService;
    private final Random random = new Random();

    public Customer(String name, int ordersToPlace, Inventory inventory, OrderService orderService) {
        this.name = name;
        this.ordersToPlace = ordersToPlace;
        this.inventory = inventory;
        this.orderService = orderService;
    }

    @Override
    public void run() {
        List<Product> products = inventory.getAllProducts();
        try {
            for (int i = 0; i < ordersToPlace; i++) {
                Product p = products.get(random.nextInt(products.size()));
                orderService.placeOrder(name, p.getName(), 1 + random.nextInt(4));
                Thread.sleep(150 + random.nextInt(350)); // think time
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IllegalStateException e) {
            // pipeline is shutting down; stop quietly
        }
    }
}
