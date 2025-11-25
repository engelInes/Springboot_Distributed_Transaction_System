package org.example.springproject.transaction;

import org.example.springproject.models.Transaction;

import java.sql.Connection;

public class TransactionContext {
    private final String transactionId;
    private final Transaction transaction;
    private Connection inventoryConnection;
    private Connection orderConnection;

    public TransactionContext(Transaction transaction) {
        this.transactionId = transaction.getTransactionId();
        this.transaction = transaction;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Connection getInventoryConnection() {
        return inventoryConnection;
    }

    public Connection getOrderConnection() {
        return orderConnection;
    }

    public void setInventoryConnection(Connection inventoryConnection) {
        this.inventoryConnection = inventoryConnection;
    }

    public void setOrderConnection(Connection orderConnection) {
        this.orderConnection = orderConnection;
    }
}