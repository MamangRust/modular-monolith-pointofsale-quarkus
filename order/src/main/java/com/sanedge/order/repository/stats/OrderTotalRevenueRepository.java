package com.sanedge.order.repository.stats;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.order.entity.Order;
import com.sanedge.order.entity.OrderMonthTotalRevenue;
import com.sanedge.order.entity.OrderYearTotalRevenue;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderTotalRevenueRepository implements PanacheRepository<Order> {

    public Uni<List<OrderMonthTotalRevenue>> findMonthlyTotalRevenue(Integer year1, Integer month1, Integer year2, Integer month2) {
        String sql = """
            WITH monthly_revenue AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER) AS year,
                    CAST(EXTRACT(MONTH FROM o.created_at) AS INTEGER) AS month,
                    CAST(COALESCE(SUM(o.total_price), 0) AS INTEGER) AS total_revenue
                FROM orders o
                JOIN order_items oi ON o.order_id = oi.order_id
                WHERE o.deleted_at IS NULL
                  AND oi.deleted_at IS NULL
                  AND (
                      (EXTRACT(YEAR FROM o.created_at) = :year1 AND EXTRACT(MONTH FROM o.created_at) = :month1)
                      OR (EXTRACT(YEAR FROM o.created_at) = :year2 AND EXTRACT(MONTH FROM o.created_at) = :month2)
                  )
                GROUP BY CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER), CAST(EXTRACT(MONTH FROM o.created_at) AS INTEGER)
            ),
            all_months AS (
                SELECT CAST(:year1 AS VARCHAR) AS year, CAST(:month1 AS INTEGER) AS month, TO_CHAR(TO_DATE(CAST(:month1 AS VARCHAR), 'MM'), 'FMMonth') AS month_name
                UNION
                SELECT CAST(:year2 AS VARCHAR) AS year, CAST(:month2 AS INTEGER) AS month, TO_CHAR(TO_DATE(CAST(:month2 AS VARCHAR), 'MM'), 'FMMonth') AS month_name
            )
            SELECT
                am.year AS year,
                am.month_name AS month,
                COALESCE(mr.total_revenue, 0) AS totalRevenue
            FROM all_months am
            LEFT JOIN monthly_revenue mr
            ON CAST(am.year AS INTEGER) = mr.year AND am.month = mr.month
            ORDER BY am.year DESC, am.month DESC
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("year1", year1)
                        .setParameter("month1", month1)
                        .setParameter("year2", year2)
                        .setParameter("month2", month2)
                        .getResultList())
                .map(rawList -> {
                    List<OrderMonthTotalRevenue> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new OrderMonthTotalRevenue(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? (String) columns[1] : null,
                                columns[2] != null ? ((Number) columns[2]).intValue() : null
                        ));
                    }
                    return list;
                });
    }

    public Uni<List<OrderYearTotalRevenue>> findYearlyTotalRevenue(Integer year) {
        String sql = """
            WITH yearly_revenue AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER) AS year,
                    CAST(COALESCE(SUM(o.total_price), 0) AS INTEGER) AS total_revenue
                FROM orders o
                JOIN order_items oi ON o.order_id = oi.order_id
                WHERE o.deleted_at IS NULL
                  AND oi.deleted_at IS NULL
                  AND (EXTRACT(YEAR FROM o.created_at) = :year OR EXTRACT(YEAR FROM o.created_at) = :year - 1)
                GROUP BY CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER)
            ),
            all_years AS (
                SELECT CAST(:year AS INTEGER) AS year
                UNION
                SELECT CAST(:year - 1 AS INTEGER) AS year
            )
            SELECT
                CAST(ay.year AS VARCHAR) AS year,
                COALESCE(yr.total_revenue, 0) AS totalRevenue
            FROM all_years ay
            LEFT JOIN yearly_revenue yr ON ay.year = yr.year
            ORDER BY ay.year DESC
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("year", year)
                        .getResultList())
                .map(rawList -> {
                    List<OrderYearTotalRevenue> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new OrderYearTotalRevenue(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? ((Number) columns[1]).intValue() : null
                        ));
                    }
                    return list;
                });
    }
}
