package com.sanedge.cashier.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindCashierMonthTotalSalesByMerchant;
import com.sanedge.cashier.domain.requests.FindCashierYearTotalSalesByMerchant;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class CashierTotalSalesByMerchantRepositoryTest {

    @Inject
    CashierTotalSalesByMerchantRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthTotalSalesByMerchant_ReturnsTwoMonthsWithZeroSales() {
        FindCashierMonthTotalSalesByMerchant req = new FindCashierMonthTotalSalesByMerchant();
        req.setMerchantId(999999L);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthTotalSalesByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month ->
                            assertThat(month.getTotalSales()).isZero());
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearTotalSalesByMerchant_ReturnsTwoYearsWithZeroSales() {
        FindCashierYearTotalSalesByMerchant req = new FindCashierYearTotalSalesByMerchant();
        req.setMerchantId(999999L);
        req.setYear(2024);
        req.setYearMinusOne(2023);

        return repository.findYearTotalSalesByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year ->
                            assertThat(year.getTotalSales()).isZero());
                })
                .replaceWithVoid();
    }
}