package com.sanedge.order.handler;

import com.sanedge.order.domain.requests.MonthTotalRevenue;
import com.sanedge.order.domain.requests.MonthTotalRevenueMerchantRequest;
import com.sanedge.order.domain.requests.YearTotalRevenueMerchantRequest;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.service.stats.OrderTotalRevenueService;
import com.sanedge.order.service.statsbymerchant.OrderTotalRevenueByMerchantService;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.order.Order.FindYearMonthTotalRevenue;
import pb.order.Order.FindYearTotalRevenue;
import pb.order.Order.FindYearMonthTotalRevenueById;
import pb.order.Order.FindYearTotalRevenueById;
import pb.order.Order.FindYearMonthTotalRevenueByMerchant;
import pb.order.Order.FindYearTotalRevenueByMerchant;
import pb.order.Order.ApiResponseOrderMonthlyTotalRevenue;
import pb.order.Order.ApiResponseOrderYearlyTotalRevenue;
import pb.order.stats.MutinyOrderTotalRevenueServiceGrpc;

@GrpcService
@Singleton
public class OrderTotalRevenueGrpcHandler extends MutinyOrderTotalRevenueServiceGrpc.OrderTotalRevenueServiceImplBase {

    @Inject
    OrderTotalRevenueService orderTotalRevenueService;

    @Inject
    OrderTotalRevenueByMerchantService orderTotalRevenueByMerchantService;

    @Override
    public Uni<ApiResponseOrderMonthlyTotalRevenue> findMonthlyTotalRevenue(FindYearMonthTotalRevenue request) {
        MonthTotalRevenue domainReq = new MonthTotalRevenue();
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return orderTotalRevenueService.findMonthlyStats(domainReq)
                .map(apiResp -> {
                    ApiResponseOrderMonthlyTotalRevenue.Builder builder = ApiResponseOrderMonthlyTotalRevenue.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProto(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseOrderYearlyTotalRevenue> findYearlyTotalRevenue(FindYearTotalRevenue request) {
        return orderTotalRevenueService.findYearlyStats(request.getYear())
                .map(apiResp -> {
                    ApiResponseOrderYearlyTotalRevenue.Builder builder = ApiResponseOrderYearlyTotalRevenue.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProto(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseOrderMonthlyTotalRevenue> findMonthlyTotalRevenueById(FindYearMonthTotalRevenueById request) {
        return Uni.createFrom().item(
                ApiResponseOrderMonthlyTotalRevenue.newBuilder()
                        .setStatus("success")
                        .setMessage("No monthly total revenue stats by order ID are available")
                        .build()
        );
    }

    @Override
    public Uni<ApiResponseOrderYearlyTotalRevenue> findYearlyTotalRevenueById(FindYearTotalRevenueById request) {
        return Uni.createFrom().item(
                ApiResponseOrderYearlyTotalRevenue.newBuilder()
                        .setStatus("success")
                        .setMessage("No yearly total revenue stats by order ID are available")
                        .build()
        );
    }

    @Override
    public Uni<ApiResponseOrderMonthlyTotalRevenue> findMonthlyTotalRevenueByMerchant(FindYearMonthTotalRevenueByMerchant request) {
        MonthTotalRevenueMerchantRequest domainReq = new MonthTotalRevenueMerchantRequest();
        domainReq.setMerchantId(request.getMerchantId());
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return orderTotalRevenueByMerchantService.findMonthlyStatsByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseOrderMonthlyTotalRevenue.Builder builder = ApiResponseOrderMonthlyTotalRevenue.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProto(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    @Override
    public Uni<ApiResponseOrderYearlyTotalRevenue> findYearlyTotalRevenueByMerchant(FindYearTotalRevenueByMerchant request) {
        YearTotalRevenueMerchantRequest domainReq = new YearTotalRevenueMerchantRequest();
        domainReq.setMerchantId(request.getMerchantId());
        domainReq.setYear(request.getYear());

        return orderTotalRevenueByMerchantService.findYearlyStatsByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseOrderYearlyTotalRevenue.Builder builder = ApiResponseOrderYearlyTotalRevenue.newBuilder()
                            .setStatus(apiResp.status())
                            .setMessage(apiResp.message());
                    if (apiResp.data() != null) {
                        for (var item : apiResp.data()) {
                            builder.addData(toProto(item));
                        }
                    }
                    return builder.build();
                })
                .onFailure().transform(e -> Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }

    private pb.order.Order.OrderMonthlyTotalRevenueResponse toProto(OrderMonthlyTotalRevenueResponse r) {
        if (r == null) {
            return pb.order.Order.OrderMonthlyTotalRevenueResponse.getDefaultInstance();
        }
        return pb.order.Order.OrderMonthlyTotalRevenueResponse.newBuilder()
                .setYear(r.getYear() != null ? r.getYear() : "")
                .setMonth(r.getMonth() != null ? r.getMonth() : "")
                .setTotalRevenue(r.getTotalRevenue() != null ? r.getTotalRevenue().intValue() : 0)
                .build();
    }

    private pb.order.Order.OrderYearlyTotalRevenueResponse toProto(OrderYearlyTotalRevenueResponse r) {
        if (r == null) {
            return pb.order.Order.OrderYearlyTotalRevenueResponse.getDefaultInstance();
        }
        return pb.order.Order.OrderYearlyTotalRevenueResponse.newBuilder()
                .setYear(r.getYear() != null ? r.getYear() : "")
                .setTotalRevenue(r.getTotalRevenue() != null ? r.getTotalRevenue().intValue() : 0)
                .build();
    }
}
