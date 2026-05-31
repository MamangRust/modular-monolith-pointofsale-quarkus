package com.sanedge.cashier.repository.statsbyid;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.cashier.entity.Cashier;
import com.sanedge.cashier.entity.CashierMonthSales;
import com.sanedge.cashier.entity.CashierYearSales;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CashierSalesByIdRepository implements PanacheRepository<Cashier> {

    public Uni<List<CashierMonthSales>> findMonthSalesById(Long cashierId, Integer year, Integer startMonth, Integer endMonth) {
        String sql = """
            WITH date_range AS (
                SELECT
                    make_date(:year, :startMonth, 1) AS start_date,
                    make_date(:year, :endMonth, 1)
                        + INTERVAL '1' MONTH * (1 + :endMonth - :startMonth)
                        - INTERVAL '1' DAY AS end_date
            ),
            cashier_activity AS (
                SELECT
                    c.cashier_id AS cashierId,
                    c.name AS cashierName,
                    EXTRACT(MONTH FROM o.created_at) AS activityMonth,
                    COUNT(o.order_id) AS orderCount,
                    CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalSales
                FROM orders o
                JOIN cashiers c ON o.cashier_id = c.cashier_id
                WHERE o.deleted_at IS NULL
                  AND c.deleted_at IS NULL
                  AND c.cashier_id = :cashierId
                  AND o.created_at BETWEEN
                        (SELECT start_date FROM date_range)
                        AND (SELECT end_date FROM date_range)
                GROUP BY c.cashier_id, c.name, EXTRACT(MONTH FROM o.created_at)
            )
            SELECT
                ca.cashierId,
                ca.cashierName,
                TO_CHAR(TO_DATE(CAST(ca.activityMonth AS VARCHAR), 'MM'), 'Mon') AS month,
                ca.orderCount,
                ca.totalSales
            FROM cashier_activity ca
            ORDER BY ca.activityMonth, ca.cashierId
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("cashierId", cashierId)
                        .setParameter("year", year)
                        .setParameter("startMonth", startMonth)
                        .setParameter("endMonth", endMonth)
                        .getResultList())
                .map(rawList -> {
                    List<CashierMonthSales> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new CashierMonthSales(
                                columns[2] != null ? (String) columns[2] : null,
                                columns[0] != null ? ((Number) columns[0]).intValue() : null,
                                columns[1] != null ? (String) columns[1] : null,
                                columns[3] != null ? ((Number) columns[3]).intValue() : null,
                                columns[4] != null ? ((Number) columns[4]).longValue() : null
                        ));
                    }
                    return list;
                });
    }

    public Uni<List<CashierYearSales>> findYearSalesById(Long cashierId, Integer year) {
        String sql = """
            WITH last_five_years AS (
                SELECT
                    c.cashier_id AS cashierId,
                    c.name AS cashierName,
                    CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR) AS year,
                    COUNT(o.order_id) AS orderCount,
                    CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalSales
                FROM orders o
                JOIN cashiers c ON o.cashier_id = c.cashier_id
                WHERE o.deleted_at IS NULL
                  AND c.deleted_at IS NULL
                  AND c.cashier_id = :cashierId
                  AND EXTRACT(YEAR FROM o.created_at)
                        BETWEEN (:year - 4) AND :year
                GROUP BY c.cashier_id, c.name, EXTRACT(YEAR FROM o.created_at)
            )
            SELECT
                year,
                cashierId,
                cashierName,
                orderCount,
                totalSales
            FROM last_five_years
            ORDER BY year, cashierId
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("cashierId", cashierId)
                        .setParameter("year", year)
                        .getResultList())
                .map(rawList -> {
                    List<CashierYearSales> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new CashierYearSales(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? ((Number) columns[1]).intValue() : null,
                                columns[2] != null ? (String) columns[2] : null,
                                columns[3] != null ? ((Number) columns[3]).intValue() : null,
                                columns[4] != null ? ((Number) columns[4]).longValue() : null
                        ));
                    }
                    return list;
                });
    }
}
