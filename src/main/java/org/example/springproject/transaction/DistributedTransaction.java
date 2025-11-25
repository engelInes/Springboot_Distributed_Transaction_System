package org.example.springproject.transaction;

import org.example.springproject.config.DatabaseWrapper;
import org.example.springproject.exceptions.DeadlockException;
import org.example.springproject.models.Transaction;
import org.example.springproject.transaction.scheduler.TwoPhaseLockingScheduler;
import org.example.springproject.util.OperationLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.RowMapper;

/**
 * The Coordinator for the custom Distributed Transaction system.
 * Implements the Two-Phase Commit (2PC) protocol (Atomicity & Durability).
 */
@Component
public class DistributedTransaction {

    private final Map<String, Transaction> activeTransactions = new ConcurrentHashMap<>();

    @Autowired
    private DatabaseWrapper databaseWrapper;

    @Autowired
    private RollbackManager rollbackManager;

    @Autowired
    private TwoPhaseLockingScheduler scheduler;

    @Autowired
    private OperationLog operationLog;

    /**
     * Starts a new distributed transaction.
     * @return The transaction ID.
     */
    public String beginTransaction() {
        Transaction tx = new Transaction();
        try {
            databaseWrapper.beginTransaction(tx.getTransactionId());
            activeTransactions.put(tx.getTransactionId(), tx);
            return tx.getTransactionId();
        } catch (SQLException e) {
            throw new RuntimeException("Could not start transaction.", e);
        }
    }

    /**
     * Commits the distributed transaction using the Two-Phase Commit (2PC) protocol.
     */
    public void commit(String txId) {
        Transaction tx = activeTransactions.get(txId);
        if (tx == null || tx.getStatus() != Transaction.TransactionStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot commit non-active transaction: " + txId);
        }

        tx.setStatus(Transaction.TransactionStatus.PREPARING);
        List<String> affectedDbs = operationLog.getAffectedDatabases(txId);
        boolean allPrepared = true;

        for (String dbName : affectedDbs) {
            if (!prepare(txId, dbName)) {
                allPrepared = false;
                break;
            }
        }

        if (allPrepared) {
            for (String dbName : affectedDbs) {
                try {
                    commitResource(txId, dbName);
                } catch (SQLException e) {
                    System.err.println("CRITICAL: Failed to commit on " + dbName + ". State is inconsistent.");
                    tx.setStatus(Transaction.TransactionStatus.ABORTED);
                    rollbackManager.rollback(tx);
                    throw new RuntimeException("Commit failed on " + dbName + ". State is inconsistent.", e);
                }
            }
            tx.setStatus(Transaction.TransactionStatus.COMMITTED);
            operationLog.logCommit(txId);
        } else {
            for (String dbName : affectedDbs) {
                try {
                    abortResource(txId, dbName);
                } catch (SQLException e) {
                    System.err.println("WARNING: Failed to abort on " + dbName + ". Ignoring for now.");
                }
            }
            tx.setStatus(Transaction.TransactionStatus.ABORTED);
            rollbackManager.rollback(tx);
        }

        scheduler.onTransactionCommit(tx);
        activeTransactions.remove(txId);
        databaseWrapper.closeConnections(txId);
    }

    /**
     * Rollbacks the transaction (both DB connections and application log) and cleans up resources.
     */
    public void rollback(String txId) {
        Transaction tx = activeTransactions.get(txId);
        if (tx == null) return;

        tx.setStatus(Transaction.TransactionStatus.ABORTED);

        List<String> affectedDbs = operationLog.getAffectedDatabases(txId);
        for (String dbName : affectedDbs) {
            try {
                abortResource(txId, dbName);
            } catch (SQLException e) {
                System.err.println("WARNING: Failed to abort connection for " + dbName + ". Continuing application-level rollback.");
            }
        }

        rollbackManager.rollback(tx);

        scheduler.onTransactionAbort(tx);
        activeTransactions.remove(txId);

        databaseWrapper.closeConnections(txId);
    }

    /** Phase 1: Prepare (Vote) */
    private boolean prepare(String txId, String dbName) {
        TransactionContext context = databaseWrapper.getContext(txId);
        Connection conn = dbName.equals("inventory") ? context.getInventoryConnection() : context.getOrderConnection();

        try {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println(dbName + " failed to prepare: " + e.getMessage());
            return false;
        }
    }

    /** Phase 2: Commit (Final Decision) */
    private void commitResource(String txId, String dbName) throws SQLException {
        TransactionContext context = databaseWrapper.getContext(txId);
        Connection conn = dbName.equals("inventory") ? context.getInventoryConnection() : context.getOrderConnection();

        if (conn != null && !conn.isClosed()) {
            conn.commit();
        }
    }

    /** Phase 2: Abort (Final Decision) */
    private void abortResource(String txId, String dbName) throws SQLException {
        TransactionContext context = databaseWrapper.getContext(txId);
        Connection conn = dbName.equals("inventory") ? context.getInventoryConnection() : context.getOrderConnection();

        if (conn != null && !conn.isClosed()) {
            conn.rollback();
        }
    }

    public <T> List<T> executeSelectForUpdate(String txId, String sql, RowMapper<T> rowMapper, String tableName, Object primaryKey, Object... params) throws DeadlockException {
        return databaseWrapper.executeSelectForUpdate(txId, getDatabaseForTable(tableName), tableName, sql, rowMapper, primaryKey, params);
    }

    public int executeUpdate(String txId, String sql, String tableName, String primaryKeyColumnName, Object primaryKey, Map<String, Object> beforeImage, Object... params) throws DeadlockException {
        return databaseWrapper.executeUpdate(txId, getDatabaseForTable(tableName), tableName, sql, primaryKey, beforeImage, null, params);
    }

    public Integer executeInsert(String txId, String sql, String tableName, Map<String, Object> data, Object... params) throws DeadlockException {
        return databaseWrapper.executeInsert(txId, getDatabaseForTable(tableName), tableName, sql, data, params);
    }

    public Integer executeInsertAndGetId(String txId, String sql, String tableName, String primaryKeyName, Map<String, Object> data, Object... params) throws DeadlockException {
        return databaseWrapper.executeInsert(txId, getDatabaseForTable(tableName), tableName, sql, data, params);
    }

    private String getDatabaseForTable(String tableName) {
        return switch (tableName.toLowerCase()) {
            case "products", "suppliers", "inventory_transactions", "transaction_log_inventory" -> "inventory";
            case "customers", "orders", "payments", "transaction_log_order" -> "order";
            default -> throw new IllegalArgumentException("Unknown table name: " + tableName);
        };
    }
}