package com.sanedge.order.service.statsbymerchant;

import java.util.List;

import com.sanedge.order.domain.requests.MonthTotalRevenueMerchantRequest;
import com.sanedge.order.domain.requests.YearTotalRevenueMerchantRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;

import io.smallrye.mutiny.Uni;

public interface OrderTotalRevenueByMerchantService {
    Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyStatsByMerchant(MonthTotalRevenueMerchantRequest req);
    Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyStatsByMerchant(YearTotalRevenueMerchantRequest req);
}
