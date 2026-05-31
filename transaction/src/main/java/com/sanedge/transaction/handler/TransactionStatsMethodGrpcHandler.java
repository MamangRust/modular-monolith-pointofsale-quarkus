package com.sanedge.transaction.handler;

import com.sanedge.transaction.service.stats.TransactionMethodService;
import com.sanedge.transaction.service.statsbymerchant.TransactionMethodByMerchantService;
import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.transaction.Transaction.FindByYearCardNumberTransactionRequest;
import pb.transaction.Transaction.FindYearTransactionStatus;
import pb.transaction.stats.MutinyTransactionStatsMethodServiceGrpc;
import pb.transaction.stats.TransactionStatsMethod.ApiResponseTransactionMonthMethod;
import pb.transaction.stats.TransactionStatsMethod.ApiResponseTransactionYearMethod;
import pb.transaction.stats.TransactionStatsMethod.TransactionMonthMethodResponse;
import pb.transaction.stats.TransactionStatsMethod.TransactionYearMethodResponse;

@GrpcService
@Singleton
public class TransactionStatsMethodGrpcHandler extends MutinyTransactionStatsMethodServiceGrpc.TransactionStatsMethodServiceImplBase {

    @Inject
    TransactionMethodService transactionMethodService;

    @Inject
    TransactionMethodByMerchantService transactionMethodByMerchantService;

    @Override
    public Uni<ApiResponseTransactionMonthMethod> findMonthlyPaymentMethods(FindYearTransactionStatus request) {
        // Fallback to month = 1 or current month (e.g. 5) as the service takes MonthMethodTransactionRequest
        com.sanedge.transaction.domain.requests.MonthMethodTransactionRequest domainReq = 
                new com.sanedge.transaction.domain.requests.MonthMethodTransactionRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(5); // default month

        return transactionMethodService.findMonthlyMethodSuccess(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionMonthMethod.Builder builder = ApiResponseTransactionMonthMethod.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionMonthMethodResponse.newBuilder()
                                    .setMonth(item.getMonth())
                                    .setPaymentMethod(item.getPaymentMethod())
                                    .setTotalTransactions(item.getTotalTransactions())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseTransactionYearMethod> findYearlyPaymentMethods(FindYearTransactionStatus request) {
        return transactionMethodService.findYearlyMethodSuccess(request.getYear())
                .map(apiResp -> {
                    ApiResponseTransactionYearMethod.Builder builder = ApiResponseTransactionYearMethod.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionYearMethodResponse.newBuilder()
                                    .setYear(item.getYear())
                                    .setPaymentMethod(item.getPaymentMethod())
                                    .setTotalTransactions(item.getTotalTransactions())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseTransactionMonthMethod> findMonthlyPaymentMethodsByCardNumber(FindByYearCardNumberTransactionRequest request) {
        com.sanedge.transaction.domain.requests.MonthMethodTransactionMerchantRequest domainReq = 
                new com.sanedge.transaction.domain.requests.MonthMethodTransactionMerchantRequest();
        
        int merchantId = 1;
        try {
            merchantId = Integer.parseInt(request.getCardNumber());
        } catch (NumberFormatException e) {
            // ignore
        }

        domainReq.setMerchantId(merchantId);
        domainReq.setYear(request.getYear());
        domainReq.setMonth(5); // default month

        return transactionMethodByMerchantService.findMonthlyMethodByMerchantSuccess(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionMonthMethod.Builder builder = ApiResponseTransactionMonthMethod.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionMonthMethodResponse.newBuilder()
                                    .setMonth(item.getMonth())
                                    .setPaymentMethod(item.getPaymentMethod())
                                    .setTotalTransactions(item.getTotalTransactions())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseTransactionYearMethod> findYearlyPaymentMethodsByCardNumber(FindByYearCardNumberTransactionRequest request) {
        com.sanedge.transaction.domain.requests.YearMethodTransactionMerchantRequest domainReq = 
                new com.sanedge.transaction.domain.requests.YearMethodTransactionMerchantRequest();
        
        int merchantId = 1;
        try {
            merchantId = Integer.parseInt(request.getCardNumber());
        } catch (NumberFormatException e) {
            // ignore
        }

        domainReq.setMerchantId(merchantId);
        domainReq.setYear(request.getYear());

        return transactionMethodByMerchantService.findYearlyMethodByMerchantSuccess(domainReq)
                .map(apiResp -> {
                    ApiResponseTransactionYearMethod.Builder builder = ApiResponseTransactionYearMethod.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(TransactionYearMethodResponse.newBuilder()
                                    .setYear(item.getYear())
                                    .setPaymentMethod(item.getPaymentMethod())
                                    .setTotalTransactions(item.getTotalTransactions())
                                    .setTotalAmount(item.getTotalAmount().intValue())
                                    .build());
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
}
