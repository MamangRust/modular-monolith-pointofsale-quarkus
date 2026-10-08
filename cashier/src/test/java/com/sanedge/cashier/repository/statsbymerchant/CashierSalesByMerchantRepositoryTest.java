package com.sanedge.cashier.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindCashierMonthSalesByMerchant;

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
class CashierSalesByMerchantRepositoryTest {

    @Inject
    CashierSalesByMerchantRepository repository;

    @Test
    void testFindMonthSalesByMerchant_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> {
        FindCashierMonthSalesByMerchant req = new FindCashierMonthSalesByMerchant();
        req.setMerchantId(999999L);
        req.setYear(2024);
        req.setStartMonth(1);
        req.setEndMonth(6);

        return repository.findMonthSalesByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    });}

    @Test
    void testFindYearSalesByMerchant_ReturnsEmptyWhenNoData(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearSalesByMerchant(999999L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid());
    }
}