package com.sanedge.merchant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.sanedge.merchant.domain.requests.FindAllMerchantDocuments;
import com.sanedge.merchant.entity.MerchantDocument;

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
class MerchantDocumentRepositoryTest {

    @Inject
    MerchantDocumentQueryRepository queryRepo;

    @Inject
    MerchantDocumentCommandRepository commandRepo;

    // ---------- helpers ----------
    private Uni<MerchantDocument> persistDocument(Integer merchantId, String docType, String docUrl, String status, String note) {
        MerchantDocument doc = new MerchantDocument();
        doc.setMerchantId(merchantId);
        doc.setDocumentType(docType);
        doc.setDocumentUrl(docUrl);
        doc.setStatus(status);
        doc.setNote(note);
        doc.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        doc.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return queryRepo.persist(doc).map(d -> d);
    }

    private Uni<MerchantDocument> persistDocument(Integer merchantId) {
        return persistDocument(merchantId, "ID_CARD", "http://docs.com/id.jpg", "PENDING", null);
    }

    private Uni<Void> clean() {
        return queryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllMerchantDocuments findAllReq(int page, int size, String search) {
        FindAllMerchantDocuments req = new FindAllMerchantDocuments();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    // ==================== Basic CRUD ====================

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(1, "PASSPORT", "http://doc.com/pass.pdf", "APPROVED", "verified"))
                .chain(d -> queryRepo.findDocumentById(d.getDocumentId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getDocumentType()).isEqualTo("PASSPORT");
                    assertThat(found.getStatus()).isEqualTo("APPROVED");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> queryRepo.findDocumentById(99999L))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    // ==================== Query - Search & Pagination ====================

    @Test
    void testFindDocumentsWithSearch(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(1, "ID_CARD", "url1", "PENDING", "note1"))
                .chain(() -> persistDocument(1, "BUSINESS_LICENSE", "url2", "APPROVED", "note2"))
                .chain(() -> persistDocument(2, "TAX_DOC", "url3", "PENDING", "note3"))
                .chain(() -> queryRepo.findDocuments(findAllReq(1, 10, "BUSINESS")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getDocumentType()).isEqualTo("BUSINESS_LICENSE");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindDocumentsPagination(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(1))
                .chain(() -> persistDocument(1))
                .chain(() -> persistDocument(1))
                .chain(() -> persistDocument(1))
                .chain(() -> persistDocument(1))
                .chain(() -> queryRepo.findDocuments(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> queryRepo.findDocuments(findAllReq(3, 2, "")))
                .invoke(page3 -> assertThat(page3.getData()).hasSize(1))
                .replaceWithVoid());
    }

    // ==================== Active / Trashed filters ====================

    @Test
    void testFindActiveDocumentsExcludesTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(10))
                .chain(() -> persistDocument(10).chain(d -> commandRepo.trashed(d.getDocumentId()).replaceWithVoid()))
                .chain(() -> queryRepo.findActiveDocuments(findAllReq(1, 10, "")))
                .invoke(result -> assertThat(result.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedDocumentsOnlyTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(20))
                .chain(() -> persistDocument(20).chain(d -> commandRepo.trashed(d.getDocumentId()).replaceWithVoid()))
                .chain(() -> queryRepo.findTrashedDocuments(findAllReq(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    // ==================== Soft Delete (Trash) ====================

    @Test
    void testTrashDocument(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(30))
                .chain(d -> commandRepo.trashed(d.getDocumentId()))
                .invoke(trashed -> assertThat(trashed.getDeletedAt()).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashAlreadyTrashedReturnsNull(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(31))
                .chain(d -> commandRepo.trashed(d.getDocumentId())
                        .chain(() -> commandRepo.trashed(d.getDocumentId())))
                .invoke(second -> assertThat(second).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreDocument(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(32))
                .chain(d -> commandRepo.trashed(d.getDocumentId())
                        .chain(() -> commandRepo.restore(d.getDocumentId())))
                .invoke(restored -> assertThat(restored.getDeletedAt()).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreNotTrashedReturnsNull(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(33))
                .chain(d -> commandRepo.restore(d.getDocumentId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid());
    }

    // ==================== Permanent Delete ====================

    @Test
    void testDeletePermanentAfterTrash(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(34))
                .chain(d -> commandRepo.trashed(d.getDocumentId())
                        .chain(() -> commandRepo.deletePermanent(d.getDocumentId()))
                        .chain(success -> queryRepo.findDocumentById(d.getDocumentId())))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentActiveReturnsFalse(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(35))
                .chain(d -> commandRepo.deletePermanent(d.getDocumentId()))
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid());
    }

    // ==================== Bulk Operations ====================

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(1).chain(d -> commandRepo.trashed(d.getDocumentId()).replaceWithVoid()))
                .chain(() -> persistDocument(2).chain(d -> commandRepo.trashed(d.getDocumentId()).replaceWithVoid()))
                .chain(() -> commandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findTrashedDocuments(findAllReq(1, 10, "")))
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(1).chain(d -> commandRepo.trashed(d.getDocumentId()).replaceWithVoid()))
                .chain(() -> persistDocument(2).chain(d -> commandRepo.trashed(d.getDocumentId()).replaceWithVoid()))
                .chain(() -> persistDocument(3))
                .chain(() -> commandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findActiveDocuments(findAllReq(1, 10, "")))
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyDatabaseReturnsZeroRecords(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> queryRepo.findDocuments(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findActiveDocuments(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findTrashedDocuments(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid());
    }

    @Test
    void testSearchNoMatchReturnsZero(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistDocument(99))
                .chain(() -> queryRepo.findDocuments(findAllReq(1, 10, "NOMATCH")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid());
    }
}