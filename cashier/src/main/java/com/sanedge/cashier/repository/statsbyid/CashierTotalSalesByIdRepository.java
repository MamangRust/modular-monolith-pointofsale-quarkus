package com.sanedge.cashier.repository.statsbyid;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.cashier.entity.Cashier;
import com.sanedge.cashier.entity.CashierMonthTotalSales;
import com.sanedge.cashier.entity.CashierYearTotalSales;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CashierTotalSalesByIdRepository implements PanacheRepository<Cashier> {

    public Uni<List<CashierMonthTotalSales>> findMonthTotalSalesById(Long cashierId, Integer startYear, Integer startMonth, Integer endYear, Integer endMonth) {
        String sql = """
            WITH monthly_totals AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR) AS year,
                    CAST(EXTRACT(MONTH FROM o.created_at) AS INTEGER) AS month,
                    CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalSales
                FROM orders o
                JOIN cashiers c ON o.cashier_id = c.cashier_id
                WHERE o.deleted_at IS NULL
                  AND c.deleted_at IS NULL
                  AND c.cashier_id = :cashierId
                  AND (
                      (EXTRACT(YEAR FROM o.created_at) = :startYear AND EXTRACT(MONTH FROM o.created_at) = :startMonth)
                      OR (EXTRACT(YEAR FROM o.created_at) = :endYear AND EXTRACT(MONTH FROM o.created_at) = :endMonth)
                  )
                GROUP BY CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR), CAST(EXTRACT(MONTH FROM o.created_at) AS INTEGER)
            ),
            all_months AS (
                SELECT CAST(:startYear AS VARCHAR) AS year, CAST(:startMonth AS INTEGER) AS month
                UNION
                SELECT CAST(:endYear AS VARCHAR) AS year, CAST(:endMonth AS INTEGER) AS month
            )
            SELECT
                am.year,
                TO_CHAR(TO_DATE(CAST(am.month AS VARCHAR), 'MM'), 'FMMonth') AS month,
                CAST(COALESCE(mt.totalSales, 0) AS BIGINT) AS totalSales
            FROM all_months am
            LEFT JOIN monthly_totals mt ON am.year = mt.year AND am.month = mt.month
            ORDER BY CAST(am.year AS INTEGER) DESC, am.month DESC
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("cashierId", cashierId)
                        .setParameter("startYear", startYear)
                        .setParameter("startMonth", startMonth)
                        .setParameter("endYear", endYear)
                        .setParameter("endMonth", endMonth)
                        .getResultList())
                .map(rawList -> {
                    List<CashierMonthTotalSales> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new CashierMonthTotalSales(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? (String) columns[1] : null,
                                columns[2] != null ? ((Number) columns[2]).longValue() : null
                        ));
                    }
                    return list;
                });
    }

    public Uni<List<CashierYearTotalSales>> findYearTotalSalesById(Long cashierId, Integer year, Integer yearMinusOne) {
        String sql = """
            WITH yearly_data AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER) AS year,
                    CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalSales
                FROM orders o
                JOIN cashiers c ON o.cashier_id = c.cashier_id
                WHERE o.deleted_at IS NULL
                  AND c.deleted_at IS NULL
                  AND c.cashier_id = :cashierId
                  AND EXTRACT(YEAR FROM o.created_at) IN (:year, :yearMinusOne)
                GROUP BY CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER)
            ),
            all_years AS (
                SELECT CAST(:year AS INTEGER) AS year
                UNION
                SELECT CAST(:yearMinusOne AS INTEGER) AS year
            )
            SELECT
                CAST(a.year AS VARCHAR) AS year,
                CAST(COALESCE(yd.totalSales, 0) AS BIGINT) AS totalSales
            FROM all_years a
            LEFT JOIN yearly_data yd ON a.year = yd.year
            ORDER BY a.year DESC
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("cashierId", cashierId)
                        .setParameter("year", year)
                        .setParameter("yearMinusOne", yearMinusOne)
                        .getResultList())
                .map(rawList -> {
                    List<CashierYearTotalSales> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new CashierYearTotalSales(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? ((Number) columns[1]).longValue() : null
                        ));
                    }
                    return list;
                });
    }
}
