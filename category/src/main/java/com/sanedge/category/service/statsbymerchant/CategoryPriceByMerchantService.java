package com.sanedge.category.service.statsbymerchant;

import java.util.List;

import com.sanedge.category.domain.requests.MonthPriceMerchant;
import com.sanedge.category.domain.requests.YearPriceMerchant;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;

import io.smallrye.mutiny.Uni;

public interface CategoryPriceByMerchantService {
    Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceByMerchant(MonthPriceMerchant req);
    Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceByMerchant(YearPriceMerchant req);
}
