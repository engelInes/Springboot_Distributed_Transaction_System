package org.example.springproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

@SpringBootApplication
public class SpringProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringProjectApplication.class, args);
        System.out.println("===========================================");
        System.out.println("  app started ");
        System.out.println("===========================================");
        System.out.println("Server running on: http://localhost:8080");
        System.out.println("API Endpoints:");
        System.out.println("  POST   /api/store/orders          - Place an order");
        System.out.println("  POST   /api/store/restock         - Restock inventory");
        System.out.println("  PUT    /api/store/orders/{id}/cancel - Cancel order");
        System.out.println("===========================================");
    }
}
