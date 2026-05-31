package com.sanedge.cashier.domain.response;

import com.sanedge.cashier.entity.CashierYearSales;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(name = "CashierResponseYearSales", description = "Response penjualan tahunan per kasir")
public class CashierResponseYearSales {
    private String year;
    private Integer cashierId;
    private String cashierName;
    private Integer orderCount;
    private Long totalSales;

    public static CashierResponseYearSales from(CashierYearSales entity) {
        return CashierResponseYearSales.builder()
                .year(entity.getYear())
                .cashierId(entity.getCashierId())
                .cashierName(entity.getCashierName())
                .orderCount(entity.getOrderCount())
                .totalSales(entity.getTotalSales())
                .build();
    }
}
