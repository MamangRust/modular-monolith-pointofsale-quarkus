package com.sanedge.transaction.handler;

import com.sanedge.transaction.service.stats.TransactionAmountService;
import com.sanedge.transaction.service.statsbymerchant.TransactionAmountByMerchantService;
import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.transaction.Transaction.FindMonthlyTransactionStatus;
import pb.transaction.Transaction.FindMonthlyTransactionStatusCardNumber;
import pb.transaction.Transaction.FindYearTransactionStatus;
import pb.transaction.Transaction.FindYearTransactionStatusCardNumber;
import pb.transaction.stats.MutinyTransactionStatsStatusServiceGrpc;
import pb.transaction.stats.TransactionStatsStatus.ApiResponseTransactionMonthStatusFailed;
import pb.transaction.stats.TransactionStatsStatus.ApiResponseTransactionMonthStatusSuccess;
import pb.transaction.stats.TransactionStatsStatus.ApiResponseTransactionYearStatusFailed;
import pb.transaction.stats.TransactionStatsStatus.ApiResponseTransactionYearStatusSuccess;
import pb.transaction.stats.TransactionStatsStatus.TransactionMonthStatusFailedResponse;
import pb.transaction.stats.TransactionStatsStatus.TransactionMonthStatusSuccessResponse;
import pb.transaction.stats.TransactionStatsStatus.TransactionYearStatusFailedResponse;
import pb.transaction.stats.TransactionStatsStatus.TransactionYearStatusSuccessResponse;

@GrpcService
@Singleton
public class TransactionStatsStatusGrpcHandler extends MutinyTransactionStatsStatusServiceGrpc.TransactionStatsStatusServiceImplBase {

    @Inject
    TransactionAmountService transactionAmountService;

    @Inject
    TransactionAmountByMerchantService transactionAmountByMerchantService;

    @Override
    public Uni<ApiResponseTransactionMonthStatusSuccess> findMonthlyTransactionStatusSuccess(FindMonthlyTransactionStatus request) {
        com.sanedge.transaction.domain.requests.MonthAmountTransactionRequest domainReq = 
                new com.sanedge.transaction.domain.requests.MonthAmountTransactionRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return transactionAmountService.findMonthlyAmountSuccess(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionMonthStatusSuccess.Builder builder = ApiResponseTransactionMonthStatusSuccess.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionMonthStatusSuccessResponse.newBuilder()
                                    .setYear(item.getYear())
                                    .setMonth(item.getMonth())
                                    .setTotalSuccess(item.getTotalSuccess())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseTransactionYearStatusSuccess> findYearlyTransactionStatusSuccess(FindYearTransactionStatus request) {
        return transactionAmountService.findYearlyAmountSuccess(request.getYear())
                .map(apiResp -> {
                    ApiResponseTransactionYearStatusSuccess.Builder builder = ApiResponseTransactionYearStatusSuccess.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionYearStatusSuccessResponse.newBuilder()
                                    .setYear(item.getYear())
                                    .setTotalSuccess(item.getTotalSuccess())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseTransactionMonthStatusFailed> findMonthlyTransactionStatusFailed(FindMonthlyTransactionStatus request) {
        com.sanedge.transaction.domain.requests.MonthAmountTransactionRequest domainReq = 
                new com.sanedge.transaction.domain.requests.MonthAmountTransactionRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return transactionAmountService.findMonthlyAmountFailed(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionMonthStatusFailed.Builder builder = ApiResponseTransactionMonthStatusFailed.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionMonthStatusFailedResponse.newBuilder()
                                    .setYear(item.getYear())
                                    .setMonth(item.getMonth())
                                    .setTotalFailed(item.getTotalFailed())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseTransactionYearStatusFailed> findYearlyTransactionStatusFailed(FindYearTransactionStatus request) {
        return transactionAmountService.findYearlyAmountFailed(request.getYear())
                .map(apiResp -> {
                    ApiResponseTransactionYearStatusFailed.Builder builder = ApiResponseTransactionYearStatusFailed.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionYearStatusFailedResponse.newBuilder()
                                    .setYear(item.getYear())
                                    .setTotalFailed(item.getTotalFailed())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseTransactionMonthStatusSuccess> findMonthlyTransactionStatusSuccessByCardNumber(FindMonthlyTransactionStatusCardNumber request) {
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
        domainReq.setMonth(request.getMonth());

        return transactionAmountByMerchantService.findMonthlyAmountSuccessByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionMonthStatusSuccess.Builder builder = ApiResponseTransactionMonthStatusSuccess.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionMonthStatusSuccessResponse.newBuilder()
                                    .setYear(item.getYear())
                                    .setMonth(item.getMonth())
                                    .setTotalSuccess(item.getTotalSuccess())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseTransactionYearStatusSuccess> findYearlyTransactionStatusSuccessByCardNumber(FindYearTransactionStatusCardNumber request) {
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
                    ApiResponseTransactionYearStatusSuccess.Builder builder = ApiResponseTransactionYearStatusSuccess.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionYearStatusSuccessResponse.newBuilder()
                                    .setYear(item.getYear())
                                    .setTotalSuccess(item.getTotalSuccess())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseTransactionMonthStatusFailed> findMonthlyTransactionStatusFailedByCardNumber(FindMonthlyTransactionStatusCardNumber request) {
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
        domainReq.setMonth(request.getMonth());

        return transactionAmountByMerchantService.findMonthlyAmountFailedByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionMonthStatusFailed.Builder builder = ApiResponseTransactionMonthStatusFailed.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionMonthStatusFailedResponse.newBuilder()
                                    .setYear(item.getYear())
                                    .setMonth(item.getMonth())
                                    .setTotalFailed(item.getTotalFailed())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseTransactionYearStatusFailed> findYearlyTransactionStatusFailedByCardNumber(FindYearTransactionStatusCardNumber request) {
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

        return transactionAmountByMerchantService.findYearlyAmountFailedByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionYearStatusFailed.Builder builder = ApiResponseTransactionYearStatusFailed.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionYearStatusFailedResponse.newBuilder()
                                    .setYear(item.getYear())
                                    .setTotalFailed(item.getTotalFailed())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }
}
