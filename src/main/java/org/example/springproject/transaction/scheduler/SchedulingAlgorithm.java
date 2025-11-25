package org.example.springproject.transaction.scheduler;

import org.example.springproject.models.Transaction;
import org.example.springproject.transaction.TransactionOperation;
import org.springframework.expression.Operation;

/**
 * Interface for transaction scheduling algorithms
 * This will be implemented later with specific algorithms (locks, timestamps, etc.)
 */
public interface SchedulingAlgorithm {

    /**
     * Check if an operation can be executed by a transaction
     *
     * @param transaction The transaction attempting to execute
     * @param operation   The operation to be executed
     * @return true if operation can proceed, false if it must wait or be aborted
     */
    boolean canExecute(Transaction transaction, TransactionOperation operation);

    /**
     * Called when an operation completes successfully
     *
     * @param transaction The transaction that executed the operation
     * @param operation   The completed operation
     */
    void onOperationComplete(Transaction transaction, TransactionOperation operation);

    /**
     * Called when a transaction commits
     *
     * @param transaction The committing transaction
     */
    void onTransactionCommit(Transaction transaction);

    /**
     * Called when a transaction aborts
     *
     * @param transaction The aborting transaction
     */
    void onTransactionAbort(Transaction transaction);

    /**
     * Get the name of this scheduling algorithm
     *
     * @return Algorithm name (e.g., "Two-Phase Locking", "Timestamp Ordering")
     */
    String getAlgorithmName();
}
