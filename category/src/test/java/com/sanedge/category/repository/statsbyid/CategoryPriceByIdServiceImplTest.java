package com.sanedge.category.repository.statsbyid;

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
class CategoryPriceByIdRepositoryTest {

    @Inject
    CategoryPriceByIdRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthlyCategoryPriceById_ReturnsEmptyWhenNoData() {
        return repository.findMonthlyCategoryPriceById(999999L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearlyCategoryPriceById_ReturnsEmptyWhenNoData() {
        return repository.findYearlyCategoryPriceById(999999L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }
}