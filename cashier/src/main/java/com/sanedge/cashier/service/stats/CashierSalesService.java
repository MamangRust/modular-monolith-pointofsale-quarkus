package com.sanedge.cashier.service.stats;

import java.util.List;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.cashier.domain.response.CashierResponseMonthSales;
import com.sanedge.cashier.domain.response.CashierResponseYearSales;

import io.smallrye.mutiny.Uni;

public interface CashierSalesService {
    Uni<ApiResponse<List<CashierResponseMonthSales>>> findMonthlySales(Integer year);
    Uni<ApiResponse<List<CashierResponseYearSales>>> findYearlySales(Integer year);
}
