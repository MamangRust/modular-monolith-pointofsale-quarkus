package com.sanedge.category.repository.statsbymerchant;

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
class CategoryPriceByMerchantRepositoryTest {

    @Inject
    CategoryPriceByMerchantRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthlyCategoryPriceByMerchant_ReturnsEmptyWhenNoData() {
        return repository.findMonthlyCategoryPriceByMerchant(999999L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearlyCategoryPriceByMerchant_ReturnsEmptyWhenNoData() {
        return repository.findYearlyCategoryPriceByMerchant(999999L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }
}