package com.sanedge.order.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class OrderSoldOutByMerchantRepositoryTest {

    @Inject
    OrderSoldOutByMerchantRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthlyOrdersByMerchant_ReturnsEmptyWhenNoData() {
        return repository.findMonthlyOrdersByMerchant(999999, 202401)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearlyOrdersByMerchant_ReturnsEmptyWhenNoData() {
        return repository.findYearlyOrdersByMerchant(999999, 202401)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }
}