package com.sanedge.cashier.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.sanedge.cashier.entity.Cashier;
import com.sanedge.common.test.PostgreSqlResource;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@RunOnVertxContext
class CashierRepositoryTest {

    @Inject
    CashierCommandRepository cashierCommandRepository;

    @Inject
    CashierQueryRepository cashierQueryRepository;

    private Cashier createCashier(String name, Long merchantId, Long userId) {
        Cashier cashier = new Cashier();
        cashier.setName(name);
        cashier.setMerchantId(merchantId);
        cashier.setUserId(userId);
        return cashier;
    }

    @Test
    @Order(1)
    @WithSession
    Uni<Void> testCreateCashier_success() {
        Cashier cashier = createCashier("Cashier One", 1L, 1L);
        return cashierCommandRepository.persist(cashier)
                .invoke(persisted -> {
                    assertThat(persisted).isNotNull();
                    assertThat(persisted.getCashierId()).isNotNull();
                    assertThat(persisted.getName()).isEqualTo("Cashier One");
                    assertThat(persisted.getMerchantId()).isEqualTo(1L);
                    assertThat(persisted.getUserId()).isEqualTo(1L);
                    assertThat(persisted.getCreatedAt()).isNotNull();
                    assertThat(persisted.getUpdatedAt()).isNotNull();
                })
                .replaceWithVoid();
    }

    @Test
    @Order(2)
    @WithSession
    Uni<Void> testFindByCashierId_exists() {
        return cashierQueryRepository.findByCashierId(1L)
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid();
    }

    @Test
    @Order(3)
    @WithSession
    Uni<Void> testFindByCashierId_notFound() {
        return cashierQueryRepository.findByCashierId(999L)
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid();
    }

    @Test
    @Order(4)
    @WithSession
    Uni<Void> testFindByNameAndMerchantId_exists() {
        return cashierQueryRepository.findByNameAndMerchantId("Cashier One", 1L)
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid();
    }

    @Test
    @Order(5)
    @WithSession
    Uni<Void> testFindByNameAndMerchantId_caseInsensitive() {
        return cashierQueryRepository.findByNameAndMerchantId("cashier one", 1L)
                .invoke(found -> assertThat(found).isNotNull())
                .replaceWithVoid();
    }

    @Test
    @Order(6)
    @WithSession
    Uni<Void> testFindByNameAndMerchantId_notFound() {
        return cashierQueryRepository.findByNameAndMerchantId("NonExistent", 1L)
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid();
    }

    @Test
    @Order(7)
    @WithSession
    Uni<Void> testCreateSecondCashier() {
        Cashier cashier = createCashier("Cashier Two", 2L, 2L);
        return cashierCommandRepository.persist(cashier)
                .invoke(persisted -> {
                    assertThat(persisted).isNotNull();
                    assertThat(persisted.getCashierId()).isNotNull();
                })
                .replaceWithVoid();
    }

    @Test
    @Order(8)
    @WithSession
    Uni<Void> testFindAllCashiers_paginationAndSearch() {
        var req = new com.sanedge.cashier.domain.requests.FindAllCashiers();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch(null);

        return cashierQueryRepository.findAllCashiers(req)
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(2);
                    assertThat(pagedResult.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid();
    }

    @Test
    @Order(9)
    @WithSession
    Uni<Void> testFindAllCashiers_withSearchKeyword() {
        var req = new com.sanedge.cashier.domain.requests.FindAllCashiers();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("One");

        return cashierQueryRepository.findAllCashiers(req)
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(1);
                    assertThat(pagedResult.getData().get(0).getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid();
    }

    @Test
    @Order(10)
    @WithSession
    Uni<Void> testTrashCashier_success() {
        return cashierCommandRepository.trashed(1L)
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid();
    }

    @Test
    @Order(11)
    @WithSession
    Uni<Void> testTrashCashier_alreadyTrashed_returnsSame() {
        return cashierCommandRepository.trashed(1L)
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid();
    }

    @Test
    @Order(12)
    @WithSession
    Uni<Void> testFindActiveCashiers_onlyActive() {
        var req = new com.sanedge.cashier.domain.requests.FindAllCashiers();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch(null);

        return cashierQueryRepository.findActiveCashiers(req)
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(1);
                    assertThat(pagedResult.getData().get(0).getName()).isEqualTo("Cashier Two");
                })
                .replaceWithVoid();
    }

    @Test
    @Order(13)
    @WithSession
    Uni<Void> testFindTrashedCashiers_onlyTrashed() {
        var req = new com.sanedge.cashier.domain.requests.FindAllCashiers();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch(null);

        return cashierQueryRepository.findTrashedCashiers(req)
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(1);
                    assertThat(pagedResult.getData().get(0).getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid();
    }

    @Test
    @Order(14)
    @WithSession
    Uni<Void> testRestoreCashier_success() {
        return cashierCommandRepository.restore(1L)
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid();
    }

    @Test
    @Order(15)
    @WithSession
    Uni<Void> testRestoreCashier_notFound() {
        return cashierCommandRepository.restore(999L)
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid();
    }

    @Test
    @Order(16)
    @WithSession
    Uni<Void> testFindByMerchants_withMerchantAndSearch() {
        var req = new com.sanedge.cashier.domain.requests.FindAllCashierMerchant();
        req.setMerchantId(1);
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch(null);

        return cashierQueryRepository.findByMerchants(req)
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(1);
                })
                .replaceWithVoid();
    }

    @Test
    @Order(17)
    @WithSession
    Uni<Void> testTrashSecondCashier() {
        return cashierCommandRepository.trashed(2L)
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid();
    }

    @Test
    @Order(18)
    @WithSession
    Uni<Void> testRestoreAllDeleted_success() {
        return cashierCommandRepository.restoreAllDeleted()
                .invoke(result -> assertThat(result).isTrue())
                .replaceWithVoid();
    }

    @Test
    @Order(19)
    @WithSession
    Uni<Void> testRestoreAllDeleted_whenNoneTrashed_returnsFalse() {
        return cashierCommandRepository.restoreAllDeleted()
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid();
    }
}
