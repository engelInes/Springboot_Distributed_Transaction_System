package org.example.springproject.transaction;

import java.util.Map;

public class TransactionOperation {
    private final String transactionId;
    private final String operationType;
    private final String tableName;
    private final Map<String, Object> beforeSnapshot;
    private final Map<String, Object> afterSnapshot;

    public TransactionOperation(String transactionId, String operationType, String tableName, Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot) {
        this.transactionId = transactionId;
        this.operationType = operationType;
        this.tableName = tableName;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getTableName() {
        return tableName;
    }

    public Map<String, Object> getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public Map<String, Object> getAfterSnapshot() {
        return afterSnapshot;
    }
}
