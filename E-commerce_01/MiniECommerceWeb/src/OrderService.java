/**
 * The single entry point for placing an order, used by both the REST API (a real
 * customer typing in the browser) and by simulated Customer threads.
 */
public class OrderService {
    private final Inventory inventory;
    private final OrderQueue orderQueue;
    private final LogQueue log;
    private final OrderStore store;

    public OrderService(Inventory inventory, OrderQueue orderQueue, LogQueue log, OrderStore store) {
        this.inventory = inventory;
        this.orderQueue = orderQueue;
        this.log = log;
        this.store = store;
    }

    public Order placeOrder(String customerName, String productName, int quantity) {
        if (customerName == null || customerName.isBlank())
            throw new IllegalArgumentException("Enter a customer name.");
        if (quantity < 1 || quantity > 50)
            throw new IllegalArgumentException("Quantity must be between 1 and 50.");
        Product product = inventory.getProduct(productName);
        if (product == null)
            throw new IllegalArgumentException("Unknown product: " + productName);

        Order order = new Order(customerName.trim(), product, quantity);
        store.add(order);
        log.log(order.getCustomerName() + " placed Order-" + order.getOrderId()
                + " (" + product.getName() + " x" + quantity + ")");
        orderQueue.put(order); // notifyAll() inside wakes the Inventory thread
        return order;
    }
}
