import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns every shared resource and every thread of the order-processing system. */
public class OrderPipeline {
    public final Inventory inventory = new Inventory();
    public final OrderQueue orderQueue = new OrderQueue("OrderQueue");
    public final OrderQueue paymentQueue = new OrderQueue("PaymentQueue");
    public final OrderQueue shippingQueue = new OrderQueue("ShippingQueue");
    public final LogQueue logQueue = new LogQueue();
    public final OrderStore store = new OrderStore();
    public final OrderService orderService = new OrderService(inventory, orderQueue, logQueue, store);
    public final LoggerService loggerService;

    private final Thread inventoryThread, paymentThread, shippingThread, loggerThread;
    private final ThreadPoolExecutor customerPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
    private final AtomicInteger customerSeq = new AtomicInteger();
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public OrderPipeline(String logFile) {
        inventory.addProduct(new Product(1, "Laptop", 55000.0, 20));
        inventory.addProduct(new Product(2, "Mouse", 500.0, 50));
        inventory.addProduct(new Product(3, "Keyboard", 1500.0, 30));

        loggerService = new LoggerService(logQueue, logFile);
        loggerThread    = new Thread(loggerService, "Logger-Thread");
        inventoryThread = new Thread(new InventoryManager(inventory, orderQueue, paymentQueue, logQueue), "Inventory-Thread");
        paymentThread   = new Thread(new PaymentProcessor(inventory, paymentQueue, shippingQueue, logQueue), "Payment-Thread");
        shippingThread  = new Thread(new ShippingService(shippingQueue, logQueue), "Shipping-Thread");
    }

    public void start() {
        loggerThread.start();
        inventoryThread.start();
        paymentThread.start();
        shippingThread.start();
        logQueue.log("System started");
    }

    /** Submit simulated customers to the ExecutorService. */
    public void simulate(int customers, int ordersEach) {
        for (int i = 0; i < customers; i++) {
            String name = "Customer-" + customerSeq.incrementAndGet();
            customerPool.submit(new Customer(name, ordersEach, inventory, orderService));
        }
    }

    public int activeCustomers() { return customerPool.getActiveCount() + customerPool.getQueue().size(); }

    /** key -> {threadName, state} for the dashboard. */
    public Map<String, String[]> threadStates() {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("inventory", new String[]{inventoryThread.getName(), inventoryThread.getState().name()});
        m.put("payment",   new String[]{paymentThread.getName(), paymentThread.getState().name()});
        m.put("shipping",  new String[]{shippingThread.getName(), shippingThread.getState().name()});
        m.put("logger",    new String[]{loggerThread.getName(), loggerThread.getState().name()});
        return m;
    }

    /** Graceful shutdown in pipeline order: finish pending orders, then stop every thread. */
    public void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        try {
            customerPool.shutdownNow();
            customerPool.awaitTermination(5, TimeUnit.SECONDS);
            orderQueue.close();
            inventoryThread.join();
            paymentThread.join();
            shippingThread.join();
            logQueue.log("System stopped");
            logQueue.close();
            loggerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
