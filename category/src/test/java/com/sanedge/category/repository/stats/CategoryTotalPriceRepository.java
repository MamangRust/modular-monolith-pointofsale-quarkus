package com.sanedge.category.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.category.domain.requests.FindCategoryMonthTotalPriceRange;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class CategoryTotalPriceRepositoryTest {

    @Inject
    CategoryTotalPriceRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthlyTotalPrice_ReturnsTwoMonthsWithZeroRevenue() {
        FindCategoryMonthTotalPriceRange req = new FindCategoryMonthTotalPriceRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyTotalPrice(req)
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
    Uni<Void> testFindYearlyTotalPrice_ReturnsTwoYearsWithZeroRevenue() {
        return repository.findYearlyTotalPrice(2024, 2023)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getYear()).isIn("2024", "2023");
                        assertThat(year.getTotalRevenue()).isZero();
                    });
                })
                .replaceWithVoid();
    }
}