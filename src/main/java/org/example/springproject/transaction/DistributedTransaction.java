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
     *
     * @return The transaction ID.
     */
    public String beginTransaction() {
        Transaction tx = new Transaction();
        try {
            System.out.println(">>> DEBUG: Beginning transaction: " + tx.getTransactionId());
            databaseWrapper.beginTransaction(tx);
            activeTransactions.put(tx.getTransactionId(), tx);
            return tx.getTransactionId();
        } catch (SQLException e) {
            System.err.println(">>> ERROR: Failed to start transaction: " + e.getMessage());
            throw new RuntimeException("Could not start transaction.", e);
        }
    }

    /**
     * Commits the distributed transaction using the Two-Phase Commit (2PC) protocol.
     */
    public void commit(String txId) {
        System.out.println(">>> DEBUG: Attempting to commit transaction: " + txId);
        Transaction tx = activeTransactions.get(txId);
        if (tx == null || tx.getStatus() != Transaction.TransactionStatus.ACTIVE) {
            System.err.println(">>> ERROR: Cannot commit non-active transaction: " + txId);
            throw new IllegalArgumentException("Cannot commit non-active transaction: " + txId);
        }

        tx.setStatus(Transaction.TransactionStatus.PREPARING);
        List<String> affectedDbs = operationLog.getAffectedDatabases(txId);
        System.out.println(">>> DEBUG: Affected databases for " + txId + ": " + affectedDbs);

        boolean allPrepared = true;

        for (String dbName : affectedDbs) {
            if (!prepare(txId, dbName)) {
                System.err.println(">>> ERROR: Prepare failed for database: " + dbName);
                allPrepared = false;
                break;
            }
        }

        if (allPrepared) {
            System.out.println(">>> DEBUG: All databases prepared. Proceeding with commit phase.");
            for (String dbName : affectedDbs) {
                try {
                    commitResource(txId, dbName);
                } catch (SQLException e) {
                    System.err.println("CRITICAL: Failed to commit on " + dbName + ". State is inconsistent.");
                    e.printStackTrace();
                    tx.setStatus(Transaction.TransactionStatus.ABORTED);
                    rollbackManager.rollback(tx);
                    // Clean up BEFORE throwing
                    scheduler.onTransactionAbort(tx);
                    activeTransactions.remove(txId);
                    databaseWrapper.closeConnections(txId);
                    throw new RuntimeException("Commit failed on " + dbName + ". State is inconsistent.", e);
                }
            }
            tx.setStatus(Transaction.TransactionStatus.COMMITTED);
            operationLog.logCommit(txId);
            System.out.println(">>> DEBUG: Transaction committed successfully: " + txId);
        } else {
            System.out.println(">>> DEBUG: Prepare failed. Aborting transaction: " + txId);
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
        System.out.println(">>> DEBUG: Transaction cleanup completed for: " + txId);
    }

    /**
     * Rollbacks the transaction (both DB connections and application log) and cleans up resources.
     */
    public void rollback(String txId) {
        System.out.println(">>> DEBUG: Rolling back transaction: " + txId);
        Transaction tx = activeTransactions.get(txId);
        if (tx == null) {
            System.out.println(">>> DEBUG: Transaction not found in active transactions: " + txId);
            return;
        }

        tx.setStatus(Transaction.TransactionStatus.ABORTED);

        List<String> affectedDbs = operationLog.getAffectedDatabases(txId);
        System.out.println(">>> DEBUG: Rolling back affected databases: " + affectedDbs);

        for (String dbName : affectedDbs) {
            try {
                abortResource(txId, dbName);
                System.out.println(">>> DEBUG: Successfully rolled back database: " + dbName);
            } catch (SQLException e) {
                System.err.println("WARNING: Failed to abort connection for " + dbName + ". Continuing application-level rollback.");
                e.printStackTrace();
            }
        }

        rollbackManager.rollback(tx);

        scheduler.onTransactionAbort(tx);
        activeTransactions.remove(txId);

        databaseWrapper.closeConnections(txId);
        System.out.println(">>> DEBUG: Rollback cleanup completed for: " + txId);
    }

    /**
     * Phase 1: Prepare (Vote)
     */
    private boolean prepare(String txId, String dbName) {
        System.out.println(">>> DEBUG: Preparing database: " + dbName + " for transaction: " + txId);
        TransactionContext context = databaseWrapper.getContext(txId);
        if (context == null) {
            System.err.println(">>> ERROR: No transaction context found for: " + txId);
            return false;
        }

        Connection conn = dbName.equals("inventory") ? context.getInventoryConnection() : context.getOrderConnection();

        try {
            if (conn == null) {
                System.err.println(">>> ERROR: Connection is null for database: " + dbName);
                return false;
            }
            if (conn.isClosed()) {
                System.err.println(">>> ERROR: Connection is already closed for database: " + dbName);
                return false;
            }
            System.out.println(">>> DEBUG: Database " + dbName + " prepared successfully");
            return true;
        } catch (SQLException e) {
            System.err.println(">>> ERROR: " + dbName + " failed to prepare: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Phase 2: Commit (Final Decision)
     */
    private void commitResource(String txId, String dbName) throws SQLException {
        System.out.println(">>> DEBUG: Committing transaction " + txId + " to database: " + dbName);
        TransactionContext context = databaseWrapper.getContext(txId);
        if (context == null) {
            throw new SQLException("No transaction context found for: " + txId);
        }

        Connection conn = dbName.equals("inventory") ? context.getInventoryConnection() : context.getOrderConnection();

        if (conn != null && !conn.isClosed()) {
            conn.commit();
            System.out.println(">>> DEBUG: Commit successful for " + dbName);
        } else {
            String msg = ">>> CRITICAL: Connection for " + dbName + " was closed/null before commit!";
            System.err.println(msg);
            throw new SQLException(msg);
        }
    }

    /**
     * Phase 2: Abort (Final Decision)
     */
    private void abortResource(String txId, String dbName) throws SQLException {
        System.out.println(">>> DEBUG: Aborting database: " + dbName + " for transaction: " + txId);
        TransactionContext context = databaseWrapper.getContext(txId);
        if (context == null) {
            System.err.println(">>> WARNING: No transaction context found for abort: " + txId);
            return;
        }

        Connection conn = dbName.equals("inventory") ? context.getInventoryConnection() : context.getOrderConnection();

        if (conn != null && !conn.isClosed()) {
            conn.rollback();
            System.out.println(">>> DEBUG: Rollback successful for " + dbName);
        } else {
            System.err.println(">>> WARNING: Connection for " + dbName + " was already closed during abort");
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