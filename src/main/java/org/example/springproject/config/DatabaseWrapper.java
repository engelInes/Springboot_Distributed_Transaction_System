package org.example.springproject.config;

import org.example.springproject.exceptions.DeadlockException;
import org.example.springproject.models.Transaction;
import org.example.springproject.transaction.TransactionContext;
import org.example.springproject.transaction.TransactionOperation;
import org.example.springproject.transaction.scheduler.TwoPhaseLockingScheduler;
import org.example.springproject.util.JDBCUtils;
import org.example.springproject.util.OperationLog;
import org.example.springproject.util.SchemaUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.example.springproject.util.AppConstants.DB_INVENTORY;
import static org.example.springproject.util.AppConstants.DB_ORDER;

@Component
public class DatabaseWrapper {

    private final Map<String, DataSource> dataSources;
    private final Map<String, TransactionContext> activeTransactions;
    private final TwoPhaseLockingScheduler scheduler;
    private final OperationLog operationLog;
    private final JDBCUtils jdbcUtils;

    public DatabaseWrapper(
            @Qualifier("inventoryDataSource") DataSource inventoryDataSource,
            @Qualifier("orderDataSource") DataSource orderDataSource,
            TwoPhaseLockingScheduler scheduler,
            OperationLog operationLog,
            JDBCUtils jdbcUtils) {

        this.dataSources = Map.of(
                DB_INVENTORY, inventoryDataSource,
                DB_ORDER, orderDataSource
        );
        this.activeTransactions = new ConcurrentHashMap<>();
        this.scheduler = scheduler;
        this.operationLog = operationLog;
        this.jdbcUtils = jdbcUtils;
    }

    public TransactionContext beginTransaction(Transaction tx) throws SQLException {
        String txId = tx.getTransactionId();
        System.out.println(">>> DEBUG [DatabaseWrapper]: Beginning transaction " + txId);
        if (activeTransactions.containsKey(txId)) {
            System.err.println(">>> WARNING: Transaction " + txId + " already exists. Cleaning up old context.");
            closeConnections(txId);
        }

        TransactionContext context = new TransactionContext(tx);
        Connection inventoryConn = null;
        Connection orderConn = null;

        try {
            inventoryConn = getConnection(DB_INVENTORY);
            orderConn = getConnection(DB_ORDER);

            System.out.println(">>> DEBUG [DatabaseWrapper]: Connections created for " + txId);
            System.out.println("    - Transaction object ID: " + tx.getTransactionId());
            System.out.println("    - Context transaction ID: " + context.getTransactionId());
            System.out.println("    - Inventory connection: " + inventoryConn + " (autoCommit=" + inventoryConn.getAutoCommit() + ")");
            System.out.println("    - Order connection: " + orderConn + " (autoCommit=" + orderConn.getAutoCommit() + ")");

            if (!txId.equals(context.getTransactionId())) {
                throw new SQLException("Transaction ID mismatch in DatabaseWrapper!");
            }

            context.setInventoryConnection(inventoryConn);
            context.setOrderConnection(orderConn);

            activeTransactions.put(txId, context);
            return context;
        } catch (SQLException e) {
            System.err.println(">>> ERROR [DatabaseWrapper]: Failed to begin transaction " + txId);
            e.printStackTrace();
            if (inventoryConn != null) {
                try {
                    inventoryConn.close();
                } catch (SQLException ex) {
                    System.err.println("Failed to close inventory connection during cleanup");
                }
            }
            if (orderConn != null) {
                try {
                    orderConn.close();
                } catch (SQLException ex) {
                    System.err.println("Failed to close order connection during cleanup");
                }
            }
            throw e;
        }
    }

    public TransactionContext getContext(String txId) {
        TransactionContext context = activeTransactions.get(txId);
        if (context == null) {
            throw new IllegalArgumentException("Transaction not found or already closed: " + txId);
        }
        return context;
    }

