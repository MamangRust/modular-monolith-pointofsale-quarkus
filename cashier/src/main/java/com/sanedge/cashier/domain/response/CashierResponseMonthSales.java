package com.sanedge.cashier.domain.response;

import com.sanedge.cashier.entity.CashierMonthSales;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(name = "CashierResponseMonthSales", description = "Response penjualan bulanan per kasir")
public class CashierResponseMonthSales {
    private String month;
    private Integer cashierId;
    private String cashierName;
    private Integer orderCount;
    private Long totalSales;

    public static CashierResponseMonthSales from(CashierMonthSales entity) {
        return CashierResponseMonthSales.builder()
                .month(entity.getMonth())
                .cashierId(entity.getCashierId())
                .cashierName(entity.getCashierName())
                .orderCount(entity.getOrderCount())
                .totalSales(entity.getTotalSales())
                .build();
    }
}
