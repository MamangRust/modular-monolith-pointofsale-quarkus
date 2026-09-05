package com.sanedge.transaction.repository.statsbymerchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sanedge.transaction.domain.requests.FindTransactionMonthMerchantRange;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Requires PostgreSQL-specific functions; enable after verifying DB compatibility")
@QuarkusTest
@RunOnVertxContext
class TransactionMethodByMerchantRepositoryTest {

    @Inject
    TransactionMethodByMerchantRepository repository;

    @Test
    @WithSession
    Uni<Void> testFindMonthlyMethodsSuccessByMerchant_ReturnsEmptyWhenNoData() {
        FindTransactionMonthMerchantRange req = new FindTransactionMonthMerchantRange();
        req.setMerchantId(999999L);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyTransactionMethodsSuccessByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindMonthlyMethodsFailedByMerchant_ReturnsEmptyWhenNoData() {
        FindTransactionMonthMerchantRange req = new FindTransactionMonthMerchantRange();
        req.setMerchantId(999999L);
        req.setStartYear(2024);
        req.setStartMonth(6);
        req.setEndYear(2024);
        req.setEndMonth(7);

        return repository.findMonthlyTransactionMethodsFailedByMerchant(req)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearlyMethodsSuccessByMerchant_ReturnsEmptyWhenNoData() {
        return repository.findYearlyTransactionMethodsSuccessByMerchant(999999L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }

    @Test
    @WithSession
    Uni<Void> testFindYearlyMethodsFailedByMerchant_ReturnsEmptyWhenNoData() {
        return repository.findYearlyTransactionMethodsFailedByMerchant(999999L, 2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).isEmpty();
                })
                .replaceWithVoid();
    }
}