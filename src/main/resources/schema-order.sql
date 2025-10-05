CREATE TABLE customers (
                           customer_id SERIAL PRIMARY KEY,
                           name VARCHAR(200) NOT NULL,
                           email VARCHAR(200) UNIQUE NOT NULL,
                           phone VARCHAR(50)
);

CREATE TABLE orders (
                        order_id SERIAL PRIMARY KEY,
                        customer_id INT REFERENCES customers(customer_id),
                        product_id INT NOT NULL,
                        quantity INT NOT NULL,
                        total_amount DECIMAL(10,2) NOT NULL,
                        status VARCHAR(50) DEFAULT 'PENDING',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        version INT NOT NULL DEFAULT 0
);

CREATE TABLE payments (
                          payment_id SERIAL PRIMARY KEY,
                          order_id INT REFERENCES orders(order_id),
                          amount DECIMAL(10,2) NOT NULL,
                          payment_method VARCHAR(50),
                          status VARCHAR(50) DEFAULT 'PENDING',
                          processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transaction_log_order (
                                       log_id SERIAL PRIMARY KEY,
                                       transaction_id VARCHAR(50) NOT NULL,
                                       operation_type VARCHAR(20),
                                       table_name VARCHAR(50),
                                       before_snapshot TEXT,
                                       after_snapshot TEXT,
                                       timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE two_phase_commit_log (
                                      transaction_id VARCHAR(50) PRIMARY KEY,
                                      state VARCHAR(20),
                                      database_name VARCHAR(50),
                                      timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Sample data
INSERT INTO customers (name, email, phone) VALUES
                                               ('Alice Smith', 'alice@example.com', '555-0101'),
                                               ('Bob Johnson', 'bob@example.com', '555-0102');