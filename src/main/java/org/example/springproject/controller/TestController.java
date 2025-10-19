package org.example.springproject.controller;

import org.example.springproject.transaction.DistributedTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private DataSource dataSource;
    private final JdbcTemplate inventoryJdbcTemplate;
    private final JdbcTemplate orderJdbcTemplate;
    private final DistributedTransaction transactionManager;
    // @Autowired
    // private CustomerRepository customerRepository;

    public TestController(
            @Qualifier("inventoryJdbcTemplate") JdbcTemplate inventoryJdbcTemplate,
            @Qualifier("orderJdbcTemplate") JdbcTemplate orderJdbcTemplate,
            DistributedTransaction transactionManager) {
        this.inventoryJdbcTemplate = inventoryJdbcTemplate;
        this.orderJdbcTemplate = orderJdbcTemplate;
        this.transactionManager = transactionManager;
    }

    @GetMapping("/connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> response = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            response.put("status", "success");
            response.put("connected", true);
            response.put("database", connection.getCatalog());
            response.put("url", connection.getMetaData().getURL());
            response.put("message", "Database connection successful!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("connected", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Application is running");
        return ResponseEntity.ok(response);
    }

    /**
     * Get all products
     */
    @GetMapping("/products")
    public ResponseEntity<List<Map<String, Object>>> getAllProducts() {
        try {
            List<Map<String, Object>> products = inventoryJdbcTemplate.queryForList(
                    "SELECT product_id, name, category, price, stock, version FROM products"
            );
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Get all orders
     */
    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> getAllOrders() {
        try {
            List<Map<String, Object>> orders = orderJdbcTemplate.queryForList(
                    "SELECT order_id, customer_id, product_id, quantity, total_amount, status, version FROM orders"
            );
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Get all customers
     */
    @GetMapping("/customers")
    public ResponseEntity<List<Map<String, Object>>> getAllCustomers() {
        try {
            List<Map<String, Object>> customers = inventoryJdbcTemplate.queryForList(
                    "SELECT customer_id, name, email, phone FROM customers"
            );
            return ResponseEntity.ok(customers);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Get all payments
     */
    @GetMapping("/payments")
    public ResponseEntity<List<Map<String, Object>>> getAllPayments() {
        try {
            List<Map<String, Object>> payments = orderJdbcTemplate.queryForList(
                    "SELECT payment_id, order_id, amount, payment_method, status FROM payments"
            );
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Get active transactions
     */
    @GetMapping("/active-transactions")
    public ResponseEntity<Map<String, Object>> getActiveTransactions() {
        try {
            return ResponseEntity.ok(Map.of(
                    "activeTransactions", transactionManager.getActiveTransactions(),
                    "count", transactionManager.getActiveTransactions().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get transaction logs from inventory database
     */
    @GetMapping("/logs/inventory")
    public ResponseEntity<List<Map<String, Object>>> getInventoryLogs() {
        try {
            List<Map<String, Object>> logs = inventoryJdbcTemplate.queryForList(
                    "SELECT log_id, transaction_id, operation_type, table_name, timestamp " +
                            "FROM transaction_log_inventory ORDER BY timestamp DESC LIMIT 20"
            );
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Get transaction logs from order database
     */
    @GetMapping("/logs/order")
    public ResponseEntity<List<Map<String, Object>>> getOrderLogs() {
        try {
            List<Map<String, Object>> logs = orderJdbcTemplate.queryForList(
                    "SELECT log_id, transaction_id, operation_type, table_name, timestamp " +
                            "FROM transaction_log_order ORDER BY timestamp DESC LIMIT 20"
            );
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetDatabase() {
        try {
            // Delete all orders and payments
            orderJdbcTemplate.update("DELETE FROM payments");
            orderJdbcTemplate.update("DELETE FROM orders");
            orderJdbcTemplate.update("DELETE FROM transaction_log_order");

            // Reset product stock to initial values
            inventoryJdbcTemplate.update("UPDATE products SET stock = 100, version = 0 WHERE product_id = 1");
            inventoryJdbcTemplate.update("UPDATE products SET stock = 50, version = 0 WHERE product_id = 2");
            inventoryJdbcTemplate.update("UPDATE products SET stock = 30, version = 0 WHERE product_id = 3");
            inventoryJdbcTemplate.update("UPDATE products SET stock = 20, version = 0 WHERE product_id = 4");

            inventoryJdbcTemplate.update("DELETE FROM inventory_transactions");
            inventoryJdbcTemplate.update("DELETE FROM transaction_log_inventory");

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Database reset successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }
}
