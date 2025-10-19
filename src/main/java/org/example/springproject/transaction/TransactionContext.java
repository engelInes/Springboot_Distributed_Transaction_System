package org.example.springproject.transaction;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;

public class TransactionContext {
    private final String transactionId;
    private Connection inventoryConnection;
    private Connection orderConnection;

    private final Map<String, Lock> readLocks = new HashMap<>();
    private final Map<String, Lock> writeLocks = new HashMap<>();

    public TransactionContext(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getTransactionId() {
        return transactionId;
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

    public void addReadLock(String tableName, Lock lock) {
        readLocks.put(tableName, lock);
    }

    public void addWriteLock(String tableName, Lock lock) {
        writeLocks.put(tableName, lock);
    }

    public void releaseAllLocks() {
        readLocks.values().forEach(lock -> {
            try {
                lock.unlock();
            } catch (IllegalMonitorStateException ignored) {
            }
        });

        writeLocks.values().forEach(lock -> {
            try {
                lock.unlock();
            } catch (IllegalMonitorStateException ignored) {
            }
        });

        readLocks.clear();
        writeLocks.clear();
    }
}
