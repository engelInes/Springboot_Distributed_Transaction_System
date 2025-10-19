package org.example.springproject.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_transactions")
public class InventoryTransactions {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer transaction_id;
    private Integer product_id;
    private Integer quantity_change;
    private String supplier_address;
    private LocalDateTime timestamp = LocalDateTime.now();

    public Integer getTransaction_id() {
        return transaction_id;
    }
    public void setTransaction_id(Integer transaction_id) {
        this.transaction_id = transaction_id;
    }
    public Integer getProduct_id() {
        return product_id;
    }
    public void setProduct_id(Integer product_id) {
        this.product_id = product_id;
    }
    public Integer getQuantity_change() {
        return quantity_change;
    }
    public void setQuantity_change(Integer quantity_change) {
        this.quantity_change = quantity_change;
    }
    public String getSupplier_address() {
        return supplier_address;
    }
    public void setSupplier_address(String supplier_address) {
        this.supplier_address = supplier_address;
    }
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
