package org.example.springproject.config.mapper;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class EntityRowMappers {

    public static final RowMapper<Map<String, Object>> PRODUCT_MAPPER = (rs, i) -> Map.of(
            "product_id", rs.getInt("product_id"),
            "name", getSafe(rs, "name"),
            "price", getSafe(rs, "price"),
            "stock", getSafe(rs, "stock"),
            "version", getSafe(rs, "version")
    );

    public static final RowMapper<Map<String, Object>> ORDER_MAPPER = (rs, i) -> Map.of(
            "order_id", rs.getInt("order_id"),
            "customer_id", getSafe(rs, "customer_id"),
            "product_id", getSafe(rs, "product_id"),
            "quantity", getSafe(rs, "quantity"),
            "status", getSafe(rs, "status"),
            "version", getSafe(rs, "version")
    );

    public static final RowMapper<Map<String, Object>> PAYMENT_MAPPER = (rs, i) -> Map.of(
            "payment_id", rs.getInt("payment_id"),
            "order_id", getSafe(rs, "order_id"),
            "amount", getSafe(rs, "amount"),
            "status", getSafe(rs, "status")
    );

    private static Object getSafe(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return rs.getObject(column);
        } catch (SQLException ignored) {
            return null;
        }
    }
}
