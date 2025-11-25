package org.example.springproject.transaction;

import org.example.springproject.models.Transaction;
import org.example.springproject.util.OperationLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Manages rollback of transactions using a log-based approach (Undo/Compensation).
 * This ensures Atomicity by restoring data to its "before image" state.
 */
@Component
public class RollbackManager {

    @Autowired
    private VersionManager versionManager;
    @Autowired
    private final OperationLog operationLog;
    private final Map<String, JdbcTemplate> jdbcTemplates;

    @Autowired
    public RollbackManager(VersionManager versionManager, OperationLog operationLog,
                           @Qualifier("jdbcTemplates") Map<String, JdbcTemplate> templates) {
        this.versionManager = versionManager;
        this.operationLog = operationLog;
        this.jdbcTemplates = templates;
    }

    /**
     * Rollback a transaction by undoing all its executed operations in reverse order.
     */
    public void rollback(Transaction transaction) {
        String transactionId = transaction.getTransactionId();
        System.out.println("Initiating application-level rollback for transaction: " + transactionId);

        try {
            List<TransactionOperation> operations = operationLog.getOperationsInReverseOrder(transactionId);

            for (TransactionOperation operation : operations) {
                if (operation.isExecuted() && !operation.isRolledBack()) {
                    undoOperation(operation);
                }
            }
            if (versionManager != null) {
                versionManager.invalidateVersions(transactionId);
            }

            operationLog.logAbort(transactionId, "Transaction rolled back due to failure or deadlock.");
            transaction.setStatus(Transaction.TransactionStatus.ROLLED_BACK);

        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to complete application-level rollback for transaction: " + transactionId);
            e.printStackTrace();
            throw new RuntimeException("Failed to rollback transaction: " + transactionId, e);
        }
    }

    /**
     * Undo a single operation based on its type (Compensation mechanism).
     */
    private void undoOperation(TransactionOperation operation) {
        if (operation.getType() == TransactionOperation.OperationType.SELECT
                || operation.getType() == TransactionOperation.OperationType.SELECT_FOR_UPDATE) {
            operation.setRolledBack(true);
            return;
        }

        JdbcTemplate jdbcTemplate = jdbcTemplates.get(operation.getDatabase());

        if (jdbcTemplate == null) {
            System.err.println("Available JDBC Templates: " + jdbcTemplates.keySet());
            throw new RuntimeException("No JdbcTemplate found for database: " + operation.getDatabase());
        }

        try {
            switch (operation.getType()) {
                case INSERT:
                    undoInsert(jdbcTemplate, operation);
                    break;
                case UPDATE:
                    undoUpdate(jdbcTemplate, operation);
                    break;
                case DELETE:
                    undoDelete(jdbcTemplate, operation);
                    break;
                default:
                    break;
            }

            operation.setRolledBack(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to undo operation: " + operation.getOperationId(), e);
        }
    }

    private void undoInsert(JdbcTemplate jdbcTemplate, TransactionOperation operation) {
        Object primaryKey = operation.getPrimaryKeyValue();
        if (primaryKey == null) {
            throw new RuntimeException("Cannot undo INSERT without captured primary key value: " + operation.getSql());
        }
        String pkName = getPrimaryKeyColumnName(operation.getTableName());
        String sql = String.format("DELETE FROM %s WHERE %s = ?", operation.getTableName(), pkName);
        jdbcTemplate.update(sql, primaryKey);
        System.out.println("UNDO (DELETE): " + sql + " WHERE " + pkName + " = " + primaryKey);
    }

    private void undoUpdate(JdbcTemplate jdbcTemplate, TransactionOperation operation) {
        Map<String, Object> beforeImage = operation.getBeforeImage();
        Object primaryKey = operation.getPrimaryKeyValue();

        if (beforeImage == null || beforeImage.isEmpty() || primaryKey == null) {
            System.err.println("WARNING: Cannot undo UPDATE without full before-image or primary key. Operation: " + operation.getSql());
            return;
        }

        StringBuilder sql = new StringBuilder("UPDATE ").append(operation.getTableName()).append(" SET ");

        List<Object> values = new java.util.ArrayList<>();
        boolean first = true;
        String pkName = getPrimaryKeyColumnName(operation.getTableName());

        for (Map.Entry<String, Object> entry : beforeImage.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(pkName)) {
                if (!first) sql.append(", ");
                sql.append(entry.getKey()).append(" = ?");
                values.add(entry.getValue());
                first = false;
            }
        }

        sql.append(" WHERE ").append(pkName).append(" = ?");
        values.add(primaryKey);

        jdbcTemplate.update(sql.toString(), values.toArray());
        System.out.println("UNDO (UPDATE): " + sql + " with values: " + values);
    }

    private void undoDelete(JdbcTemplate jdbcTemplate, TransactionOperation operation) {
        Map<String, Object> beforeImage = operation.getBeforeImage();

        if (beforeImage == null || beforeImage.isEmpty()) {
            System.err.println("WARNING: Cannot undo DELETE without before-image. Operation: " + operation.getSql());
            return;
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(operation.getTableName()).append(" (");
        StringBuilder values = new StringBuilder("VALUES (");

        List<Object> params = new java.util.ArrayList<>();
        boolean first = true;
        for (Map.Entry<String, Object> entry : beforeImage.entrySet()) {
            if (!first) {
                sql.append(", ");
                values.append(", ");
            }
            sql.append(entry.getKey());
            values.append("?");
            params.add(entry.getValue());
            first = false;
        }

        sql.append(") ").append(values).append(")");

        jdbcTemplate.update(sql.toString(), params.toArray());
        System.out.println("UNDO (INSERT): " + sql);
    }

    private String getPrimaryKeyColumnName(String tableName) {
        return switch (tableName.toLowerCase()) {
            case "products" -> "product_id";
            case "inventory_transactions" -> "transaction_id";
            case "orders" -> "order_id";
            case "payments" -> "payment_id";
            case "customers" -> "customer_id";
            default -> "id";
        };
    }
}