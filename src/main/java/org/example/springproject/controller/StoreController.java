package org.example.springproject.controller;

import org.example.springproject.service.StoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    /**
     * Place a new order
     * POST /api/store/orders
     * Body: { "customerId": 1, "productId": 1, "quantity": 2 }
     */
    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> placeOrder(@RequestBody Map<String, Integer> request) {
        try {
            Integer customerId = request.get("customerId");
            Integer productId = request.get("productId");
            Integer quantity = request.get("quantity");

            storeService.placeOrder(customerId, productId, quantity);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Order placed successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Restock inventory from supplier
     * POST /api/store/restock
     * Body: { "productId": 1, "quantity": 100, "supplierAddress": "123 Supplier St" }
     */
    @PostMapping("/restock")
    public ResponseEntity<Map<String, String>> restock(@RequestBody Map<String, Object> request) {
        try {
            Integer productId = (Integer) request.get("productId");
            Integer quantity = (Integer) request.get("quantity");
            String supplierAddress = (String) request.get("supplierAddress");

            storeService.restockFromSupplier(productId, quantity, supplierAddress);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Inventory restocked successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Cancel an order
     * PUT /api/store/orders/{orderId}/cancel
     */
    @PutMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Map<String, String>> cancelOrder(@PathVariable Integer orderId) {
        try {
            storeService.cancelOrder(orderId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Order cancelled successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }
}
