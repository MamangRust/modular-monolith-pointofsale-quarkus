package com.sanedge.transaction.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMonthlyAmountFailed {
    private String year;
    private String month;
    private Integer totalFailed;
    private Long totalAmount;
}
