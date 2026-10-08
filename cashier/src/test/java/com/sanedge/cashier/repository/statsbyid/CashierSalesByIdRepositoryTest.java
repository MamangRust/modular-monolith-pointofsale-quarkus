package com.sanedge.cashier.repository.statsbyid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindCashierMonthSalesById;

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
class CashierSalesByIdRepositoryTest {

    @Inject
    CashierSalesByIdRepository repository;

    @Test
    void testFindMonthSalesById_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> {
        FindCashierMonthSalesById req = new FindCashierMonthSalesById();
        req.setCashierId(999999L);
        req.setYear(2024);
        req.setStartMonth(1);
        req.setEndMonth(6);

        return repository.findMonthSalesById(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    });}

    @Test
    void testFindYearSalesById_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearSalesById(999999L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }
}