package com.sanedge.cashier.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindMonthTotalSalesRange;

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
class CashierTotalSalesRepositoryTest {

    @Inject
    CashierTotalSalesRepository repository;

    @Test
    void testFindMonthTotalSales_ReturnsTwoMonthsWithZeroSales(UniAsserter asserter) {
        asserter.execute(() -> {
        FindMonthTotalSalesRange req = new FindMonthTotalSalesRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthTotalSales(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month -> {
                        assertThat(month.getMonth()).isNotNull();
                        assertThat(month.getTotalSales()).isZero();
                    });
                })
                .replaceWithVoid();
    });}

    @Test
    void testFindYearTotalSales_ReturnsTwoYearsWithZeroSales(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearTotalSales(2024, 2023)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getYear()).isIn("2024", "2023");
                        assertThat(year.getTotalSales()).isZero();
                    });
                })
                .replaceWithVoid());
    }
}