package com.sanedge.order.service.stats;

import java.util.List;

import com.sanedge.order.domain.requests.MonthTotalRevenue;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;

import io.smallrye.mutiny.Uni;

public interface OrderTotalRevenueService {
    Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyStats(MonthTotalRevenue req);
    Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyStats(Integer year);
}
