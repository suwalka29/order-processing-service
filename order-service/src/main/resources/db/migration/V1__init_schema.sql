-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Core orders table
-- version column enables JPA optimistic locking (@Version)
CREATE TABLE orders (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID        NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_amount    NUMERIC(10,2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status      ON orders(status);
CREATE INDEX idx_orders_created_at  ON orders(created_at DESC);

-- Line items for each order
CREATE TABLE order_items (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id UUID        NOT NULL,
    name         VARCHAR(255) NOT NULL,
    quantity     INTEGER     NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);

-- Stores idempotency keys to prevent duplicate order creation
-- Key + response body stored atomically with the order
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    response_body   TEXT        NOT NULL,
    status_code     INTEGER     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Auto-expire old keys after 24h (run as a scheduled job)
CREATE INDEX idx_idempotency_created ON idempotency_keys(created_at);