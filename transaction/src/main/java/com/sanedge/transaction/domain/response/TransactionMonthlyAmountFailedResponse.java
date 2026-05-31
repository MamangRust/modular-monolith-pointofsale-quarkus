package com.sanedge.transaction.domain.response;

import com.sanedge.transaction.entity.TransactionMonthlyAmountFailed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMonthlyAmountFailedResponse {
    private String year;
    private String month;
    private Integer totalFailed;
    private Long totalAmount;

    public static TransactionMonthlyAmountFailedResponse from(TransactionMonthlyAmountFailed entity) {
        return TransactionMonthlyAmountFailedResponse.builder()
                .year(entity.getYear())
                .month(entity.getMonth())
                .totalFailed(entity.getTotalFailed())
                .totalAmount(entity.getTotalAmount())
                .build();
    }
}
