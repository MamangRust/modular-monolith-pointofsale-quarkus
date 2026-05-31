package com.sanedge.order.domain.response;

import java.util.List;

import com.sanedge.order.entity.OrderYearTotalRevenue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderYearlyTotalRevenueResponse {
    private String year;
    private Long totalRevenue;

    public static OrderYearlyTotalRevenueResponse from(OrderYearTotalRevenue response) {
        return OrderYearlyTotalRevenueResponse.builder()
                .year(response.getYear())
                .totalRevenue((long) response.getTotalRevenue())
                .build();
    }

    public static List<OrderYearlyTotalRevenueResponse> fromList(List<OrderYearTotalRevenue> responses) {
        if (responses == null)
            return List.of();
        return responses.stream().map(OrderYearlyTotalRevenueResponse::from).toList();
    }
}
