package org.example.springproject.service;

import org.example.springproject.transaction.DistributedTransaction;
import org.springframework.stereotype.Service;
import org.example.springproject.exceptions.DeadlockException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;

@Service
public class StoreService {
    private final DistributedTransaction transactionManager;
    private static final int MAX_RETRIES = 3;

    public StoreService(DistributedTransaction transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * place an order (distributed transaction across both databases)
     * - Check product availability (SELECT from inventory DB)
     * - Update product stock (UPDATE in inventory DB)
     * - Create order (INSERT in order DB)
     * - Create payment (INSERT in order DB)
     */
    public void placeOrder(Integer customerId, Integer productId, Integer quantity) {
        int retries = 0;

        while (retries < MAX_RETRIES) {
            String transactionId = transactionManager.beginTransaction();
            try {
                List<Map<String, Object>> products = transactionManager.executeSelect(
                        transactionId,
                        "SELECT product_id, name, price, stock, version FROM products WHERE product_id = ?",
                        new ProductRowMapper(),
                        "products",
                        productId
                );

                if (products.isEmpty()) {
                    throw new RuntimeException("Product not found");
                }

                Map<String, Object> product = products.get(0);
                Integer currentStock = (Integer) product.get("stock");
                Double currentPrice = (Double) product.get("price");
                Integer currentVersion = (Integer) product.get("version");

                if (currentStock < quantity) {
                    throw new RuntimeException("Product not found");
                }

                Map<String, Object> oldProductData = new HashMap<>(product);

                int updated = transactionManager.executeUpdate(
                        transactionId,
                        "UPDATE products SET stock=stock-?, version=version+1 WHERE product_id=? AND version=?",
                        "products",
                        "product_id",
                        productId,
                        oldProductData,
                        quantity, productId, currentVersion
                );

                if (updated == 0) {
                    throw new RuntimeException("Product modified");
                }

                Integer totalAmount = (int) (currentPrice * quantity);

                Map<String, Object> orderData = new HashMap<>();
                orderData.put("customer_id", customerId);
                orderData.put("product_id", productId);
                orderData.put("quantity", quantity);
                orderData.put("total_amount", totalAmount);
                orderData.put("status", "PENDING");
                orderData.put("created_at", LocalDateTime.now());
                orderData.put("version", 0);

                transactionManager.executeInsert(
                        transactionId,
                        "INSERT INTO orders (customer_id, product_id, quantity, total_amount, status, created_at, version) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        "orders",
                        orderData,
                        customerId, productId, quantity, totalAmount, "PENDING", LocalDateTime.now(), 0
                );

                Map<String, Object> paymentData = new HashMap<>();
                paymentData.put("order_id", 1);
                paymentData.put("amount", totalAmount);
                paymentData.put("payment_method", "CREDIT_CARD");
                paymentData.put("status", "PENDING");
                paymentData.put("processed_at", LocalDateTime.now());

                transactionManager.executeInsert(
                        transactionId,
                        "INSERT INTO payments (order_id, amount, payment_method, status, processed_at) " +
                                "VALUES (?, ?, ?, ?, ?)",
                        "payments",
                        paymentData,
                        1, totalAmount, "CREDIT_CARD", "PENDING", LocalDateTime.now()
                );

                transactionManager.commit(transactionId);
                System.out.println("Order placed successfully!");
                return;
            } catch (DeadlockException e) {
                System.err.println("Deadlock detected, rolling back... (Retry " + (retries + 1) + "/" + MAX_RETRIES + ")");
                try {
                    transactionManager.rollback(transactionId);
                } catch (Exception rollbackEx) {
                    System.err.println("Rollback failed: " + rollbackEx.getMessage());
                }
                retries++;

                try {
                    Thread.sleep((long) Math.pow(2, retries) * 100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Transaction interrupted", ie);
                }

            } catch (Exception e) {
                System.err.println("Transaction failed: " + e.getMessage());
                try {
                    transactionManager.rollback(transactionId);
                } catch (Exception rollbackEx) {
                    System.err.println("Rollback failed: " + rollbackEx.getMessage());
                }
                throw new RuntimeException("Failed to place order", e);
            }
        }
        throw new RuntimeException("Failed to place order");
    }

    /**
     * restock inventory from supplier
     * - Insert inventory transaction (INSERT in inventory DB)
     * - Update product stock (UPDATE in inventory DB)
     */
    public void restockFromSupplier(Integer productId, Integer quantity, String supplierAddress) {
        int retries = 0;

        while (retries < MAX_RETRIES) {
            String transactionId = transactionManager.beginTransaction();

            try {
                Map<String, Object> inventoryTxData = new HashMap<>();
                inventoryTxData.put("product_id", productId);
                inventoryTxData.put("quantity_change", quantity);
                inventoryTxData.put("supplier_address", supplierAddress);
                inventoryTxData.put("timestamp", LocalDateTime.now());

                transactionManager.executeInsert(
                        transactionId,
                        "INSERT INTO inventory_transactions (product_id, quantity_change, supplier_address, timestamp) " +
                                "VALUES (?, ?, ?, ?)",
                        "inventory_transactions",
                        inventoryTxData,
                        productId, quantity, supplierAddress, LocalDateTime.now()
                );

                List<Map<String, Object>> products = transactionManager.executeSelect(
                        transactionId,
                        "SELECT product_id, stock, version FROM products WHERE product_id = ?",
                        new ProductRowMapper(),
                        "products",
                        productId
                );

                if (products.isEmpty()) {
                    throw new RuntimeException("Product not found: " + productId);
                }

                Map<String, Object> oldProductData = products.get(0);
                Integer version = (Integer) oldProductData.get("version");

                int updated = transactionManager.executeUpdate(
                        transactionId,
                        "UPDATE products SET stock = stock + ?, version = version + 1 WHERE product_id = ? AND version = ?",
                        "products",
                        "product_id",
                        productId,
                        oldProductData,
                        quantity, productId, version
                );

                if (updated == 0) {
                    throw new RuntimeException("Optimistic lock failure - product was modified");
                }

                transactionManager.commit(transactionId);
                System.out.println("Inventory restocked successfully!");
                return;

            } catch (Exception e) {
                System.err.println("Restock failed: " + e.getMessage());
                try {
                    transactionManager.rollback(transactionId);
                } catch (Exception rollbackEx) {
                    System.err.println("Rollback failed: " + rollbackEx.getMessage());
                }
                throw new RuntimeException("Failed to restock inventory", e);
            }
        }

        throw new RuntimeException("Failed to restock after " + MAX_RETRIES + " retries");
    }

    /**
     * cancel order and refund
     * - Update order status (UPDATE in order DB)
     * - Update payment status (UPDATE in order DB)
     * - Restore product stock (UPDATE in inventory DB)
     */
    public void cancelOrder(Integer orderId) {
        int retries = 0;

        while (retries < MAX_RETRIES) {
            String txId = transactionManager.beginTransaction();

            try {
                List<Map<String, Object>> orders = transactionManager.executeSelect(
                        txId,
                        "SELECT order_id, customer_id, product_id, quantity, status, version FROM orders WHERE order_id = ?",
                        new OrderRowMapper(),
                        "orders",
                        orderId
                );

                if (orders.isEmpty()) {
                    throw new RuntimeException("Order not found: " + orderId);
                }

                Map<String, Object> order = orders.get(0);
                String status = (String) order.get("status");

                if ("CANCELLED".equals(status)) {
                    throw new RuntimeException("Order already cancelled");
                }

                Integer productId = (Integer) order.get("product_id");
                Integer quantity = (Integer) order.get("quantity");
                Integer orderVersion = (Integer) order.get("version");

                Map<String, Object> oldOrderData = new HashMap<>(order);

                transactionManager.executeUpdate(
                        txId,
                        "UPDATE orders SET status = ?, version = version + 1 WHERE order_id = ? AND version = ?",
                        "orders",
                        "order_id",
                        orderId,
                        oldOrderData,
                        "CANCELLED", orderId, orderVersion
                );

                List<Map<String, Object>> payments = transactionManager.executeSelect(
                        txId,
                        "SELECT payment_id, status FROM payments WHERE order_id = ?",
                        new PaymentRowMapper(),
                        "payments",
                        orderId
                );

                if (!payments.isEmpty()) {
                    Map<String, Object> payment = payments.get(0);
                    Integer paymentId = (Integer) payment.get("payment_id");
                    Map<String, Object> oldPaymentData = new HashMap<>(payment);

                    transactionManager.executeUpdate(
                            txId,
                            "UPDATE payments SET status = ? WHERE payment_id = ?",
                            "payments",
                            "payment_id",
                            paymentId,
                            oldPaymentData,
                            "REFUNDED", paymentId
                    );
                }

                List<Map<String, Object>> products = transactionManager.executeSelect(
                        txId,
                        "SELECT product_id, stock, version FROM products WHERE product_id = ?",
                        new ProductRowMapper(),
                        "products",
                        productId
                );

                if (!products.isEmpty()) {
                    Map<String, Object> product = products.get(0);
                    Integer productVersion = (Integer) product.get("version");
                    Map<String, Object> oldProductData = new HashMap<>(product);

                    transactionManager.executeUpdate(
                            txId,
                            "UPDATE products SET stock = stock + ?, version = version + 1 WHERE product_id = ? AND version = ?",
                            "products",
                            "product_id",
                            productId,
                            oldProductData,
                            quantity, productId, productVersion
                    );
                }

                transactionManager.commit(txId);
                System.out.println("Order cancelled successfully!");
                return;

            } catch (DeadlockException e) {
                System.err.println("Deadlock detected, retrying...");
                try {
                    transactionManager.rollback(txId);
                } catch (Exception rollbackEx) {
                    System.err.println("Rollback failed: " + rollbackEx.getMessage());
                }
                retries++;

                try {
                    Thread.sleep((long) Math.pow(2, retries) * 100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Transaction interrupted", ie);
                }

            } catch (Exception e) {
                System.err.println("Cancel order failed: " + e.getMessage());
                try {
                    transactionManager.rollback(txId);
                } catch (Exception rollbackEx) {
                    System.err.println("Rollback failed: " + rollbackEx.getMessage());
                }
                throw new RuntimeException("Failed to cancel order", e);
            }
        }

        throw new RuntimeException("Failed to cancel order after " + MAX_RETRIES + " retries");
    }

    private static class ProductRowMapper implements RowMapper<Map<String, Object>> {
        @Override
        public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
            Map<String, Object> map = new HashMap<>();
            map.put("product_id", rs.getInt("product_id"));
            if (hasColumn(rs, "name")) map.put("name", rs.getString("name"));
            if (hasColumn(rs, "price")) map.put("price", rs.getDouble("price"));
            if (hasColumn(rs, "stock")) map.put("stock", rs.getInt("stock"));
            if (hasColumn(rs, "version")) map.put("version", rs.getInt("version"));
            return map;
        }
    }

    private static class OrderRowMapper implements RowMapper<Map<String, Object>> {
        @Override
        public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
            Map<String, Object> map = new HashMap<>();
            map.put("order_id", rs.getInt("order_id"));
            if (hasColumn(rs, "customer_id")) map.put("customer_id", rs.getInt("customer_id"));
            if (hasColumn(rs, "product_id")) map.put("product_id", rs.getInt("product_id"));
            if (hasColumn(rs, "quantity")) map.put("quantity", rs.getInt("quantity"));
            if (hasColumn(rs, "status")) map.put("status", rs.getString("status"));
            if (hasColumn(rs, "version")) map.put("version", rs.getInt("version"));
            return map;
        }
    }

    private static class PaymentRowMapper implements RowMapper<Map<String, Object>> {
        @Override
        public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
            Map<String, Object> map = new HashMap<>();
            map.put("payment_id", rs.getInt("payment_id"));
            if (hasColumn(rs, "order_id")) map.put("order_id", rs.getInt("order_id"));
            if (hasColumn(rs, "amount")) map.put("amount", rs.getInt("amount"));
            if (hasColumn(rs, "status")) map.put("status", rs.getString("status"));
            return map;
        }
    }

    private static boolean hasColumn(ResultSet rs, String columnName) {
        try {
            rs.findColumn(columnName);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
