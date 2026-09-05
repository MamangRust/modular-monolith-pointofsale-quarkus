package com.sanedge.transaction.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.transaction.domain.requests.FindTransactionMonthRange;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class TransactionMethodRepositoryTest {

    @Inject
    TransactionMethodRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthlyMethodsSuccess_ReturnsEmptyWhenNoData() {
        FindTransactionMonthRange req = new FindTransactionMonthRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyMethodsSuccess(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    // No payment methods exist => cross join yields empty
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindMonthlyMethodsFailed_ReturnsEmptyWhenNoData() {
        FindTransactionMonthRange req = new FindTransactionMonthRange();
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyMethodsFailed(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearlyMethodsSuccess_ReturnsEmptyWhenNoData() {
        return repository.findYearlyMethodsSuccess(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearlyMethodsFailed_ReturnsEmptyWhenNoData() {
        return repository.findYearlyMethodsFailed(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }
}