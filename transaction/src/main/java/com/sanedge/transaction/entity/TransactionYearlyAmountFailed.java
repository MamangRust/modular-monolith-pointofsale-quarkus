package com.sanedge.transaction.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionYearlyAmountFailed {
    private String year;
    private Integer totalFailed;
    private Long totalAmount;
}
