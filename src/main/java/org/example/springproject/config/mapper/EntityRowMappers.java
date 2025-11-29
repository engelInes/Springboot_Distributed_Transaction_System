package org.example.springproject.config.mapper;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class EntityRowMappers {

    public static final RowMapper<Map<String, Object>> PRODUCT_MAPPER = (rs, i) -> {
        Map<String, Object> map = new HashMap<>();
        map.put("product_id", rs.getInt("product_id"));
        map.put("name", getSafe(rs, "name"));
        map.put("price", getSafe(rs, "price"));
        map.put("stock", getSafe(rs, "stock"));
        map.put("version", getSafe(rs, "version"));
        return map;
    };

    public static final RowMapper<Map<String, Object>> ORDER_MAPPER = (rs, i) -> {
        Map<String, Object> map = new HashMap<>();
        map.put("order_id", rs.getInt("order_id"));
        map.put("customer_id", getSafe(rs, "customer_id"));
        map.put("product_id", getSafe(rs, "product_id"));
        map.put("quantity", getSafe(rs, "quantity"));
        map.put("status", getSafe(rs, "status"));
        map.put("version", getSafe(rs, "version"));
        map.put("total_amount", getSafe(rs, "total_amount"));
        return map;
    };

    public static final RowMapper<Map<String, Object>> PAYMENT_MAPPER = (rs, i) -> {
        Map<String, Object> map = new HashMap<>();
        map.put("payment_id", rs.getInt("payment_id"));
        map.put("order_id", getSafe(rs, "order_id"));
        map.put("amount", getSafe(rs, "amount"));
        map.put("status", getSafe(rs, "status"));
        return map;
    };

    private static Object getSafe(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return rs.getObject(column);
        } catch (SQLException ignored) {
            return null;
        }
    }
}
