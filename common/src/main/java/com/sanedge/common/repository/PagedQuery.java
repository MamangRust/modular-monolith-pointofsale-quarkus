package com.sanedge.common.repository;

import com.sanedge.common.domain.response.PagedResult;

import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.smallrye.mutiny.Uni;

/**
 * Runs a paginated Panache query's {@code list()} and {@code count()} in
 * sequence.
 * <p>
 * A Hibernate Reactive session is not thread-safe: subscribing to both queries
 * concurrently (as {@code Uni.combine().all()} does) trips Hibernate's reactive
 * action queue with an {@code IndexOutOfBoundsException} in
 * {@code ReactiveActionQueue.executeActions}. Chaining guarantees the count only
 * runs after the page has been read.
 */
public final class PagedQuery {

    private PagedQuery() {
    }

    public static <T> Uni<PagedResult<T>> fetch(PanacheQuery<T> query) {
        return query.list()
                .chain(list -> query.count()
                        .map(count -> new PagedResult<>(list, count.intValue())));
    }
}
