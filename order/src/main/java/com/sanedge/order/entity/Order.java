package com.sanedge.order.entity;

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
@Table(name = "orders")
public class Order extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    public Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    public Long getOrderId() {
        return this.id;
    }

    public void setOrderId(Long orderId) {
        this.id = orderId;
    }
}
