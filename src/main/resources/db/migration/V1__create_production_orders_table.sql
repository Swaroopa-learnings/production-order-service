create table production_orders(
id BIGSERIAL PRIMARY KEY,
factory_id BIGINT NOT NULL,
product_code VARCHAR NOT NULL,
quantity INTEGER NOT NULL,
priority INTEGER NOT NULL,
version INTEGER NOT NULL DEFAULT 0,
created_at TIMESTAMPTZ NOT NULL,
updated_at TIMESTAMPTZ NOT NULL,
CONSTRAINT uk_factory_product UNIQUE (factory_id, product_code)
);