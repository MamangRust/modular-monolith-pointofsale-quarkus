package com.sanedge.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.sanedge.order.domain.requests.FindAllOrderByMerchantRequest;
import com.sanedge.order.domain.requests.FindAllOrderRequest;
import com.sanedge.order.entity.Order;

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
class OrderRepositoryTest {

    @Inject
    OrderQueryRepository queryRepo;

    @Inject
    OrderCommandRepository commandRepo;

    // ---------- helpers ----------
    private Uni<Order> persistOrder(Long cashierId, Long merchantId, Long totalPrice) {
        Order order = new Order();
        order.setCashierId(cashierId);
        order.setMerchantId(merchantId);
        order.setTotalPrice(totalPrice);
        order.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        order.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return queryRepo.persist(order).map(o -> o);
    }

    private Uni<Order> persistOrder(Long cashierId, Long merchantId) {
        return persistOrder(cashierId, merchantId, 100000L);
    }

    private Uni<Void> clean() {
        return queryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllOrderRequest findAllReq(int page, int size, String search) {
        FindAllOrderRequest req = new FindAllOrderRequest();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    private FindAllOrderByMerchantRequest findAllByMerchantReq(Long merchantId, int page, int size, String search) {
        FindAllOrderByMerchantRequest req = new FindAllOrderByMerchantRequest();
        req.setMerchantId(merchantId.intValue());
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(100L, 10L, 150000L))
                .chain(o -> queryRepo.findOrderById(o.getOrderId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getCashierId()).isEqualTo(100L);
                    assertThat(found.getMerchantId()).isEqualTo(10L);
                    assertThat(found.getTotalPrice()).isEqualTo(150000L);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> queryRepo.findOrderById(99999L))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    // ==================== Query - Search & Pagination ====================

    @Test
    void testFindOrdersWithSearchById(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(1L, 1L))
                .chain(() -> persistOrder(2L, 1L))
                .chain(() -> persistOrder(3L, 1L))
                .chain(() -> queryRepo.findOrders(findAllReq(1, 10, "2")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getCashierId()).isEqualTo(2L);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindOrdersPagination(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(1L, 1L))
                .chain(() -> persistOrder(2L, 1L))
                .chain(() -> persistOrder(3L, 1L))
                .chain(() -> persistOrder(4L, 1L))
                .chain(() -> persistOrder(5L, 1L))
                .chain(() -> queryRepo.findOrders(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> queryRepo.findOrders(findAllReq(3, 2, "")))
                .invoke(page3 -> assertThat(page3.getData()).hasSize(1))
                .replaceWithVoid());
    }

    @Test
    void testFindOrdersByMerchant(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(10L, 100L))
                .chain(() -> persistOrder(20L, 100L))
                .chain(() -> persistOrder(30L, 200L))
                .chain(() -> queryRepo.findOrdersByMerchant(findAllByMerchantReq(100L, 1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                    assertThat(result.getData().stream().allMatch(o -> o.getMerchantId() == 100L)).isTrue();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindOrdersByMerchantWithSearch(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(111L, 50L))
                .chain(() -> persistOrder(222L, 50L))
                .chain(() -> persistOrder(333L, 50L))
                .chain(() -> queryRepo.findOrdersByMerchant(findAllByMerchantReq(50L, 1, 10, "111")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getCashierId()).isEqualTo(111L);
                })
                .replaceWithVoid());
    }

    // ==================== Active / Trashed filters ====================

    @Test
    void testFindActiveOrdersExcludesTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(1L, 1L))
                .chain(() -> persistOrder(2L, 1L).chain(o -> commandRepo.trashed(o.getOrderId()).replaceWithVoid()))
                .chain(() -> queryRepo.findActiveOrders(findAllReq(1, 10, "")))
                .invoke(result -> assertThat(result.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedOrdersOnlyTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(1L, 1L))
                .chain(() -> persistOrder(2L, 1L).chain(o -> commandRepo.trashed(o.getOrderId()).replaceWithVoid()))
                .chain(() -> queryRepo.findTrashedOrders(findAllReq(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getCashierId()).isEqualTo(2L);
                })
                .replaceWithVoid());
    }

    // ==================== Soft Delete (Trash) ====================

    @Test
    void testTrashOrder(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(10L, 1L))
                .chain(o -> commandRepo.trashed(o.getOrderId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashAlreadyTrashedReturnsSame(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(11L, 1L))
                .chain(o -> commandRepo.trashed(o.getOrderId())
                        .chain(() -> commandRepo.trashed(o.getOrderId())))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreOrder(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(12L, 1L))
                .chain(o -> commandRepo.trashed(o.getOrderId())
                        .chain(() -> commandRepo.restore(o.getOrderId())))
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
                .chain(() -> persistOrder(13L, 1L))
                .chain(o -> commandRepo.trashed(o.getOrderId())
                        .chain(() -> commandRepo.deletePermanent(o.getOrderId()))
                        .chain(perm -> queryRepo.findOrderById(o.getOrderId())))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentActiveReturnsNull(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(14L, 1L))
                .chain(o -> commandRepo.deletePermanent(o.getOrderId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid());
    }

    // ==================== Bulk Operations ====================

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(1L, 1L).chain(o -> commandRepo.trashed(o.getOrderId()).replaceWithVoid()))
                .chain(() -> persistOrder(2L, 1L).chain(o -> commandRepo.trashed(o.getOrderId()).replaceWithVoid()))
                .chain(() -> commandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findTrashedOrders(findAllReq(1, 10, "")))
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(1L, 1L).chain(o -> commandRepo.trashed(o.getOrderId()).replaceWithVoid()))
                .chain(() -> persistOrder(2L, 1L).chain(o -> commandRepo.trashed(o.getOrderId()).replaceWithVoid()))
                .chain(() -> persistOrder(3L, 1L)) 
                .chain(() -> commandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> queryRepo.findActiveOrders(findAllReq(1, 10, "")))
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyDatabaseReturnsZeroRecords(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> queryRepo.findOrders(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findActiveOrders(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> queryRepo.findTrashedOrders(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid());
    }

    @Test
    void testSearchNoMatchReturnsZero(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistOrder(1L, 1L))
                .chain(() -> queryRepo.findOrders(findAllReq(1, 10, "NOMATCH")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid());
    }
}