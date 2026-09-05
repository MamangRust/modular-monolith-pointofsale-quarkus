package com.sanedge.order.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.order.domain.requests.FindOrderMonthRange;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class OrderSoldOutRepositoryTest {

    @Inject
    OrderSoldOutRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthlyOrdersByYear_ReturnsEmptyWhenNoData() {
        return repository.findMonthlyOrdersByYear(202401)   // January 2024
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearlyOrders_ReturnsEmptyWhenNoData() {
        return repository.findYearlyOrders(202401)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }
}