package org.example.springproject.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Orders {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer order_id;
    private Integer customer_id;
    private Integer product_id;
    private Integer quantity;
    private Integer total_amount;
    private String status;
    private LocalDateTime created_at;
    private Integer version;
}
