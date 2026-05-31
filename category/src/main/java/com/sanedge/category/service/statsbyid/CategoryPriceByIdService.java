package com.sanedge.category.service.statsbyid;

import java.util.List;

import com.sanedge.category.domain.requests.MonthPriceId;
import com.sanedge.category.domain.requests.YearPriceId;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryPriceByIdService {
    Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceById(MonthPriceId req);
    Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceById(YearPriceId req);
}
