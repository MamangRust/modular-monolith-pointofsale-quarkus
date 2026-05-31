package com.sanedge.category.service.statsbymerchant;

import java.util.List;

import com.sanedge.category.domain.requests.MonthTotalPriceMerchant;
import com.sanedge.category.domain.requests.YearTotalPriceMerchant;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryTotalPriceByMerchantService {
    Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPriceByMerchant(MonthTotalPriceMerchant req);
    Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPriceByMerchant(YearTotalPriceMerchant req);
}
