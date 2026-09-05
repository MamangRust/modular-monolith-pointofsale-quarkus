package com.sanedge.transaction.repository.statsbymerchant;

import java.util.ArrayList;
import java.util.List;

import com.sanedge.transaction.domain.requests.FindTransactionMonthMerchantRange;
import com.sanedge.transaction.entity.Transaction;
import com.sanedge.transaction.entity.TransactionMonthlyAmountFailed;
import com.sanedge.transaction.entity.TransactionMonthlyAmountSuccess;
import com.sanedge.transaction.entity.TransactionYearlyAmountFailed;
import com.sanedge.transaction.entity.TransactionYearlyAmountSuccess;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TransactionAmountByMerchantRepository implements PanacheRepository<Transaction> {

    public Uni<List<TransactionMonthlyAmountSuccess>> findMonthlySuccessByMerchant(FindTransactionMonthMerchantRange req) {
        String sql = """
            WITH monthly_data AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS year,
                    CAST(EXTRACT(MONTH FROM t.created_at) AS INTEGER) AS month,
                    COUNT(*) AS total_success,
                    CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                FROM transactions t
                WHERE
                    t.deleted_at IS NULL
                    AND t.status = 'SUCCESS'
                    AND t.merchant_id = :merchantId
                    AND (
                        (t.created_at >= make_date(:year, :month, 1)
                         AND t.created_at < (make_date(:year, :month, 1) + INTERVAL '1' MONTH))
                        OR
                        (t.created_at >= make_date(:prevYear, :prevMonth, 1)
                         AND t.created_at < (make_date(:prevYear, :prevMonth, 1) + INTERVAL '1' MONTH))
                    )
                GROUP BY CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER), CAST(EXTRACT(MONTH FROM t.created_at) AS INTEGER)
            ),
            formatted_data AS (
                SELECT CAST(year AS VARCHAR) AS year,
                       TO_CHAR(TO_DATE(CAST(month AS VARCHAR), 'MM'), 'Mon') AS month,
                       CAST(total_success AS INTEGER) AS totalSuccess,
                       CAST(total_amount AS BIGINT) AS totalAmount
                FROM monthly_data
                UNION ALL
                SELECT CAST(CAST(:year AS INTEGER) AS VARCHAR),
                       TO_CHAR(make_date(:year, :month, 1), 'Mon'),
                       0, 0
                WHERE NOT EXISTS (
                    SELECT 1 FROM monthly_data
                    WHERE year = :year AND month = :month
                )
                UNION ALL
                SELECT CAST(CAST(:prevYear AS INTEGER) AS VARCHAR),
                       TO_CHAR(make_date(:prevYear, :prevMonth, 1), 'Mon'),
                       0, 0
                WHERE NOT EXISTS (
                    SELECT 1 FROM monthly_data
                    WHERE year = :prevYear AND month = :prevMonth
                )
            )
            SELECT * FROM formatted_data
            ORDER BY year DESC, TO_DATE(month, 'Mon') DESC
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("merchantId", req.getMerchantId())
                        .setParameter("year", req.getStartYear())
                        .setParameter("month", req.getStartMonth())
                        .setParameter("prevYear", req.getEndYear())
                        .setParameter("prevMonth", req.getEndMonth())
                        .getResultList())
                .map(rawList -> {
                    List<TransactionMonthlyAmountSuccess> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new TransactionMonthlyAmountSuccess(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? (String) columns[1] : null,
                                columns[2] != null ? ((Number) columns[2]).intValue() : null,
                                columns[3] != null ? ((Number) columns[3]).longValue() : null
                        ));
                    }
                    return list;
                });
    }

    public Uni<List<TransactionYearlyAmountSuccess>> findYearlySuccessByMerchant(
            Long merchantId, Integer year) {
        String sql = """
            WITH yearly_data AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS year,
                    COUNT(*) AS total_success,
                    CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                FROM transactions t
                WHERE
                    t.deleted_at IS NULL
                    AND t.status = 'SUCCESS'
                    AND t.merchant_id = :merchantId
                    AND (EXTRACT(YEAR FROM t.created_at) = :year
                         OR EXTRACT(YEAR FROM t.created_at) = :year - 1)
                GROUP BY CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER)
            ),
            formatted_data AS (
                SELECT CAST(year AS VARCHAR) AS year, CAST(total_success AS INTEGER) AS totalSuccess, CAST(total_amount AS BIGINT) AS totalAmount FROM yearly_data
                UNION ALL
                SELECT CAST(CAST(:year AS INTEGER) AS VARCHAR), 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE year = :year)
                UNION ALL
                SELECT CAST((:year - 1) AS VARCHAR), 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE year = :year - 1)
            )
            SELECT * FROM formatted_data
            ORDER BY year DESC
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("merchantId", merchantId)
                        .setParameter("year", year)
                        .getResultList())
                .map(rawList -> {
                    List<TransactionYearlyAmountSuccess> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new TransactionYearlyAmountSuccess(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? ((Number) columns[1]).intValue() : null,
                                columns[2] != null ? ((Number) columns[2]).longValue() : null
                        ));
                    }
                    return list;
                });
    }

    public Uni<List<TransactionMonthlyAmountFailed>> findMonthlyFailedByMerchant(FindTransactionMonthMerchantRange req) {
        String sql = """
            WITH monthly_data AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS year,
                    CAST(EXTRACT(MONTH FROM t.created_at) AS INTEGER) AS month,
                    COUNT(*) AS total_failed,
                    CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                FROM transactions t
                WHERE
                    t.deleted_at IS NULL
                    AND t.status = 'FAILED'
                    AND t.merchant_id = :merchantId
                    AND (
                        (t.created_at >= make_date(:year, :month, 1)
                         AND t.created_at < (make_date(:year, :month, 1) + INTERVAL '1' MONTH))
                        OR
                        (t.created_at >= make_date(:prevYear, :prevMonth, 1)
                         AND t.created_at < (make_date(:prevYear, :prevMonth, 1) + INTERVAL '1' MONTH))
                    )
                GROUP BY CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER), CAST(EXTRACT(MONTH FROM t.created_at) AS INTEGER)
            ),
            formatted_data AS (
                SELECT
                    CAST(year AS VARCHAR) AS year,
                    TO_CHAR(TO_DATE(CAST(month AS VARCHAR), 'MM'), 'Mon') AS month,
                    CAST(total_failed AS INTEGER) AS totalFailed,
                    CAST(total_amount AS BIGINT) AS totalAmount
                FROM monthly_data
                UNION ALL
                SELECT CAST(CAST(:year AS INTEGER) AS VARCHAR),
                       TO_CHAR(make_date(:year, :month, 1), 'Mon'),
                       0, 0
                WHERE NOT EXISTS (
                    SELECT 1 FROM monthly_data
                    WHERE year = :year AND month = :month
                )
                UNION ALL
                SELECT CAST(CAST(:prevYear AS INTEGER) AS VARCHAR),
                       TO_CHAR(make_date(:prevYear, :prevMonth, 1), 'Mon'),
                       0, 0
                WHERE NOT EXISTS (
                    SELECT 1 FROM monthly_data
                    WHERE year = :prevYear AND month = :prevMonth
                )
            )
            SELECT * FROM formatted_data
            ORDER BY year DESC, TO_DATE(month, 'Mon') DESC
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("merchantId", req.getMerchantId())
                        .setParameter("year", req.getStartYear())
                        .setParameter("month", req.getStartMonth())
                        .setParameter("prevYear", req.getEndYear())
                        .setParameter("prevMonth", req.getEndMonth())
                        .getResultList())
                .map(rawList -> {
                    List<TransactionMonthlyAmountFailed> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new TransactionMonthlyAmountFailed(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? (String) columns[1] : null,
                                columns[2] != null ? ((Number) columns[2]).intValue() : null,
                                columns[3] != null ? ((Number) columns[3]).longValue() : null
                        ));
                    }
                    return list;
                });
    }

    public Uni<List<TransactionYearlyAmountFailed>> findYearlyFailedByMerchant(
            Long merchantId, Integer year) {
        String sql = """
            WITH yearly_data AS (
                SELECT
                    CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER) AS year,
                    COUNT(*) AS total_failed,
                    CAST(COALESCE(SUM(t.amount), 0) AS BIGINT) AS total_amount
                FROM transactions t
                WHERE
                    t.deleted_at IS NULL
                    AND t.status = 'FAILED'
                    AND t.merchant_id = :merchantId
                    AND (EXTRACT(YEAR FROM t.created_at) = :year
                         OR EXTRACT(YEAR FROM t.created_at) = :year - 1)
                GROUP BY CAST(EXTRACT(YEAR FROM t.created_at) AS INTEGER)
            ),
            formatted_data AS (
                SELECT
                    CAST(year AS VARCHAR) AS year,
                    CAST(total_failed AS INTEGER) AS totalFailed,
                    CAST(total_amount AS BIGINT) AS totalAmount
                FROM yearly_data
                UNION ALL
                SELECT CAST(CAST(:year AS INTEGER) AS VARCHAR), 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE year = :year)
                UNION ALL
                SELECT CAST((:year - 1) AS VARCHAR), 0, 0 WHERE NOT EXISTS (SELECT 1 FROM yearly_data WHERE year = :year - 1)
            )
            SELECT * FROM formatted_data
            ORDER BY year DESC
            """;

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(sql)
                        .setParameter("merchantId", merchantId)
                        .setParameter("year", year)
                        .getResultList())
                .map(rawList -> {
                    List<TransactionYearlyAmountFailed> list = new ArrayList<>();
                    for (Object row : rawList) {
                        Object[] columns = (Object[]) row;
                        list.add(new TransactionYearlyAmountFailed(
                                columns[0] != null ? (String) columns[0] : null,
                                columns[1] != null ? ((Number) columns[1]).intValue() : null,
                                columns[2] != null ? ((Number) columns[2]).longValue() : null
                        ));
                    }
                    return list;
                });
    }
}
