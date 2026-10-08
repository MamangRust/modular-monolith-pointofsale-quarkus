package com.sanedge.common.entity;

import java.sql.Timestamp;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

/**
 * Single shared Panache base for every entity: the audit timestamps
 * ({@code created_at}, {@code updated_at}, {@code deleted_at}) and their
 * lifecycle callbacks.
 *
 * <p>It deliberately declares <b>no</b> identifier so the same superclass fits
 * every primary-key shape. Entities with a surrogate key declare
 * {@code @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id}
 * (optionally with {@code @Column} to rename the key column), while entities
 * with a natural/custom key declare their own {@code @Id}
 * (e.g. {@code Merchant#merchantId}, {@code MerchantDocument#documentId}).</p>
 */
@Getter
@Setter
@MappedSuperclass
public class BaseModel extends PanacheEntityBase {

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @PrePersist
    protected void onCreate() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
}
