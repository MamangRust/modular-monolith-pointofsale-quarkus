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
class TransactionMethodRepositoryTest {

    @Inject
    TransactionMethodRepository repository;

    @Test
    void testFindMonthlyMethodsSuccess_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> {
        FindTransactionMonthRange req = new FindTransactionMonthRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyMethodsSuccess(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    // No payment methods exist => cross join yields empty
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    });}

    @Test
    void testFindMonthlyMethodsFailed_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> {
        FindTransactionMonthRange req = new FindTransactionMonthRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyMethodsFailed(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    });}

    @Test
    void testFindYearlyMethodsSuccess_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearlyMethodsSuccess(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindYearlyMethodsFailed_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearlyMethodsFailed(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }
}