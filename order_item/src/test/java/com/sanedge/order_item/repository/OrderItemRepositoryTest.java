package com.sanedge.order_item.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.sanedge.order_item.domain.requests.FindAllOrderItems;
import com.sanedge.order_item.entity.OrderItem;

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
class OrderItemRepositoryTest {

    @Inject
    OrderItemRepository repository;

    private Uni<OrderItem> persistItem(Long orderId, Long productId, int quantity, Long price) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setPrice(price.intValue());
        item.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        item.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return repository.persist(item).map(i -> i);
    }

    private Uni<OrderItem> persistItem(Long orderId, Long productId) {
        return persistItem(orderId, productId, 1, 5000L);
    }

    private Uni<Void> clean() {
        return repository.deleteAll().replaceWithVoid();
    }

    private FindAllOrderItems findAllReq(int page, int size, String search) {
        FindAllOrderItems req = new FindAllOrderItems();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }


    @Test
    void testCreateAndFindByOrder(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(100L, 10L))
                .chain(() -> persistItem(100L, 20L))
                .chain(() -> repository.findOrderItemByOrder(100L))
                .invoke(items -> {
                    assertThat(items).hasSize(2);
                    assertThat(items).allSatisfy(item -> assertThat(item.getOrderId()).isEqualTo(100L));
                })
                .replaceWithVoid());
    }

    @Test
    void testFindOrderItemByOrderIncludingDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(100L, 10L))
                .chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid())
                .chain(() -> persistItem(100L, 20L))
                .chain(() -> repository.findOrderItemByOrder(100L))
                .invoke(activeItems -> assertThat(activeItems).hasSize(1)) // trashed excluded
                .chain(() -> repository.findOrderItemByOrderIncludingDeleted(100L))
                .invoke(allItems -> assertThat(allItems).hasSize(2)) // includes trashed
                .replaceWithVoid());
    }

    // ==================== Query - Search & Pagination ====================

    @Test
    void testFindOrderItemsWithSearch(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(1L, 10L))
                .chain(() -> persistItem(2L, 20L))
                .chain(() -> persistItem(3L, 30L))
                .chain(() -> repository.findOrderItems(findAllReq(1, 10, "2")))
                .invoke(result -> {
                    // search matches id or orderId or productId containing "2"
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getOrderId()).isEqualTo(2L);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindOrderItemsPagination(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(1L, 10L))
                .chain(() -> persistItem(2L, 20L))
                .chain(() -> persistItem(3L, 30L))
                .chain(() -> persistItem(4L, 40L))
                .chain(() -> persistItem(5L, 50L))
                .chain(() -> repository.findOrderItems(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> repository.findOrderItems(findAllReq(3, 2, "")))
                .invoke(page3 -> assertThat(page3.getData()).hasSize(1))
                .replaceWithVoid());
    }

    // ==================== Active / Trashed filters ====================

    @Test
    void testFindActiveOrderItemsExcludesTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(1L, 10L))
                .chain(() -> persistItem(1L, 20L).chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid()))
                .chain(() -> repository.findActiveOrderItems(findAllReq(1, 10, "")))
                .invoke(result -> assertThat(result.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedOrderItemsOnlyTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(1L, 10L))
                .chain(() -> persistItem(1L, 20L).chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid()))
                .chain(() -> repository.findTrashedOrderItems(findAllReq(1, 10, "")))
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getProductId()).isEqualTo(20L);
                })
                .replaceWithVoid());
    }

    // ==================== Soft Delete (Trash) ====================

    @Test
    void testTrashOrderItem(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(10L, 100L))
                .chain(item -> repository.trashed(item.getOrderItemId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashAlreadyTrashedReturnsSame(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(11L, 110L))
                .chain(item -> repository.trashed(item.getOrderItemId())
                        .chain(() -> repository.trashed(item.getOrderItemId())))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreOrderItem(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(12L, 120L))
                .chain(item -> repository.trashed(item.getOrderItemId())
                        .chain(() -> repository.restore(item.getOrderItemId())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreNonExistentReturnsNull(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> repository.restore(99999L))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid());
    }

    // ==================== Permanent Delete ====================

    @Test
    void testDeletePermanentAfterTrash(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(13L, 130L))
                .chain(item -> repository.trashed(item.getOrderItemId())
                        .chain(() -> repository.deletePermanent(item.getOrderItemId())))
                .invoke(deleted -> assertThat(deleted).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentActiveReturnsNull(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(14L, 140L))
                .chain(item -> repository.deletePermanent(item.getOrderItemId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid());
    }

    // ==================== Bulk Operations ====================

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(1L, 1L).chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid()))
                .chain(() -> persistItem(2L, 2L).chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid()))
                .chain(() -> repository.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> repository.findTrashedOrderItems(findAllReq(1, 10, "")))
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(1L, 1L).chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid()))
                .chain(() -> persistItem(2L, 2L).chain(item -> repository.trashed(item.getOrderItemId()).replaceWithVoid()))
                .chain(() -> persistItem(3L, 3L)) // stays active
                .chain(() -> repository.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> repository.findActiveOrderItems(findAllReq(1, 10, "")))
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyDatabaseReturnsZeroRecords(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> repository.findOrderItems(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> repository.findActiveOrderItems(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .chain(() -> repository.findTrashedOrderItems(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid());
    }

    @Test
    void testSearchNoMatchReturnsZero(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistItem(1L, 1L))
                .chain(() -> repository.findOrderItems(findAllReq(1, 10, "NOMATCH")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .replaceWithVoid());
    }
}