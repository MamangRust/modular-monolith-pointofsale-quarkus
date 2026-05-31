package com.sanedge.transaction.service.stats;

import java.util.List;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.transaction.domain.requests.MonthMethodTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;

import io.smallrye.mutiny.Uni;

public interface TransactionMethodService {
    Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodSuccess(MonthMethodTransactionRequest req);
    Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodSuccess(Integer year);
    Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodFailed(MonthMethodTransactionRequest req);
    Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodFailed(Integer year);
}
