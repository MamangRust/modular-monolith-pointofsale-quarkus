package com.sanedge.cashier.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CashierMonthTotalSales {
    private String year;
    private String month;
    private Long totalSales;
}
