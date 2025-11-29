package org.example.springproject.util;

import org.example.springproject.transaction.TransactionOperation;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Stores the log of all operations performed by active transactions,
 * including before/after images for rollback (Atomicity).
 */
@Component
public class OperationLog {

    private final Map<String, List<TransactionOperation>> transactionLogs = new ConcurrentHashMap<>();

    /**
     * Add a new operation to the transaction's log.
     */
    public void logOperation(TransactionOperation operation) {
        System.out.println(">>> DEBUG [OperationLog]: Logging operation - tx=" + operation.getTransactionId() +
                " db=" + operation.getDatabase() +
                " table=" + operation.getTableName() +
                " type=" + operation.getOperationType());

        transactionLogs.computeIfAbsent(operation.getTransactionId(), k -> new CopyOnWriteArrayList<>()).add(operation);

        int count = transactionLogs.get(operation.getTransactionId()).size();
        System.out.println(">>> DEBUG [OperationLog]: Operation logged successfully. Total operations for tx=" +
                operation.getTransactionId() + ": " + count);
    }

    /**
     * Get all operations for a transaction in reverse order for rollback.
     */
    public List<TransactionOperation> getOperationsInReverseOrder(String transactionId) {
        List<TransactionOperation> operations = transactionLogs.getOrDefault(transactionId, Collections.emptyList());
        List<TransactionOperation> reversed = new CopyOnWriteArrayList<>(operations);
        Collections.reverse(reversed);

        System.out.println(">>> DEBUG [OperationLog]: Retrieved " + reversed.size() + " operations in reverse order for tx=" + transactionId);

        return reversed;
    }

    /**
     * Clear the log after a transaction commits or aborts/rolls back completely.
     */
    public void clearLog(String transactionId) {
        List<TransactionOperation> removed = transactionLogs.remove(transactionId);
        if (removed != null) {
            System.out.println(">>> DEBUG [OperationLog]: Cleared " + removed.size() + " operations for tx=" + transactionId);
        } else {
            System.out.println(">>> DEBUG [OperationLog]: No operations to clear for tx=" + transactionId);
        }
    }

    public void logAbort(String transactionId, String reason) {
        System.err.println("TRANSACTION ABORT LOG: " + transactionId + " - Reason: " + reason);
    }

    public void logCommit(String transactionId) {
        System.out.println("TRANSACTION COMMIT LOG: " + transactionId);
    }

    /**
     * Get the list of databases affected by a transaction
     */
    public List<String> getAffectedDatabases(String transactionId) {
        System.out.println(">>> DEBUG [OperationLog]: Getting affected databases for tx=" + transactionId);

        List<TransactionOperation> operations = transactionLogs.getOrDefault(transactionId, Collections.emptyList());
        System.out.println(">>> DEBUG [OperationLog]: Found " + operations.size() + " total operations");

        List<String> affectedDbs = operations.stream()
                .map(TransactionOperation::getDatabase)
                .distinct()
                .collect(Collectors.toList());

        System.out.println(">>> DEBUG [OperationLog]: Affected databases: " + affectedDbs);

        return affectedDbs;
    }
}
