package com.sanedge.cashier.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashierYearSales {
    private String year;
    private Integer cashierId;
    private String cashierName;
    private Integer orderCount;
    private Long totalSales;
}
