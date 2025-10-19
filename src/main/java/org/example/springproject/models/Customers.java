package org.example.springproject.models;

import jakarta.persistence.*;

@Entity
@Table(name = "customers")
public class Customers {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer customer_id;
    private String name;
    private String email;
    private String phone;

    public Integer getCustomer_id() {
        return customer_id;
    }
    public void setCustomer_id(Integer customer_id) {
        this.customer_id = customer_id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getPhone() {
        return phone;
    }
}
