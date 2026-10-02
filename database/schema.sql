CREATE DATABASE IF NOT EXISTS enterprise_banking;
USE enterprise_banking;

CREATE TABLE customers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(200) UNIQUE NOT NULL,
    phone VARCHAR(30)
);

CREATE TABLE accounts (
    account_number BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE transactions (
    transaction_id VARCHAR(64) PRIMARY KEY,
    from_account BIGINT,
    to_account BIGINT,
    amount DECIMAL(19,2) NOT NULL,
    transaction_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fraud_alerts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_id VARCHAR(64),
    risk_level VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
