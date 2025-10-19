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

    public Integer getLog_id() {
        return log_id;
    }
    public void setLog_id(Integer log_id) {
        this.log_id = log_id;
    }
    public String getTransaction_id() {
        return transaction_id;
    }
    public void setTransaction_id(String transaction_id) {
        this.transaction_id = transaction_id;
    }
    public String getOperation_type() {
        return operation_type;
    }
    public void setOperation_type(String operation_type) {
        this.operation_type = operation_type;
    }
    public String getTable_name() {
        return table_name;
    }
    public void setTable_name(String table_name) {
        this.table_name = table_name;
    }
    public String getBefore_snapshot() {
        return before_snapshot;
    }
    public void setBefore_snapshot(String before_snapshot) {
        this.before_snapshot = before_snapshot;
    }
    public String getAfter_snapshot() {
        return after_snapshot;
    }
    public void setAfter_snapshot(String after_snapshot) {
        this.after_snapshot = after_snapshot;
    }
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
