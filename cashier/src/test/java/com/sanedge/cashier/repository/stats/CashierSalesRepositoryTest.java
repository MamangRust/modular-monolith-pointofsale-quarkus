package com.sanedge.cashier.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindCashierMonthSales;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class CashierSalesRepositoryTest {

    @Inject
    CashierSalesRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthSales_ReturnsEmptyWhenNoData() {
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
    }

    @Test
    @WithSession
    Uni<Void> testFindYearSales_ReturnsEmptyWhenNoData() {
        return repository.findYearSales(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }
}