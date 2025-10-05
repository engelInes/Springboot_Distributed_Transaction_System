package org.example.springproject.models;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer product_id;

    private String name;
    private String category;
    private String size;
    private String color;
    private Double price;
    private Integer stock;
    private Integer version;
}
