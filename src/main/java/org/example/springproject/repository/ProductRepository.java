package org.example.springproject.repository;

import org.example.springproject.transaction.DistributedTransaction;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.example.springproject.config.mapper.EntityRowMappers.PRODUCT_MAPPER;
import static org.example.springproject.util.AppConstants.*;

@Repository
public class ProductRepository {

    private final DistributedTransaction tm;

    public ProductRepository(DistributedTransaction tm) {
        this.tm = tm;
    }

    public Map<String, Object> findByIdForUpdate(String tx, Integer productId) {
        String sql = "SELECT product_id, name, price, stock, version FROM products WHERE product_id = ?";
        List<Map<String, Object>> result = tm.executeSelectForUpdate(tx, sql, PRODUCT_MAPPER, TABLE_PRODUCTS, productId, productId);
        return result.isEmpty() ? null : result.get(0);
    }

    public void decreaseStock(String tx, Map<String, Object> product, int quantity) {
        updateStock(tx, product, quantity, true);
    }

    public void increaseStock(String tx, Map<String, Object> product, int quantity) {
        updateStock(tx, product, quantity, false);
    }

    private void updateStock(String tx, Map<String, Object> product, int quantity, boolean isDecrease) {
        Integer id = (Integer) product.get("product_id");
        Integer version = (Integer) product.get("version");
        String operator = isDecrease ? "-" : "+";

        String sql = String.format("UPDATE products SET stock = stock %s ?, version = version + 1 WHERE product_id = ? AND version = ?", operator);

        int updated = tm.executeUpdate(tx, sql, TABLE_PRODUCTS, COL_PRODUCT_ID, id, product, quantity, id, version);

        if (updated == 0) throw new RuntimeException("Optimistic lock conflict or product modified concurrently");
    }

    public void markDiscontinued(String tx, Map<String, Object> product) {
        Integer id = (Integer) product.get("product_id");
        String sql = "UPDATE products SET stock = 0, name = CONCAT('DISCONTINUED - ', name), version = version + 1 WHERE product_id = ?";
        tm.executeUpdate(tx, sql, TABLE_PRODUCTS, COL_PRODUCT_ID, id, product, id);
    }

    public void logInventoryTransaction(String tx, Integer productId, int qtyChange) {
        String sql = "INSERT INTO inventory_transactions (transaction_id, product_id, quantity_change, timestamp) VALUES (?, ?, ?, ?)";
        tm.executeInsert(tx, sql, TABLE_INVENTORY_TX,
                Map.of("product_id", productId, "quantity_change", qtyChange),
                UUID.randomUUID().toString(), productId, qtyChange, LocalDateTime.now());
    }
}