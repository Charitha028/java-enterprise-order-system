import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * REST API + static file server using the JDK's built-in HttpServer.
 *
 *   GET  /api/state     everything the dashboard needs
 *   POST /api/orders    {"customerName":"Asha","product":"Laptop","quantity":2}
 *   POST /api/simulate  {"customers":5,"ordersPerCustomer":4}
 *   POST /api/restock   {"product":"Laptop","quantity":10}
 *   GET  /              the frontend (files in the web/ folder)
 */
public class ApiServer {
    private interface Handler { void handle(HttpExchange ex) throws Exception; }

    private final OrderPipeline pipeline;
    private final Path webRoot;
    private final HttpServer server;

    public ApiServer(OrderPipeline pipeline, Path webRoot, int port) throws IOException {
        this.pipeline = pipeline;
        this.webRoot = webRoot.toAbsolutePath().normalize();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8)); // request-handling thread pool

        server.createContext("/api/state", wrap("GET", ex -> sendJson(ex, 200, stateJson())));
        server.createContext("/api/orders", wrap("POST", this::handlePlaceOrder));
        server.createContext("/api/simulate", wrap("POST", this::handleSimulate));
        server.createContext("/api/restock", wrap("POST", this::handleRestock));
        server.createContext("/", this::serveStatic);
    }

    public void start() { server.start(); }
    public void stop() { server.stop(0); }

    // ---------- API handlers ----------

    private void handlePlaceOrder(HttpExchange ex) throws Exception {
        String body = readBody(ex);
        Order o = pipeline.orderService.placeOrder(
                Json.getString(body, "customerName"),
                Json.getString(body, "product"),
                Json.getInt(body, "quantity", 1));
        sendJson(ex, 201, "{\"orderId\":" + o.getOrderId() + "}");
    }

    private void handleSimulate(HttpExchange ex) throws Exception {
        String body = readBody(ex);
        int customers = Json.getInt(body, "customers", 5);
        int each = Json.getInt(body, "ordersPerCustomer", 4);
        if (customers < 1 || customers > 10 || each < 1 || each > 10)
            throw new IllegalArgumentException("Use 1-10 customers and 1-10 orders each.");
        pipeline.simulate(customers, each);
        sendJson(ex, 202, "{\"started\":" + customers + "}");
    }

    private void handleRestock(HttpExchange ex) throws Exception {
        String body = readBody(ex);
        Product p = pipeline.inventory.getProduct(Json.getString(body, "product"));
        int qty = Json.getInt(body, "quantity", 0);
        if (p == null) throw new IllegalArgumentException("Unknown product.");
        if (qty < 1 || qty > 1000) throw new IllegalArgumentException("Restock between 1 and 1000.");
        pipeline.inventory.release(p, qty);
        pipeline.logQueue.log("Restocked " + p.getName() + " +" + qty);
        sendJson(ex, 200, "{\"ok\":true}");
    }

    // ---------- State snapshot ----------

    private String stateJson() {
        StringBuilder sb = new StringBuilder("{\"inventory\":[");
        boolean first = true;
        for (Product p : pipeline.inventory.getAllProducts()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":").append(Json.quote(p.getName()))
              .append(",\"price\":").append(String.format(Locale.ROOT, "%.2f", p.getPrice()))
              .append(",\"stock\":").append(pipeline.inventory.getStock(p)).append('}');
        }
        sb.append("],\"queues\":{\"order\":").append(pipeline.orderQueue.size())
          .append(",\"payment\":").append(pipeline.paymentQueue.size())
          .append(",\"shipping\":").append(pipeline.shippingQueue.size()).append('}');

        sb.append(",\"customersActive\":").append(pipeline.activeCustomers());

        sb.append(",\"threads\":{");
        first = true;
        for (Map.Entry<String, String[]> e : pipeline.threadStates().entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(Json.quote(e.getKey())).append(":{\"name\":").append(Json.quote(e.getValue()[0]))
              .append(",\"state\":").append(Json.quote(e.getValue()[1])).append('}');
        }
        sb.append('}');

        OrderStore.Stats s = pipeline.store.stats();
        sb.append(",\"stats\":{\"total\":").append(s.total())
          .append(",\"inProgress\":").append(s.inProgress())
          .append(",\"shipped\":").append(s.shipped())
          .append(",\"paymentFailed\":").append(s.paymentFailed())
          .append(",\"rejected\":").append(s.rejected())
          .append(",\"revenue\":").append(String.format(Locale.ROOT, "%.2f", s.revenue())).append('}');

        sb.append(",\"orders\":[");
        first = true;
        for (Order o : pipeline.store.latest(25)) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"id\":").append(o.getOrderId())
              .append(",\"customer\":").append(Json.quote(o.getCustomerName()))
              .append(",\"product\":").append(Json.quote(o.getProduct().getName()))
              .append(",\"quantity\":").append(o.getQuantity())
              .append(",\"total\":").append(String.format(Locale.ROOT, "%.2f", o.getTotal()))
              .append(",\"status\":").append(Json.quote(o.getStatus().name()))
              .append(",\"time\":").append(Json.quote(o.getCreatedAt())).append('}');
        }
        sb.append("],\"logs\":[");
        first = true;
        List<String> logs = pipeline.loggerService.getRecent(40);
        for (String line : logs) {
            if (!first) sb.append(',');
            first = false;
            sb.append(Json.quote(line));
        }
        return sb.append("]}").toString();
    }

    // ---------- Plumbing ----------

    private HttpHandler wrap(String method, Handler inner) {
        return ex -> {
            try {
                if (!ex.getRequestMethod().equalsIgnoreCase(method)) {
                    sendJson(ex, 405, "{\"error\":\"Use " + method + " for this endpoint.\"}");
                    return;
                }
                inner.handle(ex);
            } catch (IllegalArgumentException e) {
                trySend(ex, 400, e.getMessage());
            } catch (IllegalStateException | RejectedExecutionException e) {
                trySend(ex, 503, "The system is shutting down.");
            } catch (Exception e) {
                e.printStackTrace();
                trySend(ex, 500, "Something went wrong on the server.");
            } finally {
                ex.close();
            }
        };
    }

    private void trySend(HttpExchange ex, int code, String message) {
        try { sendJson(ex, code, "{\"error\":" + Json.quote(message) + "}"); } catch (IOException ignored) { }
    }

    private String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readNBytes(10_000), StandardCharsets.UTF_8);
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        send(ex, code, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange ex, int code, String type, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void serveStatic(HttpExchange ex) throws IOException {
        try (ex) {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            Path file = webRoot.resolve(path.substring(1)).normalize();
            if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) { // blocks ../ path traversal
                send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            send(ex, 200, contentType(file.toString()), Files.readAllBytes(file));
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}
