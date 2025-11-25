package org.example.springproject;

import org.example.springproject.service.StoreService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DebugRunner implements CommandLineRunner {

    private final StoreService storeService;

    public DebugRunner(StoreService storeService) {
        this.storeService = storeService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=========================================");
        System.out.println("DEBUG RUNNER STARTED - WAITING FOR DEBUGGER");
        System.out.println("=========================================");

        try {
            System.out.println("1. Testing placeOrder...");
            storeService.placeOrder(1, 1, 5);
        } catch (Exception e) {
            System.err.println("placeOrder failed: " + e.getMessage());
        }

        try {
            System.out.println("2. Testing restockFromSupplier...");
            storeService.restockFromSupplier(1, 50);
        } catch (Exception e) {
            System.err.println("restockFromSupplier failed: " + e.getMessage());
        }

        try {
            System.out.println("3. Testing cancelOrder...");
            storeService.cancelOrder(999);
        } catch (Exception e) {
            System.err.println("cancelOrder failed: " + e.getMessage());
        }

        System.out.println("=========================================");
        System.out.println("DEBUG RUNNER FINISHED");
        System.out.println("=========================================");
    }
}
