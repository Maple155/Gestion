CREATE TABLE managers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL
);
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO managers (username, password) VALUES
('admin', 'admin123'),
('stock_manager', 'stock123'),
('achat_manager', 'achat123'),
('supervisor', 'super123');
