package com.sanedge.transaction.service.statsbymerchant;

import java.util.List;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.transaction.domain.requests.MonthAmountTransactionMerchant;
import com.sanedge.transaction.domain.requests.YearAmountTransactionMerchant;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountSuccessResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountSuccessResponse;

import io.smallrye.mutiny.Uni;

public interface TransactionAmountByMerchantService {
    Uni<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>> findMonthlyAmountSuccessByMerchant(MonthAmountTransactionMerchant req);
    Uni<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>> findYearlyAmountSuccessByMerchant(YearAmountTransactionMerchant req);
    Uni<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>> findMonthlyAmountFailedByMerchant(MonthAmountTransactionMerchant req);
    Uni<ApiResponse<List<TransactionYearlyAmountFailedResponse>>> findYearlyAmountFailedByMerchant(YearAmountTransactionMerchant req);
}
