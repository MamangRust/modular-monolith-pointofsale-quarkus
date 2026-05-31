package com.sanedge.cashier.domain.response;

import com.sanedge.cashier.entity.CashierMonthTotalSales;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(name = "CashierResponseMonthTotalSales", description = "Response total penjualan bulanan semua kasir")
public class CashierResponseMonthTotalSales {
    private String year;
    private String month;
    private Long totalSales;

    public static CashierResponseMonthTotalSales from(CashierMonthTotalSales entity) {
        return CashierResponseMonthTotalSales.builder()
                .year(entity.getYear())
                .month(entity.getMonth())
                .totalSales(entity.getTotalSales())
                .build();
    }
}
