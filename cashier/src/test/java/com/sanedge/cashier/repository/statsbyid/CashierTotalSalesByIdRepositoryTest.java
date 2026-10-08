package com.sanedge.cashier.repository.statsbyid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindCashierMonthTotalSalesById;
import com.sanedge.cashier.domain.requests.FindCashierYearTotalSalesById;

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
class CashierTotalSalesByIdRepositoryTest {

    @Inject
    CashierTotalSalesByIdRepository repository;

    @Test
    void testFindMonthTotalSalesById_ReturnsTwoMonthsWithZeroSales(UniAsserter asserter) {
        asserter.execute(() -> {
        FindCashierMonthTotalSalesById req = new FindCashierMonthTotalSalesById();
        req.setCashierId(999999L);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthTotalSalesById(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month ->
                            assertThat(month.getTotalSales()).isZero());
                })
                .replaceWithVoid();
    });}

    @Test
    void testFindYearTotalSalesById_ReturnsTwoYearsWithZeroSales(UniAsserter asserter) {
        asserter.execute(() -> {
        FindCashierYearTotalSalesById req = new FindCashierYearTotalSalesById();
        req.setCashierId(999999L);
        req.setYear(2024);
        req.setYearMinusOne(2023);

        return repository.findYearTotalSalesById(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year ->
                            assertThat(year.getTotalSales()).isZero());
                })
                .replaceWithVoid();
    });}
}