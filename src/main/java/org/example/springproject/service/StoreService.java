package org.example.springproject.service;

import org.example.springproject.repository.OrderRepository;
import org.example.springproject.repository.ProductRepository;
import org.example.springproject.transaction.DistributedTransaction;
import org.example.springproject.util.TransactionRetryTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.example.springproject.util.AppConstants.*;

@Service
public class StoreService {

    private final DistributedTransaction tm;
    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;
    private final TransactionRetryTemplate retryTemplate;

    public StoreService(DistributedTransaction tm,
                        TransactionRetryTemplate retryTemplate,
                        ProductRepository productRepo,
                        OrderRepository orderRepo) {
        this.tm = tm;
        this.retryTemplate = retryTemplate;
        this.productRepo = productRepo;
        this.orderRepo = orderRepo;
    }

    public Integer placeOrder(Integer customerId, Integer productId, Integer quantity) {
        java.util.concurrent.atomic.AtomicInteger resultId = new java.util.concurrent.atomic.AtomicInteger();
        retryTemplate.execute(() -> {
            String tx = tm.beginTransaction();
            try {
                Map<String, Object> product = productRepo.findByIdForUpdate(tx, productId);
                validateProductAvailability(product, quantity);

                productRepo.decreaseStock(tx, product, quantity);

                double price = getDouble(product.get("price"));
                int total = (int) (price * quantity);

                Integer orderId = orderRepo.createOrder(tx, customerId, productId, quantity, total);
                orderRepo.createPayment(tx, orderId, total);

                tm.commit(tx);
                resultId.set(orderId);
            } catch (Exception e) {
                tm.rollback(tx);
                throw e;
            }
        }, "Failed to place order");
        return resultId.get();
    }

    public void restockFromSupplier(Integer productId, Integer quantity) {
        retryTemplate.execute(() -> {
            String tx = tm.beginTransaction();
            try {
                productRepo.logInventoryTransaction(tx, productId, quantity);

                Map<String, Object> product = productRepo.findByIdForUpdate(tx, productId);
                if (product == null) throw new RuntimeException("Product not found");

                productRepo.increaseStock(tx, product, quantity);
                tm.commit(tx);
            } catch (Exception e) {
                tm.rollback(tx);
                throw e;
            }
        }, "Failed to restock inventory");
    }

    public void cancelOrder(Integer orderId) {
        retryTemplate.execute(() -> {
            String tx = tm.beginTransaction();
            try {
                Map<String, Object> order = orderRepo.findOrderForUpdate(tx, orderId);
                validateOrderExists(order);
                if (STATUS_CANCELLED.equals(order.get("status"))) {
                    throw new RuntimeException("Order already cancelled");
                }

                orderRepo.updateOrderStatus(tx, order, STATUS_CANCELLED);

                Map<String, Object> payment = orderRepo.findPaymentForUpdate(tx, orderId);
                if (payment != null) {
                    orderRepo.updatePaymentStatus(tx, payment, STATUS_REFUNDED);
                }

                Map<String, Object> product = productRepo.findByIdForUpdate(tx, getInt(order.get("product_id")));
                if (product != null) {
                    productRepo.increaseStock(tx, product, getInt(order.get("quantity")));
                }

                tm.commit(tx);
            } catch (Exception e) {
                tm.rollback(tx);
                throw e;
            }
        }, "Failed to cancel order");
    }

    public void modifyOrderQuantity(Integer orderId, Integer newQuantity) {
        retryTemplate.execute(() -> {
            String tx = tm.beginTransaction();
            try {
                Map<String, Object> order = orderRepo.findOrderForUpdate(tx, orderId);
                validateOrderExists(order);
                if (!STATUS_PENDING.equals(order.get("status"))) {
                    throw new RuntimeException("Cannot modify non-PENDING order");
                }

                int oldQuantity = getInt(order.get("quantity"));
                int diff = newQuantity - oldQuantity;

                Map<String, Object> product = productRepo.findByIdForUpdate(tx, getInt(order.get("product_id")));
                double price = getDouble(product.get("price"));
                int newTotal = (int) (price * newQuantity);

                if (diff != 0) {
                    if (diff > 0) {
                        validateProductAvailability(product, diff);
                        productRepo.decreaseStock(tx, product, diff);
                    } else {
                        productRepo.increaseStock(tx, product, Math.abs(diff));
                    }
                }

                orderRepo.updateOrderQuantity(tx, order, newQuantity, newTotal);
                Map<String, Object> payment = orderRepo.findPaymentForUpdate(tx, orderId);
                if (payment != null) {
                    orderRepo.updatePaymentAmount(tx, payment, newTotal);
                }

                tm.commit(tx);
            } catch (Exception e) {
                tm.rollback(tx);
                throw e;
            }
        }, "Failed to modify order quantity");
    }

