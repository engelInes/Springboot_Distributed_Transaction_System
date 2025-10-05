CREATE TABLE products (
                          product_id SERIAL PRIMARY KEY,
                          name VARCHAR(200) NOT NULL,
                          category VARCHAR(100),
                          size VARCHAR(10),
                          color VARCHAR(50),
                          price DECIMAL(10,2) NOT NULL,
                          stock INT NOT NULL DEFAULT 0,
                          version INT NOT NULL DEFAULT 0
);

CREATE TABLE suppliers (
                           supplier_id SERIAL PRIMARY KEY,
                           name VARCHAR(200) NOT NULL,
                           contact VARCHAR(100)
);

CREATE TABLE inventory_transactions (
                                        transaction_id VARCHAR(50) PRIMARY KEY,
                                        product_id INT,
                                        quantity_change INT,
                                        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transaction_log_inventory (
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
INSERT INTO products (name, category, size, color, price, stock) VALUES
                                                                     ('Classic T-Shirt', 'Tops', 'M', 'Blue', 29.99, 100),
                                                                     ('Denim Jeans', 'Bottoms', 'L', 'Black', 59.99, 50),
                                                                     ('Summer Dress', 'Dresses', 'S', 'Red', 79.99, 30);