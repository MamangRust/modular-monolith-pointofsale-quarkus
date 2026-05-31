package com.sanedge.transaction.service.stats;

import java.util.List;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.transaction.domain.requests.MonthAmountTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountSuccessResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountSuccessResponse;

import io.smallrye.mutiny.Uni;

public interface TransactionAmountService {
    Uni<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>> findMonthlyAmountSuccess(MonthAmountTransactionRequest req);
    Uni<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>> findYearlyAmountSuccess(Integer year);
    Uni<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>> findMonthlyAmountFailed(MonthAmountTransactionRequest req);
    Uni<ApiResponse<List<TransactionYearlyAmountFailedResponse>>> findYearlyAmountFailed(Integer year);
}
