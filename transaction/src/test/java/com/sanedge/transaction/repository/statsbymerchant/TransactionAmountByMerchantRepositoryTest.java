package com.sanedge.transaction.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.transaction.domain.requests.FindTransactionMonthMerchantRange;

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
class TransactionAmountByMerchantRepositoryTest {

    @Inject
    TransactionAmountByMerchantRepository repository;

    @Test
    void testFindMonthlySuccessByMerchant_ReturnsTwoMonthsWithZero(UniAsserter asserter) {
        asserter.execute(() -> {
        FindTransactionMonthMerchantRange req = new FindTransactionMonthMerchantRange();
        req.setMerchantId(999999L);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlySuccessByMerchant(req)
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
    void testFindYearlySuccessByMerchant_ReturnsTwoYearsWithZero(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearlySuccessByMerchant(999999L, 2024)
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
    void testFindMonthlyFailedByMerchant_ReturnsTwoMonthsWithZero(UniAsserter asserter) {
        asserter.execute(() -> {
        FindTransactionMonthMerchantRange req = new FindTransactionMonthMerchantRange();
        req.setMerchantId(999999L);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyFailedByMerchant(req)
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
    void testFindYearlyFailedByMerchant_ReturnsTwoYearsWithZero(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearlyFailedByMerchant(999999L, 2024)
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