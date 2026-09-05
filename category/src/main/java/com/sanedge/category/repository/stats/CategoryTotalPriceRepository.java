package com.sanedge.category.repository.stats;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.category.domain.requests.FindCategoryMonthTotalPriceRange;
import com.sanedge.category.entity.Category;
import com.sanedge.category.entity.CategoryMonthTotalPrice;
import com.sanedge.category.entity.CategoryYearTotalPrice;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CategoryTotalPriceRepository implements PanacheRepository<Category> {

    public Uni<List<CategoryMonthTotalPrice>> findMonthlyTotalPrice(FindCategoryMonthTotalPriceRange req) {
        String sql = """
            WITH date_range AS (
                SELECT
                    make_date(:startYear, :startMonth, 1) AS start_date,
                    (make_date(:endYear, :endMonth, 1) + INTERVAL '1' MONTH - INTERVAL '1' DAY) AS end_date
            ),
            monthly_totals AS (
                SELECT
                    EXTRACT(YEAR FROM o.created_at) AS year,
                    EXTRACT(MONTH FROM o.created_at) AS month,
                    CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalRevenue
                FROM orders o
                JOIN order_items oi ON o.order_id = oi.order_id
                JOIN products p ON oi.product_id = p.product_id
                JOIN categories c ON p.category_id = c.category_id
                WHERE o.deleted_at IS NULL
                  AND oi.deleted_at IS NULL
                  AND p.deleted_at IS NULL
                  AND c.deleted_at IS NULL
                  AND o.created_at BETWEEN (SELECT start_date FROM date_range) AND (SELECT end_date FROM date_range)
                GROUP BY EXTRACT(YEAR FROM o.created_at), EXTRACT(MONTH FROM o.created_at)
            ),
            all_months AS (
                SELECT CAST(:startYear AS INTEGER) AS year, CAST(:startMonth AS INTEGER) AS month, TO_CHAR(make_date(:startYear, :startMonth, 1), 'FMMonth') AS month_name
                UNION
                SELECT CAST(:endYear AS INTEGER) AS year, CAST(:endMonth AS INTEGER) AS month, TO_CHAR(make_date(:endYear, :endMonth, 1), 'FMMonth') AS month_name
            )
            SELECT
                CAST(am.year AS VARCHAR) AS year,
                am.month_name AS month,
                CAST(COALESCE(mt.totalRevenue, 0) AS BIGINT) AS totalRevenue
            FROM all_months am
            LEFT JOIN monthly_totals mt ON am.year = mt.year AND am.month = mt.month
            ORDER BY am.year DESC, am.month DESC
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("startYear", req.getStartYear())
                        .setParameter("startMonth", req.getStartMonth())
                        .setParameter("endYear", req.getEndYear())
                        .setParameter("endMonth", req.getEndMonth())
                        .getResultList())
                .map(rawList -> {
                    List<CategoryMonthTotalPrice> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new CategoryMonthTotalPrice(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? (String) columns[1] : null,
                                columns[2] != null ? ((Number) columns[2]).longValue() : null
                        ));
                    }
                    return list;
                });
    }

    public Uni<List<CategoryYearTotalPrice>> findYearlyTotalPrice(Integer year, Integer yearMinusOne) {
        String sql = """
            WITH yearly_data AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM o.created_at) AS INTEGER) AS year,
                    CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalRevenue
                FROM orders o
                JOIN order_items oi ON o.order_id = oi.order_id
                JOIN products p ON oi.product_id = p.product_id
                JOIN categories c ON p.category_id = c.category_id
                WHERE o.deleted_at IS NULL
                  AND oi.deleted_at IS NULL
                  AND p.deleted_at IS NULL
                  AND c.deleted_at IS NULL
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
                CAST(COALESCE(yd.totalRevenue, 0) AS BIGINT) AS totalRevenue
            FROM all_years a
            LEFT JOIN yearly_data yd ON a.year = yd.year
            ORDER BY a.year DESC
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("year", year)
                        .setParameter("yearMinusOne", yearMinusOne)
                        .getResultList())
                .map(rawList -> {
                    List<CategoryYearTotalPrice> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new CategoryYearTotalPrice(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? ((Number) columns[1]).longValue() : null
                        ));
                    }
                    return list;
                });
    }
}
