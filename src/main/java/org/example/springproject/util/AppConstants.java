package org.example.springproject.util;

public class AppConstants {
    private AppConstants() {
    }

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_CAPTURED = "CAPTURED";

    public static final String PAYMENT_METHOD_CREDIT = "CREDIT_CARD";

    public static final String TABLE_PRODUCTS = "products";
    public static final String TABLE_ORDERS = "orders";
    public static final String TABLE_PAYMENTS = "payments";
    public static final String TABLE_INVENTORY_TX = "inventory_transactions";
    public static final String TABLE_CUSTOMERS = "customers";

    public static final String COL_PRODUCT_ID = "product_id";
    public static final String COL_ORDER_ID = "order_id";
    public static final String COL_PAYMENT_ID = "payment_id";
    public static final String COL_ID = "id";
    public static final String COL_CUSTOMER_ID = "customer_id";
    public static final String COL_TRANSACTION_ID = "transaction_id";

    public static final String DB_INVENTORY = "inventory";
    public static final String DB_ORDER = "order";
}
