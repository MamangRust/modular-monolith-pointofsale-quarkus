package com.sanedge.cashier.service.stats;

import java.util.List;

import com.sanedge.cashier.domain.requests.MonthTotalSales;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.cashier.domain.response.CashierResponseMonthTotalSales;
import com.sanedge.cashier.domain.response.CashierResponseYearTotalSales;

import io.smallrye.mutiny.Uni;

public interface CashierTotalSalesService {
    Uni<ApiResponse<List<CashierResponseMonthTotalSales>>> findMonthlyTotalSales(MonthTotalSales req);
    Uni<ApiResponse<List<CashierResponseYearTotalSales>>> findYearlyTotalSales(Integer year);
}
