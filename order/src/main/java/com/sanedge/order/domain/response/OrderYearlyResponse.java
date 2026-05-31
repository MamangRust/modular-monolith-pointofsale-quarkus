package com.sanedge.order.domain.response;

import java.util.List;

import com.sanedge.order.entity.OrderYear;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderYearlyResponse {
    private String year;
    private Integer orderCount;
    private Long totalRevenue;
    private Integer totalItemsSold;
    private Integer activeCashiers;
    private Integer uniqueProductsSold;

    public static OrderYearlyResponse from(OrderYear response) {
        return OrderYearlyResponse.builder()
                .year(response.getYear())
                .orderCount(response.getOrderCount())
                .totalRevenue((long) response.getTotalRevenue())
                .totalItemsSold(response.getTotalItemsSold())
                .activeCashiers(response.getActiveCashiers())
                .uniqueProductsSold(response.getUniqueProductsSold())
                .build();
    }

    public static List<OrderYearlyResponse> fromList(List<OrderYear> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(OrderYearlyResponse::from).toList();
    }
}
