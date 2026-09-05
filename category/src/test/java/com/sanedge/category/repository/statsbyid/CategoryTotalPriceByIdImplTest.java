package com.sanedge.category.repository.statsbyid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.category.domain.requests.FindCategoryMonthTotalPriceById;
import com.sanedge.category.domain.requests.FindCategoryYearTotalPriceById;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class CategoryTotalPriceByIdRepositoryTest {

    @Inject
    CategoryTotalPriceByIdRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthlyTotalPriceByCategoryId_ReturnsTwoMonthsWithZeroRevenue() {
        FindCategoryMonthTotalPriceById req = new FindCategoryMonthTotalPriceById();
        req.setCategoryId(999999L);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyTotalPriceByCategoryId(req)
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
    Uni<Void> testFindYearlyTotalPriceByCategoryId_ReturnsTwoYearsWithZeroRevenue() {
        FindCategoryYearTotalPriceById req = new FindCategoryYearTotalPriceById();
        req.setCategoryId(999999L);
        req.setYear(2024);
        req.setYearMinusOne(2023);

        return repository.findYearlyTotalPriceByCategoryId(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year ->
                            assertThat(year.getTotalRevenue()).isZero());
                })
                .replaceWithVoid();
    }
}