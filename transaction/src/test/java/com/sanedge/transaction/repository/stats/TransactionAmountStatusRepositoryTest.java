package com.sanedge.transaction.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.transaction.domain.requests.FindTransactionMonthRange;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import com.sanedge.common.test.PostgreSqlResource;
import io.quarkus.test.common.QuarkusTestResource;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class TransactionAmountStatusRepositoryTest {

    @Inject
    TransactionAmountStatusRepository repository;

    @Test
    void testFindMonthlyTransactionSuccess_ReturnsTwoMonthsWithZero(UniAsserter asserter) {
        asserter.execute(() -> {
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
    });}

    @Test
    void testFindYearlyTransactionSuccess_ReturnsTwoYearsWithZero(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearlyTransactionSuccess(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getTotalSuccess()).isZero();
                        assertThat(year.getTotalAmount()).isZero();
                    });
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMonthlyTransactionFailed_ReturnsTwoMonthsWithZero(UniAsserter asserter) {
        asserter.execute(() -> {
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
    });}

    @Test
    void testFindYearlyTransactionFailed_ReturnsTwoYearsWithZero(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearlyTransactionFailed(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getTotalFailed()).isZero();
                        assertThat(year.getTotalAmount()).isZero();
                    });
                })
                .replaceWithVoid());
    }
}