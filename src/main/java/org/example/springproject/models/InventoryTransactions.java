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
}
