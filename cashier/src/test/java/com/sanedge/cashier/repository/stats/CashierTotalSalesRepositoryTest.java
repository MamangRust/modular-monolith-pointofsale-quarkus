package com.sanedge.cashier.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindMonthTotalSalesRange;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class CashierTotalSalesRepositoryTest {

    @Inject
    CashierTotalSalesRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthTotalSales_ReturnsTwoMonthsWithZeroSales() {
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
    }

    @Test
    @WithSession
    Uni<Void> testFindYearTotalSales_ReturnsTwoYearsWithZeroSales() {
        return repository.findYearTotalSales(2024, 2023)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getYear()).isIn("2024", "2023");
                        assertThat(year.getTotalSales()).isZero();
                    });
                })
                .replaceWithVoid();
    }
}