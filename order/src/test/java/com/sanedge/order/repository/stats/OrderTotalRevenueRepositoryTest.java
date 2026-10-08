package com.sanedge.order.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.order.domain.requests.FindOrderMonthRange;

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
class OrderTotalRevenueRepositoryTest {

    @Inject
    OrderTotalRevenueRepository repository;

    @Test
    void testFindMonthlyTotalRevenue_ReturnsTwoMonthsWithZeroRevenue(UniAsserter asserter) {
        asserter.execute(() -> {
        FindOrderMonthRange req = new FindOrderMonthRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyTotalRevenue(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(month ->
                            assertThat(month.getTotalRevenue()).isEqualTo(0));
                })
                .replaceWithVoid();
    });}

    @Test
    void testFindYearlyTotalRevenue_ReturnsTwoYearsWithZeroRevenue(UniAsserter asserter) {
        asserter.execute(() -> repository.findYearlyTotalRevenue(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result).allSatisfy(year -> {
                        assertThat(year.getYear()).isIn("2024", "2023");
                        assertThat(year.getTotalRevenue()).isEqualTo(0);
                    });
                })
                .replaceWithVoid());
    }
}