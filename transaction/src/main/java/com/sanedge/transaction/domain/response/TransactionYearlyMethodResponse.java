package com.sanedge.transaction.domain.response;

import com.sanedge.transaction.entity.TransactionYearMethod;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionYearlyMethodResponse {
    private String year;
    private String paymentMethod;
    private Integer totalTransactions;
    private Long totalAmount;

    public static TransactionYearlyMethodResponse from(TransactionYearMethod entity) {
        return TransactionYearlyMethodResponse.builder()
                .year(entity.getYear())
                .paymentMethod(entity.getPaymentMethod())
                .totalTransactions(entity.getTotalTransactions())
                .totalAmount(entity.getTotalAmount())
                .build();
    }
}
