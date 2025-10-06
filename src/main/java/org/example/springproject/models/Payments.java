package org.example.springproject.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payments {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer payment_id;
    private Integer order_id;
    private Integer amount;
    private String payment_method;
    private String status;
    private LocalDateTime processed_at;
}
