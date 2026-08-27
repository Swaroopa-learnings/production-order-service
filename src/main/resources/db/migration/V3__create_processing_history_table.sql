create table processing_history(
order_id BIGINT NOT NULL REFERENCES production_orders(id),
status VARCHAR(30) NOT NULL
)
