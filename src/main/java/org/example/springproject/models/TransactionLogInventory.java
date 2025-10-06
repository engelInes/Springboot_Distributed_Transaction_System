package org.example.springproject.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_log_inventory")
public class TransactionLogInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer log_id;
    private String transaction_id;
    private String operation_type;
    private String table_name;
    private String before_snapshot;
    private String after_snapshot;
    private LocalDateTime timestamp=LocalDateTime.now();
}