    public void shipOrder(Integer orderId) {
        retryTemplate.execute(() -> {
            String tx = tm.beginTransaction();
            try {
                Map<String, Object> order = orderRepo.findOrderForUpdate(tx, orderId);
                validateOrderExists(order);
                if (!STATUS_PENDING.equals(order.get("status"))) {
                    throw new RuntimeException("Order must be PENDING to ship");
                }

                orderRepo.updateOrderStatus(tx, order, STATUS_SHIPPED);

                int totalAmount = getInt(order.get("total_amount"));
                Map<String, Object> payment = orderRepo.findPaymentForUpdate(tx, orderId);
                orderRepo.updatePaymentAmount(tx, payment, totalAmount);

                if (payment != null) orderRepo.updatePaymentStatus(tx, payment, STATUS_CAPTURED);

                productRepo.logInventoryTransaction(tx, getInt(order.get("product_id")), -getInt(order.get("quantity")));

                tm.commit(tx);
            } catch (Exception e) {
                tm.rollback(tx);
                throw e;
            }
        }, "Failed to ship order");
    }

    public void returnOrder(Integer orderId) {
        retryTemplate.execute(() -> {
            String tx = tm.beginTransaction();
            try {
                Map<String, Object> order = orderRepo.findOrderForUpdate(tx, orderId);
                validateOrderExists(order);
                if (!STATUS_SHIPPED.equals(order.get("status"))) {
                    throw new RuntimeException("Only SHIPPED orders can be returned");
                }

                orderRepo.updateOrderStatus(tx, order, STATUS_RETURNED);

                Map<String, Object> payment = orderRepo.findPaymentForUpdate(tx, orderId);
                if (payment != null) orderRepo.updatePaymentStatus(tx, payment, STATUS_REFUNDED);

                Map<String, Object> product = productRepo.findByIdForUpdate(tx, getInt(order.get("product_id")));
                productRepo.increaseStock(tx, product, getInt(order.get("quantity")));

                tm.commit(tx);
            } catch (Exception e) {
                tm.rollback(tx);
                throw e;
            }
        }, "Failed to return order");
    }

    public void exchangeProduct(Integer orderId, Integer newProductId) {
        retryTemplate.execute(() -> {
            String tx = tm.beginTransaction();
            try {
                Map<String, Object> order = orderRepo.findOrderForUpdate(tx, orderId);
                validateOrderExists(order);
                int oldProductId = getInt(order.get("product_id"));

                if (oldProductId == newProductId) throw new RuntimeException("Cannot exchange for same product");

                Map<String, Object> oldProduct = productRepo.findByIdForUpdate(tx, oldProductId);
                Map<String, Object> newProduct = productRepo.findByIdForUpdate(tx, newProductId);

                if (newProduct == null) throw new RuntimeException("New product not found");

                int qty = getInt(order.get("quantity"));
                validateProductAvailability(newProduct, qty);

                productRepo.increaseStock(tx, oldProduct, qty);
                productRepo.decreaseStock(tx, newProduct, qty);

                double price = getDouble(newProduct.get("price"));
                int newTotal = (int) (price * qty);

                Map<String, Object> payment = orderRepo.findPaymentForUpdate(tx, orderId);
                orderRepo.updateOrderDetails(tx, order, newProductId, qty, newTotal);
                orderRepo.updatePaymentAmount(tx, payment, newTotal);

                tm.commit(tx);
            } catch (Exception e) {
                tm.rollback(tx);
                throw e;
            }
        }, "Failed to exchange product");
    }

    public void discontinueProduct(Integer productId) {
        retryTemplate.execute(() -> {
            String tx = tm.beginTransaction();
            try {
                Map<String, Object> product = productRepo.findByIdForUpdate(tx, productId);
                if (product == null) throw new RuntimeException("Product not found");

                productRepo.markDiscontinued(tx, product);
                productRepo.logInventoryTransaction(tx, productId, 0);

                tm.commit(tx);
            } catch (Exception e) {
                tm.rollback(tx);
                throw e;
            }
        }, "Failed to discontinue product");
    }

    private void validateOrderExists(Map<String, Object> order) {
        if (order == null) throw new RuntimeException("Order not found");
    }

    private void validateProductAvailability(Map<String, Object> product, int requiredQty) {
        if (product == null) throw new RuntimeException("Product not found");
        if (getInt(product.get("stock")) < requiredQty) throw new RuntimeException("Insufficient stock");
    }

    private int getInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new RuntimeException("Invalid number format for: " + value);
    }

    private double getDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new RuntimeException("Invalid number format for: " + value);
    }
}