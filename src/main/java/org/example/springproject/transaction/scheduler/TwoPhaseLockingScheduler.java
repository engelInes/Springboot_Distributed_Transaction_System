package org.example.springproject.transaction.scheduler;

import org.example.springproject.exceptions.DeadlockException;
import org.example.springproject.models.Transaction;
import org.example.springproject.transaction.DeadlockDetector;
import org.example.springproject.transaction.TransactionOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class TwoPhaseLockingScheduler implements SchedulingAlgorithm {

    private final DeadlockDetector deadlockDetector;

    public enum LockType {
        SHARED,
        EXCLUSIVE
    }

    private static class Lock {
        private final String transactionId;
        private final LockType type;
        private final String resourceKey;

        public Lock(String transactionId, LockType type, String resourceKey) {
            this.transactionId = transactionId;
            this.type = type;
            this.resourceKey = resourceKey;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public LockType getType() {
            return type;
        }
    }

    private final Map<String, List<Lock>> lockTable;

    private final Map<String, LockPhase> transactionPhase;

    public enum LockPhase {
        GROWING,
        SHRINKING
    }

    @Autowired
    public TwoPhaseLockingScheduler(DeadlockDetector deadlockDetector) {
        this.deadlockDetector = deadlockDetector;
        this.lockTable = new ConcurrentHashMap<>();
        this.transactionPhase = new ConcurrentHashMap<>();
    }

    @Override
    public boolean canExecute(Transaction transaction, TransactionOperation operation) {
        String txId = transaction.getTransactionId();
        String resourceKey = operation.getResourceKey();

        transactionPhase.putIfAbsent(txId, LockPhase.GROWING);
        if (transactionPhase.get(txId) == LockPhase.SHRINKING) {
            throw new IllegalStateException("2PL Violation: Transaction " + txId + " attempted to acquire lock in shrinking phase.");
        }

        LockType requiredLock = operation.isWriteOperation() ? LockType.EXCLUSIVE : LockType.SHARED;

        return tryAcquireLock(txId, requiredLock, resourceKey);
    }

    @Override
    public String getAlgorithmName() {
        return "Two-Phase Locking (2PL)";
    }

    @Override
    public void onTransactionCommit(Transaction transaction) {
        String txId = transaction.getTransactionId();
        releaseLocks(txId);
        deadlockDetector.removeTransaction(txId);
        transactionPhase.remove(txId);
    }

    @Override
    public void onTransactionAbort(Transaction transaction) {
        String txId = transaction.getTransactionId();
        releaseLocks(txId);
        deadlockDetector.removeTransaction(txId);
        transactionPhase.remove(txId);
    }

    @Override
    public void onOperationComplete(Transaction transaction, TransactionOperation operation) {
    }

    /**
     * Get current lock table (for debugging/monitoring)
     */
    public Map<String, List<Lock>> getLockTable() {
        return new HashMap<>(lockTable);
    }

    /**
     * Release all locks held by a transaction
     */
    private void releaseLocks(String txId) {
        synchronized (lockTable) {
            transactionPhase.put(txId, LockPhase.SHRINKING);

            lockTable.values().forEach(locks ->
                    locks.removeIf(lock -> lock.getTransactionId().equals(txId))
            );

            lockTable.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }

    private boolean isCompatible(LockType requestedLock, List<Lock> existingLocks) {
        if (existingLocks.isEmpty()) {
            return true;
        }
        for (Lock existingLock : existingLocks) {
            if (requestedLock == LockType.EXCLUSIVE || existingLock.getType() == LockType.EXCLUSIVE) {
                return false;
            }
        }
        return true;
    }

    private boolean tryUpgradeLock(String txId, String resourceKey) {
        synchronized (lockTable) {
            List<Lock> locks = lockTable.get(resourceKey);

            boolean otherTransactionHasLock = locks.stream()
                    .anyMatch(l -> !l.getTransactionId().equals(txId));

            if (otherTransactionHasLock) {
                return false;
            }

            Lock myLock = locks.stream()
                    .filter(l -> l.getTransactionId().equals(txId))
                    .findFirst()
                    .orElse(null);

            if (myLock != null) {
                locks.remove(myLock);

                locks.add(new Lock(txId, LockType.EXCLUSIVE, resourceKey));
                return true;
            }

            return false;
        }
    }

    private boolean tryAcquireLock(String txId, LockType lockType, String resourceKey) {
        synchronized (lockTable) {
            List<Lock> existingLocks = lockTable.getOrDefault(resourceKey, new ArrayList<>());

            List<Lock> otherLocks = existingLocks.stream()
                    .filter(lock -> !lock.getTransactionId().equals(txId))
                    .collect(Collectors.toList());

            Optional<Lock> selfLock = existingLocks.stream()
                    .filter(lock -> lock.getTransactionId().equals(txId))
                    .findFirst();

            if (selfLock.isPresent()) {
                if (selfLock.get().getType() == LockType.EXCLUSIVE) {
                    return true;
                }
                if (selfLock.get().getType() == LockType.SHARED && lockType == LockType.SHARED) {
                    return true;
                }
                if (selfLock.get().getType() == LockType.SHARED && lockType == LockType.EXCLUSIVE) {
                    return tryUpgradeLock(txId, resourceKey);
                }
            }

            if (isCompatible(lockType, otherLocks)) {
                Lock newLock = new Lock(txId, lockType, resourceKey);
                lockTable.computeIfAbsent(resourceKey, k -> new ArrayList<>()).add(newLock);
                deadlockDetector.removeTransaction(txId);
                return true;
            } else {
                for (Lock lock : otherLocks) {
                    deadlockDetector.addWaitFor(txId, lock.getTransactionId());
                }

                try {
                    deadlockDetector.detectDeadlock();
                    return false;
                } catch (DeadlockException e) {
                    throw e;
                }
            }
        }
    }
}

