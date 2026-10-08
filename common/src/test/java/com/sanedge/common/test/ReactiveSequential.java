package com.sanedge.common.test;

import io.smallrye.mutiny.Uni;

/**
 * Runs several cold {@link Uni} pipelines one after another.
 * <p>
 * A Hibernate Reactive session is not thread-safe: subscribing to two
 * persistence operations concurrently — which is exactly what
 * {@code Uni.combine().all()} and {@code Uni.join().all()} do — trips
 * Hibernate's reactive action queue ({@code IndexOutOfBoundsException} in
 * {@code ReactiveActionQueue.executeActions}). Tests that need to persist or
 * mutate several entities must therefore chain the operations sequentially.
 */
public final class ReactiveSequential {

    private ReactiveSequential() {
    }

    /**
     * Subscribes to each given {@link Uni} in order, waiting for the previous one
     * to complete before starting the next.
     */
    public static Uni<Void> sequential(Uni<?>... unis) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (Uni<?> uni : unis) {
            chain = chain.chain(() -> uni.replaceWithVoid());
        }
        return chain;
    }
}
