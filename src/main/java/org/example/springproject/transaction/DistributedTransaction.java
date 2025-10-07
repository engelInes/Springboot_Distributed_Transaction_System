package org.example.springproject.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.core.DeadlockException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.example.springproject.transaction.TransactionContext;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DistributedTransaction {
    /**
     *instance connected to inventory database in order to manually run SQL statements
     */
    private final JdbcTemplate inventoryJdbcTemplate;
    /**
     *instance connected to order database
     */
    private final JdbcTemplate orderJdbcTemplate;

    /**
     * used for persisting transaction logs of a database
     */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * maps table names to their corresponding read/write locks
     */
    private final Map<String, ReentrantReadWriteLock> tableLocks = new ConcurrentHashMap<>();
    /**
     * transaction logs storing all sql operations
     */
    private final Map<String, List<TransactionOperation>> transactionLog = new ConcurrentHashMap<>();
    /**
     * maps all active transactions
     */
    private final Map<String, TransactionContext> activeTransactions = new ConcurrentHashMap<>();
    /**
     * maps table names to which transaction(s) it currently holds locks for
     * used for deadlock detection and cleanup
     */
    private final Map<String, Set<String>> transactionLocks = new ConcurrentHashMap<>();
    /**
     * a key represents a transaction waiting for locks held by the transactions
     */
    private final Map<String, Set<String>> waitForGraph = new ConcurrentHashMap<>();

    public DistributedTransaction(
            @Qualifier("inventoryJdbcTemplate") JdbcTemplate inventoryJdbcTemplate,
            @Qualifier("orderJdbcTemplate") JdbcTemplate orderJdbcTemplate){
        this.inventoryJdbcTemplate=inventoryJdbcTemplate;
        this.orderJdbcTemplate=orderJdbcTemplate;

        initializeTableLocks();
    }

    private void initializeTableLocks(){
        String[] tables = {"products", "suppliers", "orders", "inventory_transactions", "customers", "payments"};
        for(String table:tables){
            tableLocks.put(table, new ReentrantReadWriteLock(true));
        }
    }

    /**
     * starts a new transaction by creating a unique transaction ID
     * and opens connections to both databases
     *
     * @return transaction ID
     */
    public String beginTransaction(){
        String transactionId = UUID.randomUUID().toString();
        TransactionContext context = new TransactionContext(transactionId);

        try{
            Connection inventoryConnection = inventoryJdbcTemplate.getDataSource().getConnection();
            Connection orderConnection = orderJdbcTemplate.getDataSource().getConnection();
            inventoryConnection.setAutoCommit(false);
            orderConnection.setAutoCommit(false);

            context.setInventoryConnection(inventoryConnection);
            context.setOrderConnection(orderConnection);

            activeTransactions.put(transactionId,context);
            transactionLog.put(transactionId, new ArrayList<>());
            transactionLocks.put(transactionId, new HashSet<>());

            System.out.println(transactionId.substring(0,8) + "transaction started");
            return transactionId;
        }catch (SQLException e){
            throw new RuntimeException("failed to start");
        }
    }

    /**
     * checks whether a deadlock exists in the graph
     * @param node     the starting transaction ID
     * @param visited  set of transactions already visited
     * @return true if a deadlock is found, false otherwise
     */
    private boolean hasCycle(String node, Set<String> visited){
        if (visited.contains(node)) {
            return true;
        }

        visited.add(node);
        Set<String> neighbors = waitForGraph.get(node);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                if (hasCycle(neighbor, new HashSet<>(visited))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * checks for deadlocks before acquiring a table lock
     * @param transactionId ID of the current transaction
     * @param requestedTable name of the table the transaction is trying to lock
     */
    private void checkForDeadlock(String transactionId, String requestedTable){
        for(Map.Entry<String, Set<String>> entry: transactionLocks.entrySet()){
            String tableId = entry.getKey();
            if(!tableId.equals(transactionId) && entry.getValue().contains(requestedTable)){
                waitForGraph.computeIfAbsent(transactionId, k-> new HashSet<>()).add(tableId);
                if(hasCycle(transactionId, new HashSet<>())){
                    waitForGraph.remove(transactionId);
                    throw new IllegalStateException();
                }

            }
        }
    }

    /**
     * acquires read/write lock on a table for a transaction
     * @param transactionId ID of the active transaction
     * @param tableName     name of the table to lock
     * @param isWrite       true for write lock, false for read lock
     */
    public void acquireLock(String transactionId, String tableName, boolean isWrite){
        TransactionContext transactionContext=activeTransactions.get(transactionId);
        if(transactionContext == null){
            throw new IllegalStateException("transaction id not found");
        }

        checkForDeadlock(transactionId, tableName);

        ReentrantReadWriteLock lock=tableLocks.get(tableName);
        if(lock == null){
            throw new IllegalStateException("table lock not found");
        }

        try{
            System.out.println(transactionId.substring(0,8) + "acquire lock" + (isWrite?"write": "read") + tableName);

            if(isWrite){
                lock.writeLock().lock();
                transactionContext.addWriteLock(tableName, lock.writeLock());
            }
            else{
                lock.readLock().lock();
                transactionContext.addReadLock(tableName, lock.readLock());
            }

            transactionLocks.get(transactionId).add(tableName);
            System.out.println(transactionId.substring(0,8) +"lock acquired" + tableName);
        }catch (Exception e){
            throw new RuntimeException("failed to acquire lock");
        }
    }

    /**
     * logs a database operation
     * used during rollback
     *
     * @param transactionId transaction ID
     * @param operationType type of operation
     * @param tableName     name of the table
     * @param beforeSnapshot state before operation (for rollback)
     * @param afterSnapshot  state after operation (for rollback)
     */
    private void logOperation(String transactionId, String operationType, String tableName, Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot){
        TransactionOperation op = new TransactionOperation(transactionId, operationType, tableName, beforeSnapshot, afterSnapshot);
        transactionLog.get(transactionId).add(op);
    }

    private JdbcTemplate getJdbcTemplateForTable(String tableName){
        if(tableName.equals("products") || tableName.equals("suppliers") || tableName.equals("inventory_transactions") || tableName.equals("customers")){
            return inventoryJdbcTemplate;
        }
        else {
            return orderJdbcTemplate;
        }
    }

    /**
     * executes a SELECT operation in a transaction and applies a read lock on the table.
     *
     * @param transactionId ID of the active transaction
     * @param sql           SQL SELECT query
     * @param rowMapper     mapper to convert result rows into objects
     * @param tableName     target table
     * @param parameters    query parameters
     * @param <T>           result type
     * @return list of result objects
     */
    public<T> List<T> executeSelect(String transactionId, String sql, RowMapper<T> rowMapper, String tableName, Object ... parameters){
        acquireLock(transactionId, tableName, false);
        TransactionContext context = activeTransactions.get(transactionId);
        JdbcTemplate template = getJdbcTemplateForTable(tableName);

        try{
            List<T> results = template.query(sql, rowMapper, parameters);

            logOperation(transactionId, "SELECT", tableName, null, "retrieved " + results.size());
            System.out.println(transactionId.substring(0, 8) +  "SELECT on " +
                    tableName + " returned " + results.size() + " rows");
            return results;
        }catch(Exception e){
            throw new RuntimeException("failed to execute select" + tableName);
        }
    }

    public Set<String> getActiveTransactions(){
        return new HashSet<>(activeTransactions.keySet());
    }

    /**
     * executes an INSERT operation in a transaction with a write lock.
     *
     * @param transactionId ID of the transaction
     * @param sql           INSERT statement
     * @param tableName     target table
     * @param data          data inserted (for rollback)
     * @param parameters    query parameters
     * @return number of affected rows
     */
    public int executeInsert(String transactionId, String sql, String tableName, Map<String, Object> data, Object ... parameters){
        acquireLock(transactionId, tableName, true);
        JdbcTemplate template = getJdbcTemplateForTable(tableName);
        try{
            int affectedRows = template.update(sql, parameters);
            logOperation(transactionId, "INSERT", tableName, null, data);
            System.out.println(transactionId.substring(0, 8) + "INSERT on " +
                    tableName + affectedRows + " rows");

            return affectedRows;

        } catch (Exception e) {
            throw new RuntimeException("insert failed");
        }
    }

    private void persistTransactionLog(String transactionId, String operationType, String tableName, Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot){
        try{
            String beforeJson = beforeSnapshot!= null ? objectMapper.writeValueAsString(beforeSnapshot) : null;
            String afterJson = afterSnapshot!= null ? objectMapper.writeValueAsString(afterSnapshot) : null;
        }catch(Exception e){

        }
    }
    public int executeUpdate(String transactionId, String sql, String tableName, String primaryKeyColumn, Object primaryKeyValue, Map<String, Object> oldData, Object ... parameters){
        acquireLock(transactionId, tableName, true);
        JdbcTemplate template = getJdbcTemplateForTable(tableName);

        try {
            int affectedRows = template.update(sql, parameters);
            logOperation(transactionId, "UPDATE", tableName, oldData, null);
            persistTransactionLog(transactionId, "UPDATE", tableName, oldData, null);

            System.out.println(transactionId.substring(0, 8) + "UPDATE on " +
                    tableName + " affected " + affectedRows + " rows");
            return affectedRows;
        }catch (Exception e){
            throw new RuntimeException("update failed");
        }
    }

    public int executeDelete(String transactionId, String sql, String tableName,
                             Map<String, Object> deletedData, Object... params) {
        acquireLock(transactionId, tableName, true); // Write lock for DELETE

        JdbcTemplate template = getJdbcTemplateForTable(tableName);

        try {
            int affectedRows = template.update(sql, params);

            // Log deleted data for rollback (need to re-INSERT if rollback)
            logOperation(transactionId, "DELETE", tableName, deletedData, null);

            persistTransactionLog(transactionId, "DELETE", tableName, deletedData, null);

            System.out.println(transactionId.substring(0, 8) + "DELETE on " +
                    tableName + " affected " + affectedRows + " rows");

            return affectedRows;
        } catch (Exception e) {
            throw new RuntimeException("DELETE failed on " + tableName, e);
        }
    }

    private void rollbackOperation(TransactionOperation op) {
        try {
            JdbcTemplate template = getJdbcTemplateForTable(op.getTableName());

            switch (op.getOperationType()) {
                case "INSERT":
                    System.out.println("  Rolling back INSERT - would delete inserted data");
                    break;

                case "UPDATE":
                    System.out.println("  Rolling back UPDATE - would restore old data");
                    break;

                case "DELETE":
                    System.out.println("  Rolling back DELETE - would re-insert deleted data");
                    break;
            }
        } catch (Exception e) {
            System.err.println("Failed to rollback operation: " + op.getOperationType() +
                    " on " + op.getTableName());
        }
    }

    private void cleanup(String transactionId, TransactionContext context) {
        try {
            context.releaseAllLocks();

            if (context.getInventoryConnection() != null) {
                context.getInventoryConnection().close();
            }
            if (context.getOrderConnection() != null) {
                context.getOrderConnection().close();
            }

            activeTransactions.remove(transactionId);
            transactionLog.remove(transactionId);
            transactionLocks.remove(transactionId);
            waitForGraph.remove(transactionId);

            System.out.println(transactionId.substring(0, 8) + "cleaned up");

        } catch (SQLException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
    public void rollback(String transactionId){
        TransactionContext transactionContext = activeTransactions.get(transactionId);
        if(transactionContext == null){
            throw new IllegalStateException("transaction not found");
        }

        try{
            System.out.println(transactionId.substring(0, 8) + " rolling back");
            List<TransactionOperation> operations = transactionLog.get(transactionId);
            if(operations!=null){
                for(int i=operations.size()-1;i>=0;i--){
                    TransactionOperation operation = operations.get(i);
                    rollbackOperation(operation);
                }
            }

            if(transactionContext.getInventoryConnection() != null){
                transactionContext.getInventoryConnection().rollback();
            }
            if(transactionContext.getOrderConnection() != null){
                transactionContext.getOrderConnection().rollback();
            }

            System.out.println(transactionId.substring(0, 8) + "transaction rolled back");
        } catch (SQLException e) {
            throw new RuntimeException("rollback failed");
        } finally {
            cleanup(transactionId, transactionContext);
        }

    }
    public void commit(String transactionId){
        TransactionContext transactionContext = activeTransactions.get(transactionId);
        if(transactionContext == null){
            throw new IllegalStateException("transaction id not found");
        }
        try{
            System.out.println(transactionId.substring(0, 8) + "commit");
            if(transactionContext.getInventoryConnection()!=null){
                transactionContext.getInventoryConnection().commit();
            }
            if(transactionContext.getOrderConnection()!=null){
                transactionContext.getOrderConnection().commit();
            }
            System.out.println("transaction committed");
        }catch (SQLException e){
            System.err.println(transactionId.substring(0, 8) + "commit failed, rolling back");
            rollback(transactionId);
            throw new RuntimeException("commit failed, rolled back transaction", e);
        } finally {
            cleanup(transactionId, transactionContext);
        }
    }

}
