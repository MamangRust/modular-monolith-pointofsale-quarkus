package com.sanedge.category.service.stats;

import java.util.List;

import com.sanedge.category.domain.requests.MonthTotalPrice;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryTotalPriceService {
    Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPrice(MonthTotalPrice req);
    Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPrice(Integer year);
}
