package com.sanedge.category.service.stats;

import java.util.List;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryPriceService {
    Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPrice(Integer year);
    Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPrice(Integer year);
}
