package com.sanedge.category.service.statsbyid;

import java.util.List;

import com.sanedge.category.domain.requests.MonthTotalPriceCategory;
import com.sanedge.category.domain.requests.YearTotalPriceCategory;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryTotalPriceByIdService {
    Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPriceById(MonthTotalPriceCategory req);
    Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPriceById(YearTotalPriceCategory req);
}
