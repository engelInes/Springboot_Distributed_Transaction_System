package org.example.springproject.repository;

import org.example.springproject.transaction.DistributedTransaction;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.example.springproject.config.mapper.EntityRowMappers.ORDER_MAPPER;
import static org.example.springproject.config.mapper.EntityRowMappers.PAYMENT_MAPPER;
import static org.example.springproject.util.AppConstants.*;

@Repository
public class OrderRepository {

    private final DistributedTransaction tm;

    public OrderRepository(DistributedTransaction tm) {
        this.tm = tm;
    }

    public Map<String, Object> findOrderForUpdate(String tx, Integer orderId) {
        String sql = "SELECT * FROM orders WHERE order_id = ?";
        List<Map<String, Object>> res = tm.executeSelectForUpdate(tx, sql, ORDER_MAPPER, TABLE_ORDERS, orderId, orderId);
        return res.isEmpty() ? null : res.get(0);
    }

    public Map<String, Object> findPaymentForUpdate(String tx, Integer orderId) {
        String sql = "SELECT * FROM payments WHERE order_id = ?";
        List<Map<String, Object>> res = tm.executeSelectForUpdate(tx, sql, PAYMENT_MAPPER, TABLE_PAYMENTS, null, orderId);
        return res.isEmpty() ? null : res.get(0);
    }

    public Integer createOrder(String tx, Integer customerId, Integer productId, int qty, int total) {
        String sql = "INSERT INTO orders (customer_id, product_id, quantity, total_amount, status, created_at, version) VALUES (?, ?, ?, ?, ?, ?, ?)";

        Map<String, Object> data = Map.of("customer_id", customerId, "product_id", productId, "quantity", qty, "total_amount", total, "status", STATUS_PENDING, "created_at", LocalDateTime.now(), "version", 0);

        return tm.executeInsertAndGetId(tx, sql, TABLE_ORDERS, COL_ORDER_ID, data,
                customerId, productId, qty, total, STATUS_PENDING, LocalDateTime.now(), 0);
    }

    public void createPayment(String tx, Integer orderId, int amount) {
        String sql = "INSERT INTO payments (order_id, amount, payment_method, status, processed_at) VALUES (?, ?, ?, ?, ?)";

        Map<String, Object> data = Map.of("order_id", orderId, "amount", amount, "payment_method", PAYMENT_METHOD_CREDIT, "status", STATUS_PENDING, "processed_at", LocalDateTime.now());

        tm.executeInsert(tx, sql, TABLE_PAYMENTS, data, orderId, amount, PAYMENT_METHOD_CREDIT, STATUS_PENDING, LocalDateTime.now());
    }

    public void updateOrderStatus(String tx, Map<String, Object> order, String newStatus) {
        Integer id = (Integer) order.get("order_id");
        Integer ver = (Integer) order.get("version");
        String sql = "UPDATE orders SET status = ?, version = version + 1 WHERE order_id = ? AND version = ?";
        tm.executeUpdate(tx, sql, TABLE_ORDERS, COL_ORDER_ID, id, order, newStatus, id, ver);
    }

    public void updateOrderQuantity(String tx, Map<String, Object> order, Integer newQty, Integer newTotal) {
        Integer id = (Integer) order.get("order_id");
        Integer ver = (Integer) order.get("version");
        String sql = "UPDATE orders SET quantity=?, total_amount=?, version=version+1 WHERE order_id=? AND version=?";
        tm.executeUpdate(tx, sql, TABLE_ORDERS, COL_ORDER_ID, id, order, newQty, newTotal, id, ver);
    }

    public void updateOrderDetails(String tx, Map<String, Object> order, Integer newProductId, Integer newQty, Integer newTotal) {
        Integer id = (Integer) order.get("order_id");
        Integer ver = (Integer) order.get("version");
        String sql = "UPDATE orders SET product_id=?, quantity=?, total_amount=?, version=version+1 WHERE order_id=? AND version=?";
        tm.executeUpdate(tx, sql, TABLE_ORDERS, COL_ORDER_ID, id, order, newProductId, newQty, newTotal, id, ver);
    }

    public void updatePaymentStatus(String tx, Map<String, Object> payment, String newStatus) {
        Integer id = (Integer) payment.get("payment_id");
        String sql = "UPDATE payments SET status = ? WHERE payment_id = ?";
        tm.executeUpdate(tx, sql, TABLE_PAYMENTS, COL_PAYMENT_ID, id, payment, newStatus, id);
    }

    public void updatePaymentAmount(String tx, Map<String, Object> payment, int newAmount) {
        Integer id = (Integer) payment.get("payment_id");
        String sql = "UPDATE payments SET amount=? WHERE payment_id = ?";
        tm.executeUpdate(tx, sql, TABLE_PAYMENTS, COL_PAYMENT_ID, id, payment, newAmount, id);
    }
}
