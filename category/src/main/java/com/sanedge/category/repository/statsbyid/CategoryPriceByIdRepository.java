package com.sanedge.category.repository.statsbyid;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.category.entity.Category;
import com.sanedge.category.entity.CategoryMonthPrice;
import com.sanedge.category.entity.CategoryYearPrice;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CategoryPriceByIdRepository implements PanacheRepository<Category> {

    public Uni<List<CategoryMonthPrice>> findMonthlyCategoryPriceById(Long categoryId, Integer startDate) {
        String sql = """
            WITH date_range AS (
                SELECT
                    date_trunc('month', make_date(:startDate, 1, 1)) AS start_date,
                    date_trunc('month', make_date(:startDate, 1, 1)) + INTERVAL '1' YEAR - INTERVAL '1' DAY AS end_date
            ),
            monthly_category_stats AS (
                SELECT
                    CAST(c.category_id AS INTEGER) AS categoryId,
                    c.name AS categoryName,
                    date_trunc('month', o.created_at) AS activityMonth,
                    CAST(COUNT(DISTINCT o.order_id) AS INTEGER) AS orderCount,
                    CAST(COALESCE(SUM(oi.quantity), 0) AS INTEGER) AS itemsSold,
                    CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalRevenue
                FROM
                    orders o
                JOIN order_items oi ON o.order_id = oi.order_id
                JOIN products p ON oi.product_id = p.product_id
                JOIN categories c ON p.category_id = c.category_id
                WHERE
                    c.category_id = :categoryId
                    AND o.deleted_at IS NULL
                    AND oi.deleted_at IS NULL
                    AND p.deleted_at IS NULL
                    AND c.deleted_at IS NULL
                    AND o.created_at BETWEEN (SELECT start_date FROM date_range)
                                         AND (SELECT end_date FROM date_range)
                GROUP BY
                    c.category_id, c.name, activityMonth
            )
            SELECT
                TO_CHAR(mcs.activityMonth, 'Mon') AS month,
                mcs.categoryId,
                mcs.categoryName,
                mcs.orderCount,
                mcs.itemsSold,
                mcs.totalRevenue
            FROM
                monthly_category_stats mcs
            ORDER BY
                mcs.activityMonth, mcs.totalRevenue DESC
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("categoryId", categoryId)
                        .setParameter("startDate", startDate)
                        .getResultList())
                .map(rawList -> {
                    List<CategoryMonthPrice> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new CategoryMonthPrice(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? ((Number) columns[1]).intValue() : null,
                                columns[2] != null ? (String) columns[2] : null,
                                columns[3] != null ? ((Number) columns[3]).intValue() : null,
                                columns[4] != null ? ((Number) columns[4]).intValue() : null,
                                columns[5] != null ? ((Number) columns[5]).longValue() : null
                        ));
                    }
                    return list;
                });
    }

    public Uni<List<CategoryYearPrice>> findYearlyCategoryPriceById(Long categoryId, Integer startDate) {
        String sql = """
            WITH last_five_years AS (
                SELECT
                    CAST(c.category_id AS INTEGER) AS categoryId,
                    c.name AS categoryName,
                    CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR) AS year,
                    CAST(COUNT(DISTINCT o.order_id) AS INTEGER) AS orderCount,
                    CAST(COALESCE(SUM(oi.quantity), 0) AS INTEGER) AS itemsSold,
                    CAST(COALESCE(SUM(o.total_price), 0) AS BIGINT) AS totalRevenue,
                    CAST(COUNT(DISTINCT oi.product_id) AS INTEGER) AS uniqueProductsSold
                FROM
                    orders o
                JOIN order_items oi ON o.order_id = oi.order_id
                JOIN products p ON oi.product_id = p.product_id
                JOIN categories c ON p.category_id = c.category_id
                WHERE
                    c.category_id = :categoryId
                    AND o.deleted_at IS NULL
                    AND oi.deleted_at IS NULL
                    AND p.deleted_at IS NULL
                    AND c.deleted_at IS NULL
                    AND EXTRACT(YEAR FROM o.created_at)
                        BETWEEN (:startDate - 4) AND :startDate
                GROUP BY
                    c.category_id, c.name, CAST(EXTRACT(YEAR FROM o.created_at) AS VARCHAR)
            )
            SELECT
                year,
                categoryId,
                categoryName,
                orderCount,
                itemsSold,
                totalRevenue,
                uniqueProductsSold
            FROM
                last_five_years
            ORDER BY
                year, totalRevenue DESC
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("categoryId", categoryId)
                        .setParameter("startDate", startDate)
                        .getResultList())
                .map(rawList -> {
                    List<CategoryYearPrice> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new CategoryYearPrice(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? ((Number) columns[1]).intValue() : null,
                                columns[2] != null ? (String) columns[2] : null,
                                columns[3] != null ? ((Number) columns[3]).intValue() : null,
                                columns[4] != null ? ((Number) columns[4]).intValue() : null,
                                columns[5] != null ? ((Number) columns[5]).longValue() : null,
                                columns[6] != null ? ((Number) columns[6]).intValue() : null
                        ));
                    }
                    return list;
                });
    }
}
