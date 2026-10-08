package com.sanedge.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.sanedge.common.enums.PaymentStatus;
import com.sanedge.transaction.domain.requests.FindAllTransactionByMerchantRequest;
import com.sanedge.transaction.domain.requests.FindAllTransactionRequest;
import com.sanedge.transaction.entity.Transaction;

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
class TransactionRepositoryTest {

    @Inject
    TransactionQueryRepository queryRepo;

    @Inject
    TransactionCommandRepository commandRepo;

    // ---------- helpers ----------
    private Uni<Transaction> persistTransaction(Long merchantId, String paymentMethod, PaymentStatus status) {
        Transaction tx = new Transaction();
        tx.setMerchantId(merchantId);
        tx.setPaymentMethod(paymentMethod);
        tx.setStatus(status);
        tx.setAmount(100000);
        tx.setOrderId(merchantId * 1000 + 1L);
        tx.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        tx.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return queryRepo.persist(tx).map(t -> t);
    }

    private Uni<Transaction> persistTransaction(Long merchantId, String paymentMethod) {
        return persistTransaction(merchantId, paymentMethod, PaymentStatus.PENDING);
    }

    private Uni<Void> clean() {
        return queryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllTransactionRequest findAllReq(int page, int size, String search) {
        FindAllTransactionRequest req = new FindAllTransactionRequest();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    private FindAllTransactionByMerchantRequest findAllByMerchantReq(Long merchantId, int page, int size, String search) {
        FindAllTransactionByMerchantRequest req = new FindAllTransactionByMerchantRequest();
        req.setMerchantId(merchantId.intValue());
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    // ==================== Basic CRUD ====================

    @Test
    void testCreateAndFindByTransactionId(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(10L, "CREDIT", PaymentStatus.SUCCESS))
                .chain(tx -> queryRepo.findByTransactionId(tx.getTransactionId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getPaymentMethod()).isEqualTo("CREDIT");
                    assertThat(found.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByTransactionIdReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> queryRepo.findByTransactionId(99999L))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByOrderId(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> {
                    Transaction tx = new Transaction();
                    tx.setMerchantId(1L);
                    tx.setPaymentMethod("DEBIT");
                    tx.setStatus(PaymentStatus.PENDING);
                    tx.setAmount(50000);
                    tx.setOrderId(555L);
                    tx.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    tx.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    return queryRepo.persist(tx);
                })
                .chain(tx -> queryRepo.findByOrderId(555L))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getOrderId()).isEqualTo(555L);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByOrderIdReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> queryRepo.findByOrderId(99999L))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByIdempotencyKey(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> {
                    Transaction tx = new Transaction();
                    tx.setMerchantId(1L);
                    tx.setPaymentMethod("CASH");
                    tx.setStatus(PaymentStatus.PENDING);
                    tx.setAmount(50000);
                    tx.setOrderId(1L);
                    tx.setIdempotencyKey("idem-repo-1");
                    tx.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    tx.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    return queryRepo.persist(tx);
                })
                .chain(tx -> queryRepo.findByIdempotencyKey("idem-repo-1"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getIdempotencyKey()).isEqualTo("idem-repo-1");
                })
                .chain(() -> queryRepo.findByIdempotencyKey("idem-repo-missing"))
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid());
    }

    // ==================== Query - Search & Pagination ====================

    @Test
    void testFindTransactionsWithSearchByPaymentMethod(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(1L, "CREDIT"))
                .chain(() -> persistTransaction(1L, "DEBIT"))
                .chain(() -> persistTransaction(1L, "CASH"))
                .chain(() -> queryRepo.findTransactions(findAllReq(1, 10, "credit")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getPaymentMethod()).isEqualTo("CREDIT");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTransactionsPagination(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(1L, "A"))
                .chain(() -> persistTransaction(1L, "B"))
                .chain(() -> persistTransaction(1L, "C"))
                .chain(() -> persistTransaction(1L, "D"))
                .chain(() -> persistTransaction(1L, "E"))
                .chain(() -> queryRepo.findTransactions(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> queryRepo.findTransactions(findAllReq(3, 2, "")))
                .invoke(page3 -> assertThat(page3.getData()).hasSize(1))
                .replaceWithVoid());
    }

    // ==================== Active / Trashed filters ====================

    @Test
    void testFindActiveTransactionsExcludesTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(1L, "CREDIT"))
                .chain(() -> persistTransaction(1L, "DEBIT").chain(tx -> commandRepo.trashed(tx.getTransactionId()).replaceWithVoid()))
                .chain(() -> queryRepo.findActiveTransactions(findAllReq(1, 10, "")))
                .invoke(result -> assertThat(result.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedTransactionsOnlyTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(1L, "CREDIT"))
                .chain(() -> persistTransaction(1L, "DEBIT").chain(tx -> commandRepo.trashed(tx.getTransactionId()).replaceWithVoid()))
                .chain(() -> queryRepo.findTrashedTransactions(findAllReq(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getPaymentMethod()).isEqualTo("DEBIT");
                })
                .replaceWithVoid());
    }

    // ==================== findByMerchant ====================

    @Test
    void testFindTransactionsByMerchant(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(100L, "CREDIT"))
                .chain(() -> persistTransaction(100L, "CASH"))
                .chain(() -> persistTransaction(200L, "DEBIT"))
                .chain(() -> queryRepo.findTransactionsByMerchant(findAllByMerchantReq(100L, 1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                    assertThat(result.getData().stream().allMatch(tx -> tx.getMerchantId().equals(100L))).isTrue();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTransactionsByMerchantWithSearch(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(50L, "QRIS", PaymentStatus.SUCCESS))
                .chain(() -> persistTransaction(50L, "BANK_TRANSFER", PaymentStatus.FAILED))
                .chain(() -> persistTransaction(50L, "CASH", PaymentStatus.PENDING))
                .chain(() -> queryRepo.findTransactionsByMerchant(findAllByMerchantReq(50L, 1, 10, "failed")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getPaymentMethod()).isEqualTo("BANK_TRANSFER");
                    assertThat(result.getData().get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
                })
                .replaceWithVoid());
    }

    // ==================== Soft Delete (Trash) ====================

    @Test
    void testTrashTransaction(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(1L, "CREDIT"))
                .chain(tx -> commandRepo.trashed(tx.getTransactionId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashAlreadyTrashedReturnsSame(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(1L, "CREDIT"))
                .chain(tx -> commandRepo.trashed(tx.getTransactionId())
                        .chain(() -> commandRepo.trashed(tx.getTransactionId())))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreTransaction(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(1L, "CREDIT"))
                .chain(tx -> commandRepo.trashed(tx.getTransactionId())
                        .chain(() -> commandRepo.restore(tx.getTransactionId())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreNonExistentReturnsNull(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> commandRepo.restore(99999L))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid());
    }

    // ==================== Permanent Delete ====================

    @Test
    void testDeletePermanentAfterTrash(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(1L, "CREDIT"))
                .chain(tx -> commandRepo.trashed(tx.getTransactionId())
                        .chain(() -> commandRepo.deletePermanent(tx.getTransactionId()))
                        .chain(perm -> queryRepo.findByTransactionId(tx.getTransactionId())))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentActiveReturnsNull(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(1L, "CREDIT"))
                .chain(tx -> commandRepo.deletePermanent(tx.getTransactionId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid());
    }

    // ==================== Bulk Operations ====================

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(1L, "A").chain(tx -> commandRepo.trashed(tx.getTransactionId()).replaceWithVoid()))
                .chain(() -> persistTransaction(1L, "B").chain(tx -> commandRepo.trashed(tx.getTransactionId()).replaceWithVoid()))
                .chain(() -> commandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findTrashedTransactions(findAllReq(1, 10, "")))
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(1L, "X").chain(tx -> commandRepo.trashed(tx.getTransactionId()).replaceWithVoid()))
                .chain(() -> persistTransaction(1L, "Y").chain(tx -> commandRepo.trashed(tx.getTransactionId()).replaceWithVoid()))
                .chain(() -> persistTransaction(1L, "Z")) // stays active
                .chain(() -> commandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findActiveTransactions(findAllReq(1, 10, "")))
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyDatabaseReturnsZeroRecords(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> queryRepo.findTransactions(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findActiveTransactions(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findTrashedTransactions(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid());
    }

    @Test
    void testSearchNoMatchReturnsZero(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistTransaction(1L, "CREDIT"))
                .chain(() -> queryRepo.findTransactions(findAllReq(1, 10, "NOMATCH")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid());
    }
}