package org.example.springproject.transaction;

import org.example.springproject.exceptions.DeadlockException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implements a Wait-For graph to detect deadlocks using Depth-First Search (DFS).
 * A deadlock occurs if a cycle is found in the graph.
 */
@Component
public class DeadlockDetector {

    private final ConcurrentMap<String, String> waitForGraph;

    public DeadlockDetector() {
        this.waitForGraph = new ConcurrentHashMap<>();
    }

    /**
     * Adds an edge T_waiting -> T_holding to the wait-for graph.
     */
    public void addWaitFor(String waitingTxId, String holdingTxId) {
        if (!waitingTxId.equals(holdingTxId)) {
            waitForGraph.put(waitingTxId, holdingTxId);
        }
    }

    /**
     * Removes all edges involving the given transaction ID.
     */
    public void removeTransaction(String txId) {
        waitForGraph.remove(txId);

        waitForGraph.entrySet().removeIf(entry -> entry.getValue().equals(txId));
    }

    /**
     * Detects a cycle in the wait-for graph and throws a DeadlockException if one is found.
     */
    public void detectDeadlock() throws DeadlockException {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String txId : waitForGraph.keySet()) {
            List<String> cycle = findCycle(txId, visited, recursionStack, new ArrayList<>());
            if (!cycle.isEmpty()) {
                throw new DeadlockException("Deadlock detected involving transactions: " + cycle);
            }
        }
    }

    private List<String> findCycle(String currentTx, Set<String> visited, Set<String> recursionStack, List<String> path) {
        if (recursionStack.contains(currentTx)) {
            int index = path.indexOf(currentTx);
            return path.subList(index, path.size());
        }

        if (visited.contains(currentTx)) {
            return Collections.emptyList();
        }

        visited.add(currentTx);
        recursionStack.add(currentTx);
        path.add(currentTx);

        String nextTx = waitForGraph.get(currentTx);

        if (nextTx != null) {
            List<String> cycle = findCycle(nextTx, visited, recursionStack, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }

        recursionStack.remove(currentTx);
        path.remove(path.size() - 1);
        return Collections.emptyList();
    }
}