    public <T> List<T> executeSelectForUpdate(String txId, String database, String tableName, String sql,
                                              RowMapper<T> rowMapper, Object primaryKey, Object... params) throws DeadlockException {
        String sqlForUpdate = sql + " FOR UPDATE";
        TransactionContext context = getContext(txId);

        TransactionOperation op = new TransactionOperation(txId, TransactionOperation.OperationType.SELECT_FOR_UPDATE,
                database, tableName, primaryKey, null, null, sqlForUpdate, params);

        System.out.println(">>> DEBUG [DatabaseWrapper]: Executing SELECT FOR UPDATE on " + database + "." + tableName + " for tx=" + txId);

        checkLockOrThrow(context, op);

        try {
            Connection conn = getActiveConnection(context, database);
            verifyConnectionValid(conn, database, txId);

            List<T> result = jdbcUtils.executeQuery(conn, sqlForUpdate, rowMapper, params);

            // CRITICAL: Always complete the operation to log it
            completeOperation(context, op);
            System.out.println(">>> DEBUG [DatabaseWrapper]: SELECT FOR UPDATE completed and logged");

            return result;
        } catch (SQLException | DataAccessException e) {
            System.err.println(">>> ERROR: SELECT FOR UPDATE failed on " + database + ": " + e.getMessage());
            e.printStackTrace();
            throw new DeadlockException("Operation failed: " + e.getMessage());
        }
    }

    public int executeUpdate(String txId, String database, String tableName, String sql, Object primaryKey,
                             Map<String, Object> beforeImage, Map<String, Object> afterImage, Object... params) throws DeadlockException {

        TransactionContext context = getContext(txId);
        TransactionOperation op = new TransactionOperation(txId, TransactionOperation.OperationType.UPDATE,
                database, tableName, primaryKey, beforeImage, afterImage, sql, params);

        System.out.println(">>> DEBUG [DatabaseWrapper]: Executing UPDATE on " + database + "." + tableName + " for tx=" + txId);

        checkLockOrThrow(context, op);

        try {
            Connection conn = getActiveConnection(context, database);
            verifyConnectionValid(conn, database, txId);

            int rows = jdbcUtils.executeUpdate(conn, sql, params);

            if (rows > 0) {
                // CRITICAL: Always complete the operation to log it
                completeOperation(context, op);
                System.out.println(">>> DEBUG [DatabaseWrapper]: UPDATE completed and logged (" + rows + " rows)");
            } else {
                System.out.println(">>> WARNING [DatabaseWrapper]: UPDATE affected 0 rows, not logging");
            }

            return rows;
        } catch (SQLException | DataAccessException e) {
            System.err.println(">>> ERROR: UPDATE failed on " + database + ": " + e.getMessage());
            e.printStackTrace();
            throw new DeadlockException("Operation failed: " + e.getMessage());
        }
    }

    /**
     * Executes an INSERT. specially handles 'inventory_transactions' which uses String IDs.
     */
    public Integer executeInsert(String txId, String database, String tableName, String sql,
                                 Map<String, Object> data, Object... params) throws DeadlockException {

        TransactionContext context = getContext(txId);
        TransactionOperation initialOp = new TransactionOperation(txId, TransactionOperation.OperationType.INSERT,
                database, tableName, null, null, data, sql, params);

        System.out.println(">>> DEBUG [DatabaseWrapper]: Executing INSERT on " + database + "." + tableName + " for tx=" + txId);

        checkLockOrThrow(context, initialOp);

        try {
            Connection conn = getActiveConnection(context, database);
            verifyConnectionValid(conn, database, txId);

            Object primaryKey;
            Integer generatedId = null;

            if (SchemaUtils.usesManualStringKey(tableName)) {
                jdbcUtils.executeUpdate(conn, sql, params);
                primaryKey = params[0];
            } else {
                generatedId = jdbcUtils.executeInsertWithAutoGeneratedKey(conn, sql, params);
                primaryKey = generatedId;
            }

            TransactionOperation finalOp = new TransactionOperation(txId, TransactionOperation.OperationType.INSERT,
                    database, tableName, primaryKey, null, data, sql, params);

            completeOperation(context, finalOp);
            System.out.println(">>> DEBUG [DatabaseWrapper]: INSERT completed and logged (id=" + primaryKey + ")");

            return generatedId != null ? generatedId : 0;

        } catch (SQLException | DataAccessException e) {
            System.err.println(">>> ERROR: INSERT failed on " + database + ": " + e.getMessage());
            e.printStackTrace();
            throw new DeadlockException("Operation failed: " + e.getMessage());
        }
    }

