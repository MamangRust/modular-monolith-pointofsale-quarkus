package com.sanedge.cashier.service.statsbyid;

import java.util.List;

import com.sanedge.cashier.domain.requests.MonthTotalSalesCashier;
import com.sanedge.cashier.domain.requests.YearTotalSalesCashier;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.cashier.domain.response.CashierResponseMonthTotalSales;
import com.sanedge.cashier.domain.response.CashierResponseYearTotalSales;

import io.smallrye.mutiny.Uni;

public interface CashierTotalSalesByIdService {
    Uni<ApiResponse<List<CashierResponseMonthTotalSales>>> findMonthlyTotalSalesById(MonthTotalSalesCashier req);
    Uni<ApiResponse<List<CashierResponseYearTotalSales>>> findYearlyTotalSalesById(YearTotalSalesCashier req);
}
