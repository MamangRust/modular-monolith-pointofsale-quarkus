package com.sanedge.order.repository.statsbymerchant;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.order.entity.Order;
import com.sanedge.order.entity.OrderMonth;
import com.sanedge.order.entity.OrderYear;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderSoldOutByMerchantRepository implements PanacheRepository<Order> {

    public Uni<List<OrderMonth>> findMonthlyOrdersByMerchant(Integer merchantId, Integer yearMonth) {
        String sql = """
            WITH date_range AS (
                SELECT
                    date_trunc('month', TO_TIMESTAMP(CAST(:yearMonth AS VARCHAR), 'YYYYMM')) AS start_date,
                    date_trunc('month', TO_TIMESTAMP(CAST(:yearMonth AS VARCHAR), 'YYYYMM')) + INTERVAL '1' YEAR - INTERVAL '1' DAY AS end_date
            ),
            monthly_orders AS (
                SELECT
                    date_trunc('month', o.created_at) AS activity_month,
                    CAST(COUNT(o.order_id) AS INTEGER) AS order_count,
                    CAST(SUM(o.total_price) AS BIGINT) AS total_revenue,
                    CAST(SUM(oi.quantity) AS INTEGER) AS total_items_sold
                FROM orders o
                JOIN order_items oi ON o.order_id = oi.order_id
                WHERE o.deleted_at IS NULL
                  AND oi.deleted_at IS NULL
                  AND o.merchant_id = :merchantId
                  AND o.created_at BETWEEN (SELECT start_date FROM date_range)
                                       AND (SELECT end_date FROM date_range)
                GROUP BY activity_month
            )
            SELECT
                TO_CHAR(mo.activity_month, 'Mon') AS month,
                mo.order_count AS orderCount,
                mo.total_revenue AS totalRevenue,
                mo.total_items_sold AS totalItemsSold
            FROM monthly_orders mo
            ORDER BY mo.activity_month
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("merchantId", merchantId)
                        .setParameter("yearMonth", yearMonth)
                        .getResultList())
                .map(rawList -> {
                    List<OrderMonth> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new OrderMonth(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? ((Number) columns[1]).intValue() : null,
                                columns[2] != null ? ((Number) columns[2]).longValue() : null,
                                columns[3] != null ? ((Number) columns[3]).intValue() : null
                        ));
                    }
                    return list;
                });
    }

    public Uni<List<OrderYear>> findYearlyOrdersByMerchant(Integer merchantId, Integer yearMonth) {
        String sql = """
            WITH last_five_years AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR) AS year,
                    CAST(COUNT(o.order_id) AS INTEGER) AS order_count,
                    CAST(SUM(o.total_price) AS BIGINT) AS total_revenue,
                    CAST(SUM(oi.quantity) AS INTEGER) AS total_items_sold,
                    CAST(COUNT(DISTINCT o.user_id) AS INTEGER) AS active_cashiers,
                    CAST(COUNT(DISTINCT oi.product_id) AS INTEGER) AS unique_products_sold
                FROM orders o
                JOIN order_items oi ON o.order_id = oi.order_id
                WHERE o.deleted_at IS NULL
                  AND oi.deleted_at IS NULL
                  AND o.merchant_id = :merchantId
                  AND EXTRACT(YEAR FROM o.created_at) BETWEEN EXTRACT(YEAR FROM TO_TIMESTAMP(CAST(:yearMonth AS VARCHAR), 'YYYYMM')) - 4
                                                           AND EXTRACT(YEAR FROM TO_TIMESTAMP(CAST(:yearMonth AS VARCHAR), 'YYYYMM'))
                GROUP BY CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR)
            )
            SELECT
                year,
                order_count AS orderCount,
                total_revenue AS totalRevenue,
                total_items_sold AS totalItemsSold,
                active_cashiers AS activeCashiers,
                unique_products_sold AS uniqueProductsSold
            FROM last_five_years
            ORDER BY year
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("merchantId", merchantId)
                        .setParameter("yearMonth", yearMonth)
                        .getResultList())
                .map(rawList -> {
                    List<OrderYear> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new OrderYear(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? ((Number) columns[1]).intValue() : null,
                                columns[2] != null ? ((Number) columns[2]).longValue() : null,
                                columns[3] != null ? ((Number) columns[3]).intValue() : null,
                                columns[4] != null ? ((Number) columns[4]).intValue() : null,
                                columns[5] != null ? ((Number) columns[5]).intValue() : null
                        ));
                    }
                    return list;
                });
    }
}
