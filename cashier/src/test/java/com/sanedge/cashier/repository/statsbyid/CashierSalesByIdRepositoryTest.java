package com.sanedge.cashier.repository.statsbyid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindCashierMonthSalesById;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class CashierSalesByIdRepositoryTest {

    @Inject
    CashierSalesByIdRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthSalesById_ReturnsEmptyWhenNoData() {
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
    }

    @Test
    @WithSession
    Uni<Void> testFindYearSalesById_ReturnsEmptyWhenNoData() {
        return repository.findYearSalesById(999999L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }
}