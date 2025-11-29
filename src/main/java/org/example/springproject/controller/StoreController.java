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

            Integer orderId = storeService.placeOrder(customerId, productId, quantity);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Order placed successfully",
                    "orderId", String.valueOf(orderId)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Restock inventory from supplier
     * POST /api/store/restock
     * Body: { "productId": 1, "quantity": 100 }
     */
    @PostMapping("/restock")
    public ResponseEntity<Map<String, String>> restock(@RequestBody Map<String, Object> request) {
        try {
            Integer productId = (Integer) request.get("productId");
            Integer quantity = (Integer) request.get("quantity");

            storeService.restockFromSupplier(productId, quantity);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Inventory restocked successfully"
            ));
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Modify order quantity
     * PUT /api/store/orders/{orderId}/quantity
     * Body: { "newQuantity": 5 }
     */
    @PutMapping("/orders/{orderId}/quantity")
    public ResponseEntity<Map<String, String>> modifyOrderQuantity(
            @PathVariable Integer orderId,
            @RequestBody Map<String, Integer> request) {
        try {
            Integer newQuantity = request.get("newQuantity");
            storeService.modifyOrderQuantity(orderId, newQuantity);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Order quantity updated successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Ship an order
     * POST /api/store/orders/{orderId}/ship
     */
    @PostMapping("/orders/{orderId}/ship")
    public ResponseEntity<Map<String, String>> shipOrder(@PathVariable Integer orderId) {
        try {
            storeService.shipOrder(orderId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Order shipped successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Return an order
     * POST /api/store/orders/{orderId}/return
     */
    @PostMapping("/orders/{orderId}/return")
    public ResponseEntity<Map<String, String>> returnOrder(@PathVariable Integer orderId) {
        try {
            storeService.returnOrder(orderId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Order returned successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Exchange a product in an order
     * PUT /api/store/orders/{orderId}/exchange
     * Body: { "newProductId": 102 }
     */
    @PutMapping("/orders/{orderId}/exchange")
    public ResponseEntity<Map<String, String>> exchangeProduct(
            @PathVariable Integer orderId,
            @RequestBody Map<String, Integer> request) {
        try {
            Integer newProductId = request.get("newProductId");
            storeService.exchangeProduct(orderId, newProductId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Product exchanged successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Discontinue a product
     * DELETE /api/store/products/{productId}
     */
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Map<String, String>> discontinueProduct(@PathVariable Integer productId) {
        try {
            storeService.discontinueProduct(productId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Product discontinued successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }
}
