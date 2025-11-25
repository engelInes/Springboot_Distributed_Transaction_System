package org.example.springproject.util;

import static org.example.springproject.util.AppConstants.*;

public class SchemaUtils {

    private SchemaUtils() {}

    /**
     * Resolves the primary key column name based on the table name.
     */
    public static String getPrimaryKeyColumn(String tableName) {
        if (tableName == null) return COL_ID;

        return switch (tableName.toLowerCase()) {
            case TABLE_PRODUCTS -> COL_PRODUCT_ID;
            case TABLE_ORDERS -> COL_ORDER_ID;
            case TABLE_CUSTOMERS -> COL_CUSTOMER_ID;
            case TABLE_PAYMENTS -> COL_PAYMENT_ID;
            case TABLE_INVENTORY_TX -> COL_TRANSACTION_ID;
            default -> COL_ID;
        };
    }

    /**
     * Determines if a table uses manual string keys (UUID) instead of auto-increment.
     */
    public static boolean usesManualStringKey(String tableName) {
        return TABLE_INVENTORY_TX.equalsIgnoreCase(tableName);
    }
}
