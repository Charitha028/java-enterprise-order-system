import java.util.Locale;
import java.util.Random;

/** Payment thread. Simulated gateway fails ~20% of the time; failure releases the stock. */
public class PaymentProcessor implements Runnable {
    private static final double FAILURE_RATE = 0.20;

    private final Inventory inventory;
    private final OrderQueue paymentQueue;
    private final OrderQueue shippingQueue;
    private final LogQueue log;
    private final Random random = new Random();

    public PaymentProcessor(Inventory inventory, OrderQueue paymentQueue,
                            OrderQueue shippingQueue, LogQueue log) {
        this.inventory = inventory;
        this.paymentQueue = paymentQueue;
        this.shippingQueue = shippingQueue;
        this.log = log;
    }

    @Override
    public void run() {
        try {
            Order order;
            while ((order = paymentQueue.take()) != null) {
                Thread.sleep(400 + random.nextInt(500)); // gateway latency

                if (random.nextDouble() >= FAILURE_RATE) {
                    order.setStatus(Order.Status.PAYMENT_SUCCESS);
                    log.log(String.format(Locale.ROOT, "Payment successful for Order-%d (%.2f)",
                            order.getOrderId(), order.getTotal()));
                    shippingQueue.put(order);
                } else {
                    order.setStatus(Order.Status.PAYMENT_FAILED);
                    inventory.release(order.getProduct(), order.getQuantity());
                    log.log("Payment FAILED for Order-" + order.getOrderId()
                            + " -> stock released (" + order.getProduct().getName()
                            + " x" + order.getQuantity() + ")");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            shippingQueue.close();
        }
    }
}
