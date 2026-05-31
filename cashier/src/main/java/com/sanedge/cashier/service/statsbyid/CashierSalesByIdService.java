package com.sanedge.cashier.service.statsbyid;

import java.util.List;

import com.sanedge.cashier.domain.requests.MonthCashierIdRequest;
import com.sanedge.cashier.domain.requests.YearCashierIdRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.cashier.domain.response.CashierResponseMonthSales;
import com.sanedge.cashier.domain.response.CashierResponseYearSales;

import io.smallrye.mutiny.Uni;

public interface CashierSalesByIdService {
    Uni<ApiResponse<List<CashierResponseMonthSales>>> findMonthlyCashierById(MonthCashierIdRequest req);
    Uni<ApiResponse<List<CashierResponseYearSales>>> findYearlyCashierById(YearCashierIdRequest req);
}
