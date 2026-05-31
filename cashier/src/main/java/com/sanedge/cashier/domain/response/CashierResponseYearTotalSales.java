package com.sanedge.cashier.domain.response;

import com.sanedge.cashier.entity.CashierYearTotalSales;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(name = "CashierResponseYearTotalSales", description = "Response total penjualan tahunan semua kasir")
public class CashierResponseYearTotalSales {
    private String year;
    private Long totalSales;

    public static CashierResponseYearTotalSales from(CashierYearTotalSales entity) {
        return CashierResponseYearTotalSales.builder()
                .year(entity.getYear())
                .totalSales(entity.getTotalSales())
                .build();
    }
}
