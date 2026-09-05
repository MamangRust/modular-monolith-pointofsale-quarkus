package com.sanedge.category.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.category.domain.requests.FindCategoryMonthTotalPriceByMerchant;
import com.sanedge.category.domain.requests.FindCategoryYearTotalPriceByMerchant;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class CategoryTotalPriceByMerchantRepositoryTest {

    @Inject
    CategoryTotalPriceByMerchantRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthlyTotalPriceByMerchant_ReturnsTwoMonthsWithZeroRevenue() {
        FindCategoryMonthTotalPriceByMerchant req = new FindCategoryMonthTotalPriceByMerchant();
        req.setMerchantId(999999L);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyTotalPriceByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month ->
                            assertThat(month.getTotalRevenue()).isZero());
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearlyTotalPriceByMerchant_ReturnsTwoYearsWithZeroRevenue() {
        FindCategoryYearTotalPriceByMerchant req = new FindCategoryYearTotalPriceByMerchant();
        req.setMerchantId(999999L);
        req.setYear(2024);
        req.setYearMinusOne(2023);

        return repository.findYearlyTotalPriceByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year ->
                            assertThat(year.getTotalRevenue()).isZero());
                })
                .replaceWithVoid();
    }
}