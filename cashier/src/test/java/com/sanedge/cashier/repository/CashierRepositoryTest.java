package com.sanedge.cashier.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.cashier.domain.requests.FindAllCashierMerchant;
import com.sanedge.cashier.domain.requests.FindAllCashiers;
import com.sanedge.cashier.entity.Cashier;
import com.sanedge.common.test.PostgreSqlResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class CashierRepositoryTest {

    @Inject
    CashierCommandRepository cashierCommandRepository;

    @Inject
    CashierQueryRepository cashierQueryRepository;

    // ---------- helpers ----------

    private Uni<Void> clean() {
        return cashierCommandRepository.deleteAll().replaceWithVoid();
    }

    private Uni<Cashier> persistCashier(String name, Long merchantId, Long userId) {
        Cashier cashier = new Cashier();
        cashier.setName(name);
        cashier.setMerchantId(merchantId);
        cashier.setUserId(userId);
        return cashierCommandRepository.persist(cashier);
    }

    private FindAllCashiers findAllReq(int page, int size, String search) {
        FindAllCashiers req = new FindAllCashiers();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search);
        return req;
    }

    private FindAllCashierMerchant findByMerchantReq(Integer merchantId, int page, int size, String search) {
        FindAllCashierMerchant req = new FindAllCashierMerchant();
        req.setMerchantId(merchantId);
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search);
        return req;
    }

    // ---------- tests ----------

    @Test
    void testCreateCashier_success(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .invoke(persisted -> {
                    assertThat(persisted).isNotNull();
                    assertThat(persisted.getCashierId()).isNotNull();
                    assertThat(persisted.getName()).isEqualTo("Cashier One");
                    assertThat(persisted.getMerchantId()).isEqualTo(1L);
                    assertThat(persisted.getUserId()).isEqualTo(1L);
                    assertThat(persisted.getCreatedAt()).isNotNull();
                    assertThat(persisted.getUpdatedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByCashierId_exists(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(saved -> cashierQueryRepository.findByCashierId(saved.getCashierId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByCashierId_notFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> cashierQueryRepository.findByCashierId(999L))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByNameAndMerchantId_exists(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(() -> cashierQueryRepository.findByNameAndMerchantId("Cashier One", 1L))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByNameAndMerchantId_caseInsensitive(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(() -> cashierQueryRepository.findByNameAndMerchantId("cashier one", 1L))
                .invoke(found -> assertThat(found).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByNameAndMerchantId_notFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(() -> cashierQueryRepository.findByNameAndMerchantId("NonExistent", 1L))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindAllCashiers_paginationAndSearch(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(() -> persistCashier("Cashier Two", 2L, 2L))
                .chain(() -> cashierQueryRepository.findAllCashiers(findAllReq(1, 10, null)))
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(2);
                    assertThat(pagedResult.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindAllCashiers_withSearchKeyword(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(() -> persistCashier("Cashier Two", 2L, 2L))
                .chain(() -> cashierQueryRepository.findAllCashiers(findAllReq(1, 10, "One")))
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(1);
                    assertThat(pagedResult.getData().get(0).getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashCashier_success(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(saved -> cashierCommandRepository.trashed(saved.getCashierId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashCashier_alreadyTrashed_returnsSame(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(saved -> cashierCommandRepository.trashed(saved.getCashierId())
                        .chain(ignored -> cashierCommandRepository.trashed(saved.getCashierId())))
                .invoke(trashedAgain -> {
                    assertThat(trashedAgain).isNotNull();
                    assertThat(trashedAgain.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindActiveCashiers_onlyActive(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(first -> persistCashier("Cashier Two", 2L, 2L)
                        .chain(second -> cashierCommandRepository.trashed(first.getCashierId())
                                .replaceWith(second)))
                .chain(() -> cashierQueryRepository.findActiveCashiers(findAllReq(1, 10, null)))
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(1);
                    assertThat(pagedResult.getData().get(0).getName()).isEqualTo("Cashier Two");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedCashiers_onlyTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(first -> persistCashier("Cashier Two", 2L, 2L)
                        .chain(second -> cashierCommandRepository.trashed(first.getCashierId())
                                .replaceWith(second)))
                .chain(() -> cashierQueryRepository.findTrashedCashiers(findAllReq(1, 10, null)))
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(1);
                    assertThat(pagedResult.getData().get(0).getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreCashier_success(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(saved -> cashierCommandRepository.trashed(saved.getCashierId())
                        .chain(ignored -> cashierCommandRepository.restore(saved.getCashierId())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreCashier_notFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> cashierCommandRepository.restore(999L))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByMerchants_withMerchantAndSearch(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(() -> persistCashier("Cashier Two", 2L, 2L))
                .chain(() -> cashierQueryRepository.findByMerchants(findByMerchantReq(1, 1, 10, null)))
                .invoke(pagedResult -> {
                    assertThat(pagedResult).isNotNull();
                    assertThat(pagedResult.getData()).hasSize(1);
                    assertThat(pagedResult.getData().get(0).getName()).isEqualTo("Cashier One");
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashSecondCashier(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(first -> persistCashier("Cashier Two", 2L, 2L))
                .chain(second -> cashierCommandRepository.trashed(second.getCashierId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted_success(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(first -> persistCashier("Cashier Two", 2L, 2L)
                        .chain(second -> cashierCommandRepository.trashed(first.getCashierId())
                                .chain(ignored -> cashierCommandRepository.trashed(second.getCashierId()))))
                .chain(() -> cashierCommandRepository.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted_whenNoneTrashed_returnsFalse(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCashier("Cashier One", 1L, 1L))
                .chain(() -> cashierCommandRepository.restoreAllDeleted())
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid());
    }
}
