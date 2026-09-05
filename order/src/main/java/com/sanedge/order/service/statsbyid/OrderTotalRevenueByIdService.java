package com.sanedge.order.service.statsbyid;

import java.util.List;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.order.domain.requests.MonthTotalRevenueByIdRequest;
import com.sanedge.order.domain.requests.YearTotalRevenueByIdRequest;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;

import io.smallrye.mutiny.Uni;

public interface OrderTotalRevenueByIdService {
    Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyStatsById(MonthTotalRevenueByIdRequest req);

    Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyStatsById(YearTotalRevenueByIdRequest req);
}
