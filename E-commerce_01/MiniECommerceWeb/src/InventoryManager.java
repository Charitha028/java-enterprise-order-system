import java.util.Random;

/** Inventory thread: consumes orderQueue, reserves stock, feeds paymentQueue. */
public class InventoryManager implements Runnable {
    private final Inventory inventory;
    private final OrderQueue orderQueue;
    private final OrderQueue paymentQueue;
    private final LogQueue log;
    private final Random random = new Random();

    public InventoryManager(Inventory inventory, OrderQueue orderQueue,
                            OrderQueue paymentQueue, LogQueue log) {
        this.inventory = inventory;
        this.orderQueue = orderQueue;
        this.paymentQueue = paymentQueue;
        this.log = log;
    }

    @Override
    public void run() {
        try {
            Order order;
            while ((order = orderQueue.take()) != null) {
                Thread.sleep(150 + random.nextInt(150)); // simulated stock-check time
                Product p = order.getProduct();
                if (inventory.reserve(p, order.getQuantity())) {
                    order.setStatus(Order.Status.INVENTORY_RESERVED);
                    log.log("Inventory reserved " + p.getName() + " x" + order.getQuantity()
                            + " for Order-" + order.getOrderId());
                    paymentQueue.put(order);
                } else {
                    order.setStatus(Order.Status.REJECTED_OUT_OF_STOCK);
                    log.log("Order-" + order.getOrderId() + " REJECTED: not enough "
                            + p.getName() + " in stock (wanted " + order.getQuantity() + ")");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            paymentQueue.close();
        }
    }
}
