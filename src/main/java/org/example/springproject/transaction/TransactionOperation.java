package org.example.springproject.transaction;

import java.util.Map;
import java.util.UUID;

/**
 * Represents a single SQL operation within a distributed transaction.
 * Contains data necessary for scheduling, logging, and rollback.
 */
public class TransactionOperation {

    public enum OperationType {
        INSERT, UPDATE, DELETE, SELECT, SELECT_FOR_UPDATE
    }

    private final String operationId;
    private final String transactionId;
    private final OperationType type;
    private final String database;
    private final String tableName;
    private final Object primaryKeyValue;
    private final Map<String, Object> beforeImage;
    private final Map<String, Object> afterImage;
    private final String sql;
    private final Object[] params;

    private boolean isExecuted = false;
    private boolean isRolledBack = false;

    public TransactionOperation(String transactionId, OperationType type, String database, String tableName,
                                Object primaryKeyValue, Map<String, Object> beforeImage, Map<String, Object> afterImage,
                                String sql, Object... params) {
        this.operationId = UUID.randomUUID().toString();
        this.transactionId = transactionId;
        this.type = type;
        this.database = database;
        this.tableName = tableName;
        this.primaryKeyValue = primaryKeyValue;
        this.beforeImage = beforeImage;
        this.afterImage = afterImage;
        this.sql = sql;
        this.params = params;
    }

    public boolean isWriteOperation() {
        return type == OperationType.INSERT || type == OperationType.UPDATE || type == OperationType.DELETE || type == OperationType.SELECT_FOR_UPDATE;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public OperationType getType() {
        return type;
    }

    public String getDatabase() {
        return database;
    }

    public String getTableName() {
        return tableName;
    }

    public Object getPrimaryKeyValue() {
        return primaryKeyValue;
    }

    public Map<String, Object> getBeforeImage() {
        return beforeImage;
    }

    public Map<String, Object> getAfterImage() {
        return afterImage;
    }

    public String getSql() {
        return sql;
    }

    public Object[] getParams() {
        return params;
    }

    public boolean isExecuted() {
        return isExecuted;
    }

    public boolean isRolledBack() {
        return isRolledBack;
    }

    public void setExecuted(boolean executed) {
        isExecuted = executed;
    }

    public void setRolledBack(boolean rolledBack) {
        isRolledBack = rolledBack;
    }

    public String getResourceKey() {
        if (primaryKeyValue != null) {
            return String.format("%s.%s.%s", database, tableName, primaryKeyValue);
        } else {
            return String.format("%s.%s.*", database, tableName);
        }
    }
}