package com.sanedge.category.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.sanedge.category.domain.requests.FindAllCategory;
import com.sanedge.category.entity.Category;

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
class CategoryRepositoryTest {

    @Inject
    CategoryQueryRepository categoryQueryRepo;

    @Inject
    CategoryCommandRepository categoryCommandRepo;

    private Uni<Category> persistCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        category.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return categoryQueryRepo.persist(category).map(c -> c);
    }

    private Uni<Void> clean() {
        return categoryQueryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllCategory findAllReq(int page, int size, String search) {
        FindAllCategory req = new FindAllCategory();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search == null ? "" : search);
        return req;
    }

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("Test Category"))
                .chain(c -> categoryQueryRepo.findCategoryById(c.getCategoryId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Test Category");
                    assertThat(found.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByName(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("Unique Name"))
                .chain(() -> categoryQueryRepo.findByName("Unique Name"))
                .invoke(found -> assertThat(found).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByNameReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> categoryQueryRepo.findByName("NonExistent"))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindCategoryByIdReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> categoryQueryRepo.findCategoryById(9999L))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindCategoriesWithSearch(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("Alpha"))
                .chain(() -> persistCategory("Beta"))
                .chain(() -> persistCategory("Gamma"))
                .chain(() -> {
                    FindAllCategory req = findAllReq(1, 10, "Al");
                    return categoryQueryRepo.findCategories(req);
                })
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Alpha");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindCategoriesPagination(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("A"))
                .chain(() -> persistCategory("B"))
                .chain(() -> persistCategory("C"))
                .chain(() -> persistCategory("D"))
                .chain(() -> persistCategory("E"))
                .chain(() -> {
                    FindAllCategory req = findAllReq(2, 2, "");
                    return categoryQueryRepo.findCategories(req);
                })
                .invoke(page2 -> {
                    assertThat(page2.getData()).hasSize(2);
                    assertThat(page2.getTotalRecords()).isEqualTo(5);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindActiveCategoriesExcludesTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("Active1"))
                .chain(() -> persistCategory("ToTrash").chain(c -> categoryCommandRepo.trashed(c.getCategoryId())))
                .chain(() -> {
                    FindAllCategory req = findAllReq(1, 10, "");
                    return categoryQueryRepo.findActiveCategories(req);
                })
                .invoke(result -> assertThat(result.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedCategoriesOnlyTrashed(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("Stay"))
                .chain(() -> persistCategory("TrashMe").chain(c -> categoryCommandRepo.trashed(c.getCategoryId())))
                .chain(() -> {
                    FindAllCategory req = findAllReq(1, 10, "");
                    return categoryQueryRepo.findTrashedCategories(req);
                })
                .invoke(result -> {
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("TrashMe");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindNameAndId(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("X"))
                .chain(() -> persistCategory("Y"))
                .chain(() -> categoryQueryRepo.findNameAndId())
                .invoke(list -> assertThat(list).hasSize(2))
                .replaceWithVoid());
    }

    // Soft delete & restore
    @Test
    void testTrashCategory(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("ToTrash"))
                .chain(c -> categoryCommandRepo.trashed(c.getCategoryId()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashAlreadyTrashedReturnsSame(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("Already"))
                .chain(c -> categoryCommandRepo.trashed(c.getCategoryId())
                        .chain(() -> categoryCommandRepo.trashed(c.getCategoryId())))
                .invoke(trashed -> {
                    // Should return the already trashed entity, not null
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreCategory(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("RestoreMe"))
                .chain(c -> categoryCommandRepo.trashed(c.getCategoryId())
                        .chain(() -> categoryCommandRepo.restore(c.getCategoryId())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreNonExistentReturnsNull(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> categoryCommandRepo.restore(99999L))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("DelPerm"))
                .chain(c -> categoryCommandRepo.trashed(c.getCategoryId())
                        .chain(() -> categoryCommandRepo.deletePermanent(c.getCategoryId()))
                        .chain(perm -> categoryQueryRepo.findCategoryById(c.getCategoryId())))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentNotTrashedReturnsNull(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("Active"))
                .chain(c -> categoryCommandRepo.deletePermanent(c.getCategoryId()))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("A").chain(c -> categoryCommandRepo.trashed(c.getCategoryId()).replaceWithVoid()))
                .chain(() -> persistCategory("B").chain(c -> categoryCommandRepo.trashed(c.getCategoryId()).replaceWithVoid()))
                .chain(() -> categoryCommandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> {
                    FindAllCategory req = findAllReq(1, 10, "");
                    return categoryQueryRepo.findTrashedCategories(req);
                })
                .invoke(trashed -> assertThat(trashed.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("X").chain(c -> categoryCommandRepo.trashed(c.getCategoryId()).replaceWithVoid()))
                .chain(() -> persistCategory("Y"))
                .chain(() -> categoryCommandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> {
                    FindAllCategory req = findAllReq(1, 10, "");
                    return categoryQueryRepo.findActiveCategories(req);
                })
                .invoke(active -> assertThat(active.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid());
    }

    // Edge Cases
    @Test
    void testEmptyDatabaseReturnsZeroRecords(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> {
                    FindAllCategory req = findAllReq(1, 10, "");
                    return categoryQueryRepo.findCategories(req);
                })
                .invoke(r -> assertThat(r.getTotalRecords()).isEqualTo(0))
                .chain(() -> {
                    FindAllCategory req = findAllReq(1, 10, "");
                    return categoryQueryRepo.findActiveCategories(req);
                })
                .invoke(r -> assertThat(r.getTotalRecords()).isEqualTo(0))
                .chain(() -> {
                    FindAllCategory req = findAllReq(1, 10, "");
                    return categoryQueryRepo.findTrashedCategories(req);
                })
                .invoke(r -> assertThat(r.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid());
    }

    @Test
    void testSearchNoMatchReturnsZero(UniAsserter asserter) {
        asserter.execute(() -> clean()
                .chain(() -> persistCategory("Something"))
                .chain(() -> {
                    FindAllCategory req = findAllReq(1, 10, "NOMATCH");
                    return categoryQueryRepo.findCategories(req);
                })
                .invoke(r -> assertThat(r.getTotalRecords()).isEqualTo(0))
                .replaceWithVoid());
    }
}