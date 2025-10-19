package org.example.springproject.models;

import jakarta.persistence.*;

@Entity
@Table(name = "suppliers")
public class Suppliers {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer supplier_id;
    private String name;
    private String contact;

    public Integer getSupplier_id() {
        return supplier_id;
    }
    public void setSupplier_id(Integer supplier_id) {
        this.supplier_id = supplier_id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getContact() {
        return contact;
    }
    public void setContact(String contact) {
        this.contact = contact;
    }
}
