# Mini E-Commerce Order Processing: Full-Stack Version (Project 01)

A Java multithreading project with a real web frontend.

* **Backend:** Core Java. Four pipeline threads plus an ExecutorService customer pool,
  exposed through a REST API (JDK built-in `HttpServer`, no libraries to download).
* **Frontend:** HTML, CSS and JavaScript dashboard served by the same Java program.

## Run it

**Windows:** double-click `run.bat`  (or run it from a terminal inside this folder)

**Mac / Linux:** `./run.sh`

**Any OS, by hand** (from inside this folder, the one that contains `src` and `web`):

    mkdir out
    javac -d out src/*.java        (on Windows: javac -d out src\*.java)
    java -cp out Main

Then open **http://localhost:8080**. Use another port with `java -cp out Main 9090`.

**VS Code:** install the "Extension Pack for Java", open THIS folder (File > Open Folder),
open `src/Main.java` and press the Run button. Then browse to http://localhost:8080.
Stop with Ctrl+C in the terminal; the shutdown hook drains the pipeline and flushes `ecommerce.log`.

Requires JDK 17 or newer.

## Project structure

    MiniECommerceWeb
    ├── src
    │   ├── Product, Order, Inventory         model + shared resource (synchronized)
    │   ├── OrderQueue, LogQueue              wait() / notifyAll() blocking queues
    │   ├── Customer                          producer, runs in an ExecutorService pool
    │   ├── InventoryManager                  thread 1: reserve stock
    │   ├── PaymentProcessor                  thread 2: charge, release stock on failure
    │   ├── ShippingService                   thread 3: ship
    │   ├── LoggerService                     thread 4: writes ecommerce.log
    │   ├── OrderStore, OrderService          thread-safe order list, single entry point
    │   ├── OrderPipeline                     owns all queues and threads, graceful stop
    │   ├── ApiServer, Json                   REST API + static files
    │   └── Main
    ├── web  (index.html, style.css, app.js)
    └── run.bat / run.sh

## REST API

| Method | Path | Body | Purpose |
|---|---|---|---|
| GET  | /api/state    |  | Inventory, queues, thread states, stats, latest orders, log |
| POST | /api/orders   | `{"customerName":"Asha","product":"Laptop","quantity":2}` | Place an order |
| POST | /api/simulate | `{"customers":5,"ordersPerCustomer":4}` | Start simulated customers |
| POST | /api/restock  | `{"product":"Laptop","quantity":10}` | Add stock |

## Threading concepts and where to find them

| Concept | File |
|---|---|
| Thread, Runnable, join() | OrderPipeline, Main |
| ExecutorService / thread pool | OrderPipeline (customers), ApiServer (requests) |
| synchronized, critical section, race condition | Inventory.reserve() |
| wait() / notifyAll(), Producer-Consumer | OrderQueue, LogQueue |
| Thread states (WAITING, TIMED_WAITING...) | shown live on the dashboard |
| Thread-safe counters and collections | Order (AtomicInteger), OrderStore |
| File handling | LoggerService |
| Graceful shutdown | OrderPipeline.stop() |

## Moving to Spring Boot later

The threading engine (`OrderPipeline`, queues, workers) does not depend on the web layer.
In Spring Boot, `ApiServer` becomes a `@RestController`, `OrderPipeline` a `@Service` bean,
and `web/` moves to `src/main/resources/static`.
