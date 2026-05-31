package com.sanedge.cashier.handler;

import com.sanedge.cashier.domain.requests.MonthCashierIdRequest;
import com.sanedge.cashier.domain.requests.MonthCashierMerchantRequest;
import com.sanedge.cashier.domain.requests.YearCashierIdRequest;
import com.sanedge.cashier.domain.requests.YearCashierMerchantRequest;
import com.sanedge.cashier.service.stats.CashierSalesService;
import com.sanedge.cashier.service.statsbyid.CashierSalesByIdService;
import com.sanedge.cashier.service.statsbymerchant.CashierSalesByMerchantService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.cashier.Cashier.ApiResponseCashierMonthSales;
import pb.cashier.Cashier.ApiResponseCashierYearSales;
import pb.cashier.Cashier.FindYearCashier;
import pb.cashier.Cashier.FindYearCashierById;
import pb.cashier.Cashier.FindYearCashierByMerchant;
import pb.cashier.stats.MutinyCashierSalesServiceGrpc;

@GrpcService
@Singleton
public class CashierSalesGrpcHandler extends MutinyCashierSalesServiceGrpc.CashierSalesServiceImplBase {

    @Inject
    CashierSalesService cashierSalesService;

    @Inject
    CashierSalesByIdService cashierSalesByIdService;

    @Inject
    CashierSalesByMerchantService cashierSalesByMerchantService;

    @Override
    public Uni<ApiResponseCashierMonthSales> findMonthSales(FindYearCashier request) {
        return cashierSalesService.findMonthlySales(request.getYear())
                .map(apiResp -> {
                    ApiResponseCashierMonthSales.Builder builder = ApiResponseCashierMonthSales.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoMonthSales(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseCashierYearSales> findYearSales(FindYearCashier request) {
        return cashierSalesService.findYearlySales(request.getYear())
                .map(apiResp -> {
                    ApiResponseCashierYearSales.Builder builder = ApiResponseCashierYearSales.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoYearSales(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseCashierMonthSales> findMonthSalesByMerchant(FindYearCashierByMerchant request) {
        MonthCashierMerchantRequest domainReq = new MonthCashierMerchantRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMerchantId(request.getMerchantId());

        return cashierSalesByMerchantService.findMonthlyCashierByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseCashierMonthSales.Builder builder = ApiResponseCashierMonthSales.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoMonthSales(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseCashierYearSales> findYearSalesByMerchant(FindYearCashierByMerchant request) {
        YearCashierMerchantRequest domainReq = new YearCashierMerchantRequest();
        domainReq.setYear(request.getYear());
        domainReq.setMerchantId(request.getMerchantId());

        return cashierSalesByMerchantService.findYearlyCashierByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseCashierYearSales.Builder builder = ApiResponseCashierYearSales.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoYearSales(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseCashierMonthSales> findMonthSalesById(FindYearCashierById request) {
        MonthCashierIdRequest domainReq = new MonthCashierIdRequest();
        domainReq.setYear(request.getYear());
        domainReq.setCashierId(request.getCashierId());

        return cashierSalesByIdService.findMonthlyCashierById(domainReq)
                .map(apiResp -> {
                    ApiResponseCashierMonthSales.Builder builder = ApiResponseCashierMonthSales.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoMonthSales(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseCashierYearSales> findYearSalesById(FindYearCashierById request) {
        YearCashierIdRequest domainReq = new YearCashierIdRequest();
        domainReq.setYear(request.getYear());
        domainReq.setCashierId(request.getCashierId());

        return cashierSalesByIdService.findYearlyCashierById(domainReq)
                .map(apiResp -> {
                    ApiResponseCashierYearSales.Builder builder = ApiResponseCashierYearSales.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoYearSales(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    private pb.cashier.Cashier.CashierResponseMonthSales toProtoMonthSales(
            com.sanedge.cashier.domain.response.CashierResponseMonthSales r) {
        if (r == null) {
            return pb.cashier.Cashier.CashierResponseMonthSales.getDefaultInstance();
        }
        return pb.cashier.Cashier.CashierResponseMonthSales.newBuilder()
                .setCashierId(r.getCashierId().intValue())
                .setCashierName(r.getCashierName())
                .setMonth(r.getMonth())
                .setTotalSales(r.getTotalSales().intValue())
                .build();
    }

    private pb.cashier.Cashier.CashierResponseYearSales toProtoYearSales(
            com.sanedge.cashier.domain.response.CashierResponseYearSales r) {
        if (r == null) {
            return pb.cashier.Cashier.CashierResponseYearSales.getDefaultInstance();
        }
        return pb.cashier.Cashier.CashierResponseYearSales.newBuilder()
                .setCashierId(r.getCashierId().intValue())
                .setCashierName(r.getCashierName())
                .setYear(r.getYear())
                .setTotalSales(r.getTotalSales().intValue())
                .build();
    }
}