    public Map<String, Object> fetchBeforeImage(String database, String tableName, Long primaryKey) {
        Connection conn = null;
        try {
            String pkCol = SchemaUtils.getPrimaryKeyColumn(tableName);
            String sql = "SELECT * FROM " + tableName + " WHERE " + pkCol + " = ?";

            conn = getConnection(database);
            try {
                List<Map<String, Object>> result = jdbcUtils.executeQuery(conn, sql, jdbcUtils.getGenericRowMapper(), primaryKey);
                if (result.isEmpty()) {
                    throw new RuntimeException("Record not found: " + tableName + " " + pkCol + "=" + primaryKey);
                }
                return result.get(0);
            } finally {
                if (conn != null && !conn.isClosed()) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch before image from " + tableName, e);
        }
    }

    public void closeConnections(String txId) {
        System.out.println(">>> DEBUG [DatabaseWrapper]: Closing connections for transaction " + txId);
        TransactionContext context = activeTransactions.remove(txId);

        if (context != null) {
            Connection invConn = context.getInventoryConnection();
            Connection ordConn = context.getOrderConnection();

            closeSafely(invConn, "inventory", txId);
            closeSafely(ordConn, "order", txId);

            System.out.println(">>> DEBUG [DatabaseWrapper]: Connections closed for " + txId);
        } else {
            System.out.println(">>> DEBUG [DatabaseWrapper]: No context found for " + txId + " (already cleaned up)");
        }
    }


    private Connection getConnection(String dbName) throws SQLException {
        Connection conn = dataSources.get(dbName).getConnection();
        conn.setAutoCommit(false);
        return conn;
    }

    private Connection getActiveConnection(TransactionContext context, String database) {
        return DB_INVENTORY.equals(database) ? context.getInventoryConnection() : context.getOrderConnection();
    }

    private void verifyConnectionValid(Connection conn, String database, String txId) throws SQLException {
        if (conn == null) {
            throw new SQLException("Connection is null for database " + database + " in transaction " + txId);
        }
        if (conn.isClosed()) {
            throw new SQLException("Connection is closed for database " + database + " in transaction " + txId);
        }
        if (conn.getAutoCommit()) {
            System.err.println(">>> WARNING: Connection for " + database + " has autoCommit=true! Fixing...");
            conn.setAutoCommit(false);
        }
    }

    private void checkLockOrThrow(TransactionContext context, TransactionOperation op) throws DeadlockException {
        if (!scheduler.canExecute(context.getTransaction(), op)) {
            System.err.println(">>> DEADLOCK: Transaction " + context.getTransactionId() + " cannot acquire lock for " + op.getTableName());
            throw new DeadlockException("Transaction must abort due to lock conflict: " + context.getTransactionId());
        }
    }

    private void completeOperation(TransactionContext context, TransactionOperation op) {
        System.out.println(">>> DEBUG [DatabaseWrapper]: Completing operation - tx=" + context.getTransactionId() +
                " db=" + op.getDatabase() + " table=" + op.getTableName() + " type=" + op.getOperationType());

        op.setExecuted(true);
        operationLog.logOperation(op);

        System.out.println(">>> DEBUG [DatabaseWrapper]: Operation logged to OperationLog");

        scheduler.onOperationComplete(context.getTransaction(), op);

        System.out.println(">>> DEBUG [DatabaseWrapper]: Scheduler notified of completion");
    }

    private void closeSafely(Connection conn, String dbName, String txId) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    System.out.println(">>> DEBUG [DatabaseWrapper]: Closing " + dbName + " connection for " + txId);

                    // CRITICAL: Reset autoCommit to true before returning to pool
                    // This ensures the connection is in a clean state for reuse
                    if (!conn.getAutoCommit()) {
                        conn.setAutoCommit(true);
                    }

                    conn.close();
                    System.out.println(">>> DEBUG [DatabaseWrapper]: " + dbName + " connection closed successfully");
                } else {
                    System.out.println(">>> DEBUG [DatabaseWrapper]: " + dbName + " connection was already closed for " + txId);
                }
            } catch (SQLException e) {
                System.err.println(">>> ERROR [DatabaseWrapper]: Error closing " + dbName + " connection for " + txId + ": " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println(">>> DEBUG [DatabaseWrapper]: " + dbName + " connection was null for " + txId);
        }
    }
}