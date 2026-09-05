package com.sanedge.cashier.handler;

import com.sanedge.cashier.domain.requests.MonthTotalSales;
import com.sanedge.cashier.domain.requests.MonthTotalSalesCashier;
import com.sanedge.cashier.domain.requests.MonthTotalSalesMerchant;
import com.sanedge.cashier.domain.requests.YearTotalSalesCashier;
import com.sanedge.cashier.domain.requests.YearTotalSalesMerchant;
import com.sanedge.cashier.service.stats.CashierTotalSalesService;
import com.sanedge.cashier.service.statsbyid.CashierTotalSalesByIdService;
import com.sanedge.cashier.service.statsbymerchant.CashierTotalSalesByMerchantService;

import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.cashier.Cashier.FindYearMonthTotalSales;
import pb.cashier.Cashier.FindYearMonthTotalSalesById;
import pb.cashier.Cashier.FindYearMonthTotalSalesByMerchant;
import pb.cashier.Cashier.FindYearTotalSales;
import pb.cashier.Cashier.FindYearTotalSalesById;
import pb.cashier.Cashier.FindYearTotalSalesByMerchant;
import pb.cashier.stats.CashierTotalSales.ApiResponseCashierMonthlyTotalSales;
import pb.cashier.stats.CashierTotalSales.ApiResponseCashierYearlyTotalSales;
import pb.cashier.stats.MutinyCashierTotalSalesServiceGrpc;

@GrpcService
@Singleton
public class CashierTotalSalesGrpcHandler extends MutinyCashierTotalSalesServiceGrpc.CashierTotalSalesServiceImplBase {

    @Inject
    CashierTotalSalesService cashierTotalSalesService;

    @Inject
    CashierTotalSalesByIdService cashierTotalSalesByIdService;

    @Inject
    CashierTotalSalesByMerchantService cashierTotalSalesByMerchantService;

    @Override
    public Uni<ApiResponseCashierMonthlyTotalSales> findMonthlyTotalSales(FindYearMonthTotalSales request) {
        MonthTotalSales domainReq = new MonthTotalSales();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return cashierTotalSalesService.findMonthlyTotalSales(domainReq)
                .map(apiResp -> {
                    ApiResponseCashierMonthlyTotalSales.Builder builder = ApiResponseCashierMonthlyTotalSales
                            .newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoMonthlyTotalSales(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseCashierYearlyTotalSales> findYearlyTotalSales(FindYearTotalSales request) {
        return cashierTotalSalesService.findYearlyTotalSales(request.getYear())
                .map(apiResp -> {
                    ApiResponseCashierYearlyTotalSales.Builder builder = ApiResponseCashierYearlyTotalSales.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoYearlyTotalSales(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseCashierMonthlyTotalSales> findMonthlyTotalSalesById(FindYearMonthTotalSalesById request) {
        MonthTotalSalesCashier domainReq = new MonthTotalSalesCashier();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());
        domainReq.setCashierId(request.getCashierId());

        return cashierTotalSalesByIdService.findMonthlyTotalSalesById(domainReq)
                .map(apiResp -> {
                    ApiResponseCashierMonthlyTotalSales.Builder builder = ApiResponseCashierMonthlyTotalSales
                            .newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoMonthlyTotalSales(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseCashierYearlyTotalSales> findYearlyTotalSalesById(FindYearTotalSalesById request) {
        YearTotalSalesCashier domainReq = new YearTotalSalesCashier();
        domainReq.setYear(request.getYear());
        domainReq.setCashierId(request.getCashierId());

        return cashierTotalSalesByIdService.findYearlyTotalSalesById(domainReq)
                .map(apiResp -> {
                    ApiResponseCashierYearlyTotalSales.Builder builder = ApiResponseCashierYearlyTotalSales.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoYearlyTotalSales(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseCashierMonthlyTotalSales> findMonthlyTotalSalesByMerchant(
            FindYearMonthTotalSalesByMerchant request) {
        MonthTotalSalesMerchant domainReq = new MonthTotalSalesMerchant();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());
        domainReq.setMerchantId(request.getMerchantId());

        return cashierTotalSalesByMerchantService.findMonthlyTotalSalesByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseCashierMonthlyTotalSales.Builder builder = ApiResponseCashierMonthlyTotalSales
                            .newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoMonthlyTotalSales(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseCashierYearlyTotalSales> findYearlyTotalSalesByMerchant(
            FindYearTotalSalesByMerchant request) {
        YearTotalSalesMerchant domainReq = new YearTotalSalesMerchant();
        domainReq.setYear(request.getYear());
        domainReq.setMerchantId(request.getMerchantId());

        return cashierTotalSalesByMerchantService.findYearlyTotalSalesByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseCashierYearlyTotalSales.Builder builder = ApiResponseCashierYearlyTotalSales.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProtoYearlyTotalSales(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    private pb.cashier.Cashier.CashierResponseMonthTotalSales toProtoMonthlyTotalSales(
            com.sanedge.cashier.domain.response.CashierResponseMonthTotalSales r) {
        if (r == null) {
            return pb.cashier.Cashier.CashierResponseMonthTotalSales.getDefaultInstance();
        }
        return pb.cashier.Cashier.CashierResponseMonthTotalSales.newBuilder()
                .setYear(r.getYear())
                .setMonth(r.getMonth())
                .setTotalSales(r.getTotalSales().intValue())
                .build();
    }

    private pb.cashier.Cashier.CashierResponseYearTotalSales toProtoYearlyTotalSales(
            com.sanedge.cashier.domain.response.CashierResponseYearTotalSales r) {
        if (r == null) {
            return pb.cashier.Cashier.CashierResponseYearTotalSales.getDefaultInstance();
        }
        return pb.cashier.Cashier.CashierResponseYearTotalSales.newBuilder()
                .setYear(r.getYear())
                .setTotalSales(r.getTotalSales().intValue())
                .build();
    }
}
