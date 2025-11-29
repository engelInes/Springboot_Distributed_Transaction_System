package org.example.springproject.models;

import jdk.dynalink.Operation;
import org.example.springproject.transaction.TransactionOperation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a distributed transaction across multiple databases
 */
public class Transaction {
    private String transactionId;
    private final long timestamp;
    private final LocalDateTime startTime;
    private TransactionStatus status;
    private final List<TransactionOperation> operations;
    private final ReentrantReadWriteLock lock;

    public enum TransactionStatus {
        ACTIVE,
        PREPARING,
        COMMITTED,
        ABORTED,
        ROLLED_BACK
    }

    public Transaction() {
        this.transactionId = UUID.randomUUID().toString();
        this.timestamp = System.nanoTime();
        this.startTime = LocalDateTime.now();
        this.status = TransactionStatus.ACTIVE;
        this.operations = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public Transaction(String transactionId) {
        this.transactionId = transactionId;
        this.timestamp = System.nanoTime();
        this.startTime = LocalDateTime.now();
        this.status = TransactionStatus.ACTIVE;
        this.operations = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public void addOperation(TransactionOperation operation) {
        lock.writeLock().lock();
        try {
            if (status != TransactionStatus.ACTIVE) {
                throw new IllegalStateException("Cannot add operation to non-active transaction");
            }
            operations.add(operation);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<TransactionOperation> getOperations() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(operations);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setStatus(TransactionStatus status) {
        lock.writeLock().lock();
        try {
            this.status = status;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public TransactionStatus getStatus() {
        lock.readLock().lock();
        try {
            return status;
        } finally {
            lock.readLock().unlock();
        }
    }

    public String getTransactionId() {
        return transactionId;
    }
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public int getOperationCount() {
        lock.readLock().lock();
        try {
            return operations.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        return String.format("Transaction[id=%s, timestamp=%d, status=%s, operations=%d]",
                transactionId, timestamp, status, operations.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return transactionId.hashCode();
    }
}
