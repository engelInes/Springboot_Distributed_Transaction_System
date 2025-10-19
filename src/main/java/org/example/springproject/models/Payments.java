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

    public Integer getPayment_id() {
        return payment_id;
    }
    public void setPayment_id(Integer payment_id) {
        this.payment_id = payment_id;
    }
    public Integer getOrder_id() {
        return order_id;
    }
    public void setOrder_id(Integer order_id) {
        this.order_id = order_id;
    }
    public Integer getAmount() {
        return amount;
    }
    public void setAmount(Integer amount) {
        this.amount = amount;
    }
    public String getPayment_method() {
        return payment_method;
    }
    public void setPayment_method(String payment_method) {
        this.payment_method = payment_method;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public LocalDateTime getProcessed_at() {
        return processed_at;
    }
    public void setProcessed_at(LocalDateTime processed_at) {
        this.processed_at = processed_at;
    }
}
