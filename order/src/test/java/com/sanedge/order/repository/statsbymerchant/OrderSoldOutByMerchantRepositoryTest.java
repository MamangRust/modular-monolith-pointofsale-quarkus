package com.sanedge.order.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.order.domain.requests.FindOrderMonthMerchantRange;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class OrderTotalRevenueByMerchantRepositoryTest {

    @Inject
    OrderTotalRevenueByMerchantRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthlyTotalRevenueByMerchant_ReturnsTwoMonthsWithZeroRevenue() {
        FindOrderMonthMerchantRange req = new FindOrderMonthMerchantRange();
        req.setMerchantId(999999L);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyTotalRevenueByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month ->
                            assertThat(month.getTotalRevenue()).isEqualTo(0));
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearlyTotalRevenueByMerchant_ReturnsTwoYearsWithZeroRevenue() {
        return repository.findYearlyTotalRevenueByMerchant(999999L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getYear()).isIn("2024", "2023");
                        assertThat(year.getTotalRevenue()).isEqualTo(0);
                    });
                })
                .replaceWithVoid();
    }
}