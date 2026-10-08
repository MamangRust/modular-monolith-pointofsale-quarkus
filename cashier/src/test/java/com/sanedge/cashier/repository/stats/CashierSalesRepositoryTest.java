package com.sanedge.cashier.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindCashierMonthSales;

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
class CashierSalesRepositoryTest {

    @Inject
    CashierSalesRepository repository;

    @Test
    void testFindMonthSales_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> {
        FindCashierMonthSales req = new FindCashierMonthSales();
        req.setYear(2024);
        req.setStartMonth(1);
        req.setEndMonth(6);

        return repository.findMonthSales(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    });}

    @Test
    void testFindYearSales_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearSales(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }
}