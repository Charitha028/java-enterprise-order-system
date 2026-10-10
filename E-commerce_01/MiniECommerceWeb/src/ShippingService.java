import java.util.Random;

/** Shipping thread: PAYMENT_SUCCESS -> SHIPPED. */
public class ShippingService implements Runnable {
    private final OrderQueue shippingQueue;
    private final LogQueue log;
    private final Random random = new Random();

    public ShippingService(OrderQueue shippingQueue, LogQueue log) {
        this.shippingQueue = shippingQueue;
        this.log = log;
    }

    @Override
    public void run() {
        try {
            Order order;
            while ((order = shippingQueue.take()) != null) {
                Thread.sleep(300 + random.nextInt(400)); // packing time
                order.setStatus(Order.Status.SHIPPED);
                log.log("Order-" + order.getOrderId() + " shipped to " + order.getCustomerName());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
