package com.sanedge.product.entity;

import com.sanedge.common.entity.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(name = "categories")
public class Category extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    public Long id;

    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @Column(name = "slug_category", unique = true)
    private String slugCategory;

    public Long getCategoryId() {
        return this.id;
    }

    public void setCategoryId(Long categoryId) {
        this.id = categoryId;
    }
}
