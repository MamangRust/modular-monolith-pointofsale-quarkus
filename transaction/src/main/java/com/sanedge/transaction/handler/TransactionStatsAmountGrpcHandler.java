package com.sanedge.transaction.handler;

import com.sanedge.transaction.service.stats.TransactionAmountService;
import com.sanedge.transaction.service.statsbymerchant.TransactionAmountByMerchantService;
import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.transaction.Transaction.FindByYearCardNumberTransactionRequest;
import pb.transaction.Transaction.FindYearTransactionStatus;
import pb.transaction.stats.MutinyTransactionStatsAmountServiceGrpc;
import pb.transaction.stats.TransactionStatsAmount.ApiResponseTransactionMonthAmount;
import pb.transaction.stats.TransactionStatsAmount.ApiResponseTransactionYearAmount;
import pb.transaction.stats.TransactionStatsAmount.TransactionMonthAmountResponse;
import pb.transaction.stats.TransactionStatsAmount.TransactionYearlyAmountResponse;

@GrpcService
@Singleton
public class TransactionStatsAmountGrpcHandler extends MutinyTransactionStatsAmountServiceGrpc.TransactionStatsAmountServiceImplBase {

    @Inject
    TransactionAmountService transactionAmountService;

    @Inject
    TransactionAmountByMerchantService transactionAmountByMerchantService;

    @Override
    public Uni<ApiResponseTransactionMonthAmount> findMonthlyAmounts(FindYearTransactionStatus request) {
        com.sanedge.transaction.domain.requests.MonthAmountTransactionRequest domainReq = 
                new com.sanedge.transaction.domain.requests.MonthAmountTransactionRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(5); // default month

        return transactionAmountService.findMonthlyAmountSuccess(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionMonthAmount.Builder builder = ApiResponseTransactionMonthAmount.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionMonthAmountResponse.newBuilder()
                                    .setMonth(item.getMonth())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseTransactionYearAmount> findYearlyAmounts(FindYearTransactionStatus request) {
        return transactionAmountService.findYearlyAmountSuccess(request.getYear())
                .map(apiResp -> {
                    ApiResponseTransactionYearAmount.Builder builder = ApiResponseTransactionYearAmount.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionYearlyAmountResponse.newBuilder()
                                    .setYear(item.getYear())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseTransactionMonthAmount> findMonthlyAmountsByCardNumber(FindByYearCardNumberTransactionRequest request) {
        com.sanedge.transaction.domain.requests.MonthAmountTransactionMerchant domainReq = 
                new com.sanedge.transaction.domain.requests.MonthAmountTransactionMerchant();
        
        int merchantId = 1;
        try {
            merchantId = Integer.parseInt(request.getCardNumber());
        } catch (NumberFormatException e) {
            // ignore
        }

        domainReq.setMerchantId(merchantId);
        domainReq.setYear(request.getYear());
        domainReq.setMonth(5); // default month

        return transactionAmountByMerchantService.findMonthlyAmountSuccessByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionMonthAmount.Builder builder = ApiResponseTransactionMonthAmount.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionMonthAmountResponse.newBuilder()
                                    .setMonth(item.getMonth())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseTransactionYearAmount> findYearlyAmountsByCardNumber(FindByYearCardNumberTransactionRequest request) {
        com.sanedge.transaction.domain.requests.YearAmountTransactionMerchant domainReq = 
                new com.sanedge.transaction.domain.requests.YearAmountTransactionMerchant();
        
        int merchantId = 1;
        try {
            merchantId = Integer.parseInt(request.getCardNumber());
        } catch (NumberFormatException e) {
            // ignore
        }

        domainReq.setMerchantId(merchantId);
        domainReq.setYear(request.getYear());

        return transactionAmountByMerchantService.findYearlyAmountSuccessByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionYearAmount.Builder builder = ApiResponseTransactionYearAmount.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionYearlyAmountResponse.newBuilder()
                                    .setYear(item.getYear())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
}
