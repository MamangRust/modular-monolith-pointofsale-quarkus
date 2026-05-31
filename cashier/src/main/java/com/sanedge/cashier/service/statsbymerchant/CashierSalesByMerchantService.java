package com.sanedge.cashier.service.statsbymerchant;

import java.util.List;

import com.sanedge.cashier.domain.requests.MonthCashierMerchantRequest;
import com.sanedge.cashier.domain.requests.YearCashierMerchantRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.cashier.domain.response.CashierResponseMonthSales;
import com.sanedge.cashier.domain.response.CashierResponseYearSales;

import io.smallrye.mutiny.Uni;

public interface CashierSalesByMerchantService {
    Uni<ApiResponse<List<CashierResponseMonthSales>>> findMonthlyCashierByMerchant(MonthCashierMerchantRequest req);
    Uni<ApiResponse<List<CashierResponseYearSales>>> findYearlyCashierByMerchant(YearCashierMerchantRequest req);
}
