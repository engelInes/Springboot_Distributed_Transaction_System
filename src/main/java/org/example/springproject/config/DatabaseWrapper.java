package org.example.springproject.config;

import org.example.springproject.exceptions.DeadlockException;
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

    public TransactionContext beginTransaction(String txId) throws SQLException {
        TransactionContext context = new TransactionContext(new org.example.springproject.models.Transaction());
        try {
            Connection inventoryConn = getConnection(DB_INVENTORY);
            Connection orderConn = getConnection(DB_ORDER);

            context.setInventoryConnection(inventoryConn);
            context.setOrderConnection(orderConn);

            activeTransactions.put(txId, context);
            return context;
        } catch (SQLException e) {
            if (context.getInventoryConnection() != null) context.getInventoryConnection().close();
            if (context.getOrderConnection() != null) context.getOrderConnection().close();
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

        checkLockOrThrow(context, op);

        try {
            Connection conn = getActiveConnection(context, database);
            List<T> result = jdbcUtils.executeQuery(conn, sqlForUpdate, rowMapper, params);

            completeOperation(context, op);
            return result;
        } catch (SQLException | DataAccessException e) {
            throw new DeadlockException("Operation failed: " + e.getMessage());
        }
    }

    public int executeUpdate(String txId, String database, String tableName, String sql, Object primaryKey,
                             Map<String, Object> beforeImage, Map<String, Object> afterImage, Object... params) throws DeadlockException {

        TransactionContext context = getContext(txId);
        TransactionOperation op = new TransactionOperation(txId, TransactionOperation.OperationType.UPDATE,
                database, tableName, primaryKey, beforeImage, afterImage, sql, params);

        checkLockOrThrow(context, op);

        try {
            Connection conn = getActiveConnection(context, database);
            int rows = jdbcUtils.executeUpdate(conn, sql, params);

            if (rows > 0) {
                completeOperation(context, op);
            }
            return rows;
        } catch (SQLException | DataAccessException e) {
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

        checkLockOrThrow(context, initialOp);

        try {
            Connection conn = getActiveConnection(context, database);
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

            return generatedId != null ? generatedId : 0;

        } catch (SQLException | DataAccessException e) {
            throw new DeadlockException("Operation failed: " + e.getMessage());
        }
    }

    public Map<String, Object> fetchBeforeImage(String database, String tableName, Long primaryKey) {
        try {
            String pkCol = SchemaUtils.getPrimaryKeyColumn(tableName);
            String sql = "SELECT * FROM " + tableName + " WHERE " + pkCol + " = ?";

            Connection conn = getConnection(database);
            try {
                List<Map<String, Object>> result = jdbcUtils.executeQuery(conn, sql, jdbcUtils.getGenericRowMapper(), primaryKey);
                if (result.isEmpty()) {
                    throw new RuntimeException("Record not found: " + tableName + " " + pkCol + "=" + primaryKey);
                }
                return result.get(0);
            } finally {
                conn.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch before image from " + tableName, e);
        }
    }

    public void closeConnections(String txId) {
        TransactionContext context = activeTransactions.remove(txId);
        if (context != null) {
            closeSafely(context.getInventoryConnection());
            closeSafely(context.getOrderConnection());
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

    private void checkLockOrThrow(TransactionContext context, TransactionOperation op) throws DeadlockException {
        if (!scheduler.canExecute(context.getTransaction(), op)) {
            throw new DeadlockException("Transaction must abort due to lock conflict: " + context.getTransactionId());
        }
    }

    private void completeOperation(TransactionContext context, TransactionOperation op) {
        op.setExecuted(true);
        operationLog.logOperation(op);
        scheduler.onOperationComplete(context.getTransaction(), op);
    }

    private void closeSafely(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }
}