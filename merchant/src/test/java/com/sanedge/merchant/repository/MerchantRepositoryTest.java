package com.sanedge.merchant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sanedge.common.enums.Status;
import com.sanedge.merchant.domain.requests.FindAllMerchants;
import com.sanedge.merchant.entity.Merchant;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import com.sanedge.common.test.PostgreSqlResource;
import io.quarkus.test.common.QuarkusTestResource;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class MerchantRepositoryTest {

    @Inject
    MerchantQueryRepository queryRepo;

    @Inject
    MerchantCommandRepository commandRepo;

    private Uni<Merchant> persistMerchant(String name, String apiKey, Long userId, Status status) {
        Merchant merchant = new Merchant();
        merchant.setName(name);
        merchant.setApiKey(apiKey);
        merchant.setUserId(userId != null ? userId.intValue() : 100);
        merchant.setStatus(status != null ? status : Status.SUCCESS);
        merchant.setMerchantNo(UUID.randomUUID());
        merchant.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        merchant.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return queryRepo.persist(merchant).map(m -> m);
    }

    private Uni<Merchant> persistMerchant(String name, String apiKey, Long userId) {
        return persistMerchant(name, apiKey, userId, Status.SUCCESS);
    }

    private Uni<Merchant> persistMerchant(String name, String apiKey) {
        return persistMerchant(name, apiKey, 100L, Status.SUCCESS);
    }

    private Uni<Void> clean() {
        return queryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllMerchants findAllReq(int page, int size, String search) {
        FindAllMerchants req = new FindAllMerchants();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    // ==================== Basic CRUD ====================

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Merchant One", "key-001"))
                .chain(m -> queryRepo.findMerchantById(m.getMerchantId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Merchant One");
                    assertThat(found.getApiKey()).isEqualTo("key-001");
                    assertThat(found.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> queryRepo.findMerchantById(99999L))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByApiKey(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Api Merchant", "secret-key"))
                .chain(() -> queryRepo.findByApiKey("secret-key"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getApiKey()).isEqualTo("secret-key");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByName(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Unique Name", "key"))
                .chain(() -> queryRepo.findByName("Unique Name"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Unique Name");
                })
                .replaceWithVoid());
    }

    @Test
    void testExistsByNameTrue(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Exists", "k1"))
                .chain(() -> queryRepo.existsByName("Exists"))
                .invoke(exists -> assertThat(exists).isTrue())
                .replaceWithVoid());
    }

    @Test
    void testExistsByNameFalse(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> queryRepo.existsByName("NonExistent"))
                .invoke(exists -> assertThat(exists).isFalse())
                .replaceWithVoid());
    }

    // ==================== Query - Search & Pagination ====================

    @Test
    void testFindMerchantsWithSearch(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Alpha Store", "key-a"))
                .chain(() -> persistMerchant("Beta Shop", "key-b"))
                .chain(() -> persistMerchant("Gamma Market", "key-c"))
                .chain(() -> queryRepo.findMerchants(findAllReq(1, 10, "beta")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Beta Shop");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindMerchantsPagination(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("A", "a"))
                .chain(() -> persistMerchant("B", "b"))
                .chain(() -> persistMerchant("C", "c"))
                .chain(() -> persistMerchant("D", "d"))
                .chain(() -> persistMerchant("E", "e"))
                .chain(() -> queryRepo.findMerchants(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> queryRepo.findMerchants(findAllReq(3, 2, "")))
                .invoke(page3 -> assertThat(page3.getData()).hasSize(1))
                .replaceWithVoid());
    }

    // ==================== Active / Trashed filters ====================

    @Test
    void testFindActiveMerchantsExcludesTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Active", "a"))
                .chain(() -> persistMerchant("ToTrash", "t")
                        .chain(m -> commandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> queryRepo.findActiveMerchants(findAllReq(1, 10, "")))
                .invoke(result -> assertThat(result.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedMerchantsOnlyTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Stay", "s"))
                .chain(() -> persistMerchant("TrashMe", "t")
                        .chain(m -> commandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> queryRepo.findTrashedMerchants(findAllReq(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("TrashMe");
                })
                .replaceWithVoid());
    }

    // ==================== Soft Delete (Trash) ====================

    @Test
    void testTrashMerchant(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Trash", "tk"))
                .chain(m -> commandRepo.trashed(m.getMerchantId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashAlreadyTrashedReturnsNull(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Double", "dk"))
                .chain(m -> commandRepo.trashed(m.getMerchantId())
                        .chain(() -> commandRepo.trashed(m.getMerchantId())))
                .invoke(second -> assertThat(second).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreMerchant(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Restore", "rk"))
                .chain(m -> commandRepo.trashed(m.getMerchantId())
                        .chain(() -> commandRepo.restore(m.getMerchantId())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreNotTrashedReturnsNull(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("ActiveR", "ark"))
                .chain(m -> commandRepo.restore(m.getMerchantId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid());
    }

    // ==================== Permanent Delete ====================

    @Test
    void testDeletePermanentAfterTrash(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("DelPerm", "dp"))
                .chain(m -> commandRepo.trashed(m.getMerchantId())
                        .chain(() -> commandRepo.deletePermanent(m.getMerchantId()))
                        .chain(success -> queryRepo.findMerchantById(m.getMerchantId())))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentActiveReturnsFalse(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("ActiveDel", "ad"))
                .chain(m -> commandRepo.deletePermanent(m.getMerchantId()))
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid());
    }

    // ==================== Bulk Operations ====================

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("A1", "a1").chain(m -> commandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> persistMerchant("A2", "a2").chain(m -> commandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> commandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findTrashedMerchants(findAllReq(1, 10, "")))
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("D1", "d1").chain(m -> commandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> persistMerchant("D2", "d2").chain(m -> commandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> persistMerchant("Keep", "keep"))
                .chain(() -> commandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findActiveMerchants(findAllReq(1, 10, "")))
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    // ==================== Update Status ====================

    @Test
    void testUpdateStatusToSuspended(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Status", "st", 100L, Status.SUCCESS))
                .chain(m -> commandRepo.updateStatus(m.getMerchantId(), "FAILED")
                        .chain(() -> queryRepo.findMerchantById(m.getMerchantId())))
                .invoke(updated -> assertThat(updated.getStatus()).isEqualTo(Status.FAILED))
                .replaceWithVoid());
    }

    @Test
    void testUpdateStatusInvalid(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Invalid", "inv"))
                .chain(m -> commandRepo.updateStatus(m.getMerchantId(), "INVALID"))
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid());
    }

    // ==================== findByUserId ====================

    @Test
    void testFindByUserId(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("U1", "u1", 42L))
                .chain(() -> persistMerchant("U2", "u2", 42L))
                .chain(() -> queryRepo.findByUserId(42L))
                .invoke(list -> assertThat(list).hasSize(2))
                .replaceWithVoid());
    }

    @Test
    void testFindByUserIdNullReturnsEmpty(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> queryRepo.findByUserId(null))
                .invoke(list -> assertThat(list).isEmpty())
                .replaceWithVoid());
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyDatabaseReturnsZeroRecords(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> queryRepo.findMerchants(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findActiveMerchants(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findTrashedMerchants(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid());
    }

    @Test
    void testSearchNoMatchReturnsZero(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistMerchant("Something", "sm"))
                .chain(() -> queryRepo.findMerchants(findAllReq(1, 10, "NOMATCH")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid());
    }
}