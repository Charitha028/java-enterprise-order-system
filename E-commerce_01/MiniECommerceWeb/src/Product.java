/** A product sold in the store. Stock is only changed through Inventory (synchronized). */
public class Product {
    private final int id;
    private final String name;
    private final double price;
    private int stock; // guarded by Inventory's lock

    public Product(int id, String name, double price, int stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }

    // Package-private: only Inventory touches stock, inside synchronized methods.
    int getStock() { return stock; }
    void setStock(int stock) { this.stock = stock; }
}
