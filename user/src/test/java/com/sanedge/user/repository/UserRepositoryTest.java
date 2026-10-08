package com.sanedge.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.hibernate.reactive.mutiny.Mutiny;

import com.sanedge.common.test.PostgreSqlResource;
import com.sanedge.user.domain.requests.FindAllUsers;
import com.sanedge.user.entity.User;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestReactiveTransaction;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(PostgreSqlResource.class)
@TestReactiveTransaction
class UserRepositoryTest {

    @Inject
    UserRepository userRepository;

    private Uni<User> createAndPersistUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setFirstname("First_" + username);
        user.setLastname("Last_" + username);
        user.setEmail(email);
        user.setPassword("password123");
        return userRepository.persist(user).replaceWith(user);
    }

    // A Hibernate Reactive session is not thread-safe, so entities must be
    // persisted sequentially instead of via Uni.combine().all()/Uni.join().all().
    private Uni<List<User>> createAndPersistUsers(String... usernames) {
        Uni<List<User>> chain = Uni.createFrom().item(new ArrayList<User>());
        for (String username : usernames) {
            chain = chain.chain(users -> createAndPersistUser(username, username + "@example.com")
                    .invoke(users::add)
                    .replaceWith(users));
        }
        return chain;
    }

    private Uni<Void> trashSequentially(List<User> users, int... indices) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (int index : indices) {
            User user = users.get(index);
            chain = chain.chain(() -> userRepository.trash(user.id).replaceWithVoid());
        }
        return chain;
    }

    private Uni<Void> clearSession() {
        return userRepository.getSession().invoke(Mutiny.Session::clear).replaceWithVoid();
    }

    private Uni<List<User>> reload(List<User> users) {
        Uni<List<User>> chain = Uni.createFrom().item(new ArrayList<User>());
        for (User user : users) {
            chain = chain.chain(list -> userRepository.findById(Math.toIntExact(user.id))
                    .invoke(list::add)
                    .replaceWith(list));
        }
        return chain;
    }

    @Test
    void testCreateAndFindById(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUser("johndoe", "john.doe@example.com")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getEmail()).isEqualTo("john.doe@example.com");
                })
                .chain(saved -> userRepository.findById(Math.toIntExact(saved.id)))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getUsername()).isEqualTo("johndoe");
                    assertThat(found.getFirstname()).isEqualTo("First_johndoe");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByEmail(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUser("janedoe", "jane.doe@example.com")
                .chain(ignored -> userRepository.findByEmail("jane.doe@example.com"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getUsername()).isEqualTo("janedoe");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindByEmailReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> userRepository.findByEmail("nonexistent@example.com")
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound(UniAsserter asserter) {
        asserter.execute(() -> userRepository.findById(Integer.MAX_VALUE)
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid());
    }

    @Test
    void testFindByUsername(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUser("alice", "alice@example.com")
                .chain(ignored -> userRepository.findByUsername("alice"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getEmail()).isEqualTo("alice@example.com");
                })
                .replaceWithVoid());
    }

    @Test
    void testExistsByUsernameAndEmail(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUser("bob", "bob@example.com")
                .chain(ignored -> userRepository.existsByUsername("bob")
                        .chain(r0 -> userRepository.existsByEmail("bob@example.com")
                                .chain(r1 -> userRepository.existsByUsername("notbob")
                                        .chain(r2 -> userRepository.existsByEmail("notbob@example.com")
                                                .map(r3 -> List.of(r0, r1, r2, r3))))))
                .invoke(results -> {
                    assertThat(results.get(0)).isTrue();
                    assertThat(results.get(1)).isTrue();
                    assertThat(results.get(2)).isFalse();
                    assertThat(results.get(3)).isFalse();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashUser(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUser("trashme", "trash@example.com")
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> userRepository.trash(saved.id))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testTrashUserReturnsNullIfAlreadyTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUser("trashme2", "trash2@example.com")
                .chain(saved -> userRepository.trash(saved.id)
                        .chain(ignored -> userRepository.trash(saved.id)))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid());
    }

    @Test
    void testTrashUserReturnsNullIfNotFound(UniAsserter asserter) {
        asserter.execute(() -> userRepository.trash(99999L)
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreUser(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUser("restoreme", "restore@example.com")
                .chain(saved -> userRepository.trash(saved.id)
                        .chain(ignored -> userRepository.restore(saved.id)))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testRestoreUserReturnsNullIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUser("restoreme2", "restore2@example.com")
                .chain(saved -> userRepository.restore(saved.id))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanent(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUser("deleteme", "delete@example.com")
                .chain(saved -> userRepository.trash(saved.id)
                        .chain(ignored -> userRepository.deletePermanent(saved.id))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return userRepository.findById(Math.toIntExact(saved.id));
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid());
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUser("deleteme2", "delete2@example.com")
                .chain(saved -> userRepository.deletePermanent(saved.id)
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return userRepository.findById(Math.toIntExact(saved.id));
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNotNull())
                .replaceWithVoid());
    }

    @Test
    void testRestoreAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUsers("bulk1", "bulk2")
                .chain(users -> trashSequentially(users, 0, 1).replaceWith(users))
                .chain(users -> userRepository.restoreAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return clearSession().replaceWith(users);
                        }))
                .chain(this::reload)
                .invoke(users -> {
                    assertThat(users.get(0).getDeletedAt()).isNull();
                    assertThat(users.get(1).getDeletedAt()).isNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testDeleteAllDeleted(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUsers("bulkdel1", "bulkdel2", "bulkdel3")
                .chain(users -> trashSequentially(users, 0, 1).replaceWith(users))
                .chain(users -> userRepository.deleteAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return clearSession().replaceWith(users);
                        }))
                .chain(this::reload)
                .invoke(users -> {
                    assertThat(users.get(0)).isNull();
                    assertThat(users.get(1)).isNull();
                    assertThat(users.get(2)).isNotNull();
                })
                .replaceWithVoid());
    }

    @Test
    void testFindActiveUsers(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUsers("active1", "trashed1")
                .chain(users -> trashSequentially(users, 1).replaceWith(users))
                .chain(users -> {
                    FindAllUsers req = new FindAllUsers();
                    req.setPage(1);
                    req.setPageSize(10);
                    return userRepository.findActiveUsers(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getUsername()).isEqualTo("active1");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindTrashedUsers(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUsers("trashed2", "active2")
                .chain(users -> trashSequentially(users, 0).replaceWith(users))
                .chain(users -> {
                    FindAllUsers req = new FindAllUsers();
                    req.setPage(1);
                    req.setPageSize(10);
                    return userRepository.findTrashedUsers(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getUsername()).isEqualTo("trashed2");
                })
                .replaceWithVoid());
    }

    @Test
    void testFindUsersWithSearchKeyword(UniAsserter asserter) {
        // "man" matches the firstname of superman and spiderman, but not robin.
        asserter.execute(() -> createAndPersistUsers("superman", "spiderman", "robin")
                .chain(users -> {
                    FindAllUsers req = new FindAllUsers();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("man");
                    return userRepository.findUsers(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid());
    }

    @Test
    void testFindUsersWithPagination(UniAsserter asserter) {
        asserter.execute(() -> createAndPersistUsers("pageuser1", "pageuser2", "pageuser3",
                "pageuser4", "pageuser5")
                .chain(users -> {
                    FindAllUsers reqPage1 = new FindAllUsers();
                    reqPage1.setPage(1);
                    reqPage1.setPageSize(2);
                    return userRepository.findUsers(reqPage1);
                })
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(page1 -> {
                    FindAllUsers reqPage2 = new FindAllUsers();
                    reqPage2.setPage(2);
                    reqPage2.setPageSize(2);
                    return userRepository.findUsers(reqPage2)
                            .invoke(page2 -> {
                                assertThat(page2.getData()).hasSize(2);
                                assertThat(page2.getData().get(0).getUsername()).isNotIn(
                                        page1.getData().get(0).getUsername(),
                                        page1.getData().get(1).getUsername());
                            });
                })
                .replaceWithVoid());
    }
}
