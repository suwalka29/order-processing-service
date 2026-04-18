-- Returns summary stats for customers with more than 2 orders.
-- Uses a window function to efficiently get most recent order status
-- without a correlated subquery per row.

SELECT
    customer_id,
    COUNT(id)                      AS total_orders,
    SUM(total_amount)              AS total_spent,
    ROUND(AVG(total_amount), 2)    AS avg_order_value,
    -- DISTINCT ON gives us the most recent row per customer
    first_value(status) OVER (
        PARTITION BY customer_id
        ORDER BY created_at DESC
    )                              AS latest_order_status
FROM orders
GROUP BY customer_id, status, created_at
HAVING COUNT(id) OVER (PARTITION BY customer_id) > 2
ORDER BY total_spent DESC;