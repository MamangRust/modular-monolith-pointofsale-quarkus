package com.sanedge.role.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.hibernate.reactive.mutiny.Mutiny;

import com.sanedge.common.test.PostgreSqlResource;
import com.sanedge.role.domain.requests.FindAllRoles;
import com.sanedge.role.entity.Role;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class RoleRepositoryTest {

    @Inject
    RoleRepository roleRepository;

    private Uni<Role> createAndPersistRole(String roleName) {
        Role role = new Role();
        role.setRoleName(roleName);
        return roleRepository.persist(role).replaceWith(role);
    }

    // A Hibernate Reactive session is not thread-safe, so entities must be
    // persisted sequentially instead of via Uni.combine().all()/Uni.join().all().
    private Uni<List<Role>> createAndPersistRoles(String... names) {
        Uni<List<Role>> chain = Uni.createFrom().item(new ArrayList<Role>());
        for (String name : names) {
            chain = chain.chain(roles -> createAndPersistRole(name)
                    .invoke(roles::add)
                    .replaceWith(roles));
        }
        return chain;
    }

    private Uni<Void> trashSequentially(List<Role> roles, int... indices) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (int index : indices) {
            Role role = roles.get(index);
            chain = chain.chain(() -> roleRepository.trash(role.id).replaceWithVoid());
        }
        return chain;
    }

    private Uni<Void> clearSession() {
        return roleRepository.getSession().invoke(Mutiny.Session::clear).replaceWithVoid();
    }

    private Uni<List<Role>> reload(List<Role> roles) {
        Uni<List<Role>> chain = Uni.createFrom().item(new ArrayList<Role>());
        for (Role role : roles) {
            chain = chain.chain(list -> roleRepository.findById(role.id)
                    .invoke(list::add)
                    .replaceWith(list));
        }
        return chain;
    }

    private FindAllRoles pageRequest(int page, int pageSize, String search) {
        FindAllRoles req = new FindAllRoles();
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(search);
        return req;
    }

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRole("Admin")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getRoleName()).isEqualTo("Admin");
                })
                .chain(saved -> roleRepository.findById(saved.id))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getRoleName()).isEqualTo("Admin");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByRoleName(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRole("Editor")
                .chain(ignored -> roleRepository.findByRoleName("Editor"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getRoleName()).isEqualTo("Editor");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByRoleNameReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> roleRepository.findByRoleName("NonExistentRole")
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> roleRepository.findById(99999L)
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindUserRolesReturnsEmptyWhenNoMapping(UniAsserter asserter) {
        asserter.execute(() -> roleRepository.findUserRoles(99999L)
                .invoke(result -> assertThat(result).isEmpty())
                .replaceWithVoid());
    }

    @Test
    void testTrashRole(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRole("TrashMe")
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> roleRepository.trash(saved.id))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashRoleReturnsNullIfAlreadyTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRole("TrashMe2")
                .chain(saved -> roleRepository.trash(saved.id)
                        .chain(ignored -> roleRepository.trash(saved.id)))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashRoleReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> roleRepository.trash(99999L)
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreRole(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRole("RestoreMe")
                .chain(saved -> roleRepository.trash(saved.id)
                        .chain(ignored -> roleRepository.restore(saved.id)))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreRoleReturnsNullIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRole("RestoreMe2")
                .chain(saved -> roleRepository.restore(saved.id))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRole("DeleteMe")
                .chain(saved -> roleRepository.trash(saved.id)
                        .chain(ignored -> roleRepository.deletePermanent(saved.id))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return roleRepository.findById(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRole("DeleteMe2")
                .chain(saved -> roleRepository.deletePermanent(saved.id)
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return roleRepository.findById(saved.id);
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRoles("BulkRole1", "BulkRole2")
                .chain(roles -> trashSequentially(roles, 0, 1).replaceWith(roles))
                .chain(roles -> roleRepository.restoreAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return clearSession().replaceWith(roles);
                        }))
                .chain(this::reload)
                .invoke(roles -> {
                    assertThat(roles.get(0).getDeletedAt()).isNull();
                    assertThat(roles.get(1).getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRoles("BulkDelRole1", "BulkDelRole2", "BulkDelRole3")
                .chain(roles -> trashSequentially(roles, 0, 1).replaceWith(roles))
                .chain(roles -> roleRepository.deleteAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return clearSession().replaceWith(roles);
                        }))
                .chain(this::reload)
                .invoke(roles -> {
                    assertThat(roles.get(0)).isNull();
                    assertThat(roles.get(1)).isNull();
                    assertThat(roles.get(2)).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindActiveRoles(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRoles("ActiveRole1", "TrashedRole1")
                .chain(roles -> trashSequentially(roles, 1).replaceWith(roles))
                .chain(roles -> roleRepository.findActiveRoles(pageRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getRoleName()).isEqualTo("ActiveRole1");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedRoles(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRoles("TrashedRole2", "ActiveRole2")
                .chain(roles -> trashSequentially(roles, 0).replaceWith(roles))
                .chain(roles -> roleRepository.findTrashedRoles(pageRequest(1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getRoleName()).isEqualTo("TrashedRole2");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindRolesWithSearchKeyword(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRoles("SuperAdmin", "SuperEditor", "Viewer")
                .chain(roles -> roleRepository.findRoles(pageRequest(1, 10, "Super")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindRolesWithPagination(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRoles("PageRole1", "PageRole2", "PageRole3",
                "PageRole4", "PageRole5")
                .chain(roles -> roleRepository.findRoles(pageRequest(1, 2, null)))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(page1 -> roleRepository.findRoles(pageRequest(2, 2, null))
                        .invoke(page2 -> assertThat(page2.getData()).hasSize(2)))
                .replaceWithVoid());
    }

    @Test
    void testFindRolesWithNullSearchReturnsAll(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRoles("RoleC", "RoleD")
                .chain(roles -> roleRepository.findRoles(pageRequest(1, 10, null)))
                .invoke(result -> assertThat(result.getData()).hasSize(2))
                .replaceWithVoid());
    }

    @Test
    void testFindRolesSearchCaseInsensitive(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistRole("ManagerRole")
                .chain(ignored -> roleRepository.findRoles(pageRequest(1, 10, "manager")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getRoleName()).isEqualTo("ManagerRole");
                })
                .replaceWithVoid());
    }
}
