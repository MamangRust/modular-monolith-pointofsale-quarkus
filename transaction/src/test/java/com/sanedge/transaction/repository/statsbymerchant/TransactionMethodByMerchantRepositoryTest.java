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
class TransactionMethodByMerchantRepositoryTest {

    @Inject
    TransactionMethodByMerchantRepository repository;

    @Test
    void testFindMonthlyMethodsSuccessByMerchant_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> {
        FindTransactionMonthMerchantRange req = new FindTransactionMonthMerchantRange();
        req.setMerchantId(999999L);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyTransactionMethodsSuccessByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    });}

    @Test
    void testFindMonthlyMethodsFailedByMerchant_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> {
        FindTransactionMonthMerchantRange req = new FindTransactionMonthMerchantRange();
        req.setMerchantId(999999L);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyTransactionMethodsFailedByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    });}

    @Test
    void testFindYearlyMethodsSuccessByMerchant_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearlyTransactionMethodsSuccessByMerchant(999999L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyMethodsFailedByMerchant_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearlyTransactionMethodsFailedByMerchant(999999L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }
}