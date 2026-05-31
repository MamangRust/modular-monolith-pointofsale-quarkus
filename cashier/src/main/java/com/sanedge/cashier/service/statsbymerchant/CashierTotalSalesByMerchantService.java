package com.sanedge.cashier.service.statsbymerchant;

import java.util.List;

import com.sanedge.cashier.domain.requests.MonthTotalSalesMerchant;
import com.sanedge.cashier.domain.requests.YearTotalSalesMerchant;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.cashier.domain.response.CashierResponseMonthTotalSales;
import com.sanedge.cashier.domain.response.CashierResponseYearTotalSales;

import io.smallrye.mutiny.Uni;

public interface CashierTotalSalesByMerchantService {
    Uni<ApiResponse<List<CashierResponseMonthTotalSales>>> findMonthlyTotalSalesByMerchant(MonthTotalSalesMerchant req);
    Uni<ApiResponse<List<CashierResponseYearTotalSales>>> findYearlyTotalSalesByMerchant(YearTotalSalesMerchant req);
}
