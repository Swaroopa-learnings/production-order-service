INSERT INTO production_orders (
    factory_id,
    product_code,
    quantity,
    priority,
    version,
    created_at,
    updated_at,
    status
)
SELECT
    ((i - 1) / 1000) + 1 AS factory_id,
    'PROD-' || LPAD((((i - 1) % 1000) + 1)::text, 4, '0') AS product_code,
    (random() * 99 + 1)::INTEGER,
    (random() * 9 + 1)::INTEGER,
    0,
    NOW() - (random() * INTERVAL '365 days'),
    NOW(),
    (ARRAY[
        'CREATED',
        'PROCESSING',
        'COMPLETED',
        'FAILED',
        'BLOCKED'
    ])[floor(random() * 5 + 1)::INTEGER]
FROM generate_series(1, 100000) AS i;