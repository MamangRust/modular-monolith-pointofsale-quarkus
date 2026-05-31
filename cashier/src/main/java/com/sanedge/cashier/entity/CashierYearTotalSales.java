package com.sanedge.cashier.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashierYearTotalSales {
    private String year;
    private Long totalSales;
}
