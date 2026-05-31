package com.sanedge.transaction.service.statsbymerchant;

import java.util.List;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.transaction.domain.requests.MonthMethodTransactionMerchantRequest;
import com.sanedge.transaction.domain.requests.YearMethodTransactionMerchantRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;

import io.smallrye.mutiny.Uni;

public interface TransactionMethodByMerchantService {
    Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodByMerchantSuccess(MonthMethodTransactionMerchantRequest req);
    Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodByMerchantFailed(MonthMethodTransactionMerchantRequest req);
    Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodByMerchantSuccess(YearMethodTransactionMerchantRequest req);
    Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodByMerchantFailed(YearMethodTransactionMerchantRequest req);
}
