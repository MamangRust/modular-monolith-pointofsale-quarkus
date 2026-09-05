package com.sanedge.transaction.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.transaction.domain.requests.FindTransactionMonthRange;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class TransactionAmountStatusRepositoryTest {

    @Inject
    TransactionAmountStatusRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthlyTransactionSuccess_ReturnsTwoMonthsWithZero() {
        FindTransactionMonthRange req = new FindTransactionMonthRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyTransactionSuccess(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month -> {
                        assertThat(month.getTotalSuccess()).isZero();
                        assertThat(month.getTotalAmount()).isZero();
                    });
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearlyTransactionSuccess_ReturnsTwoYearsWithZero() {
        return repository.findYearlyTransactionSuccess(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getTotalSuccess()).isZero();
                        assertThat(year.getTotalAmount()).isZero();
                    });
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindMonthlyTransactionFailed_ReturnsTwoMonthsWithZero() {
        FindTransactionMonthRange req = new FindTransactionMonthRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyTransactionFailed(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month -> {
                        assertThat(month.getTotalFailed()).isZero();
                        assertThat(month.getTotalAmount()).isZero();
                    });
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearlyTransactionFailed_ReturnsTwoYearsWithZero() {
        return repository.findYearlyTransactionFailed(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getTotalFailed()).isZero();
                        assertThat(year.getTotalAmount()).isZero();
                    });
                })
                .replaceWithVoid();
    }
}