import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/** An order placed by a customer. Status is volatile because several threads read/update it. */
public class Order {

    public enum Status {
        PLACED, INVENTORY_RESERVED, REJECTED_OUT_OF_STOCK,
        PAYMENT_SUCCESS, PAYMENT_FAILED, SHIPPED
    }

    private static final AtomicInteger IDS = new AtomicInteger(100); // thread-safe id generator
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int orderId;
    private final String customerName;
    private final Product product;
    private final int quantity;
    private final String createdAt = LocalTime.now().format(FMT);
    private volatile Status status = Status.PLACED;

    public Order(String customerName, Product product, int quantity) {
        this.orderId = IDS.incrementAndGet();
        this.customerName = customerName;
        this.product = product;
        this.quantity = quantity;
    }

    public int getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public String getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public double getTotal() { return product.getPrice() * quantity; }
}
