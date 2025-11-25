package org.example.springproject.util;

import org.example.springproject.exceptions.DeadlockException;
import org.springframework.stereotype.Component;

@Component
public class TransactionRetryTemplate {

    private static final int MAX_RETRIES = 3;

    public void execute(Runnable action, String failureMessage) {
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                action.run();
                return;
            } catch (DeadlockException e) {
                retries++;
                if (retries >= MAX_RETRIES) {
                    throw new RuntimeException(failureMessage, e);
                }
                sleepBackoff(retries);
            }
        }
        throw new RuntimeException(failureMessage);
    }

    private void sleepBackoff(int retry) {
        try {
            Thread.sleep((long) Math.pow(2, retry) * 100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
