package org.example.springproject.transaction;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages version tracking for transactions.
 * Currently serves as a safeguard to ensure invalid versions from rolled-back
 * transactions are cleared from any application-level caches.
 */
@Component
public class VersionManager {

    private static final Logger LOGGER = Logger.getLogger(VersionManager.class.getName());

    private final ConcurrentHashMap<String, Set<String>> activeVersions = new ConcurrentHashMap<>();

    /**
     * Registers that a transaction has created a new version of a resource.
     * Call this from your StoreService or DistributedTransaction if you implement
     * caching later.
     */
    public void trackVersion(String transactionId, String tableName, Object id) {
        String resourceKey = tableName + ":" + id;
        activeVersions.computeIfAbsent(transactionId, k -> ConcurrentHashMap.newKeySet())
                .add(resourceKey);
    }

    /**
     * Called by RollbackManager when a transaction aborts.
     * This invalidates any "dirty" versions created by this transaction.
     * * Since the RollbackManager.undoUpdate() restores the actual DB row to its
     * previous state (including the old version number), this method primarily
     * cleans up internal tracking.
     */
    public void invalidateVersions(String transactionId) {
        Set<String> resources = activeVersions.remove(transactionId);

        if (resources != null && !resources.isEmpty()) {
            LOGGER.warning(String.format("Invalidating potential dirty reads for Transaction %s: %s",
                    transactionId, resources));
            // logic to evict these keys from a second-level cache would go here
        } else {
            LOGGER.info("No active version tracking found for transaction: " + transactionId);
        }
    }
}