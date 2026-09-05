package com.sanedge.order.handler;

import com.sanedge.order.domain.requests.MonthTotalRevenue;
import com.sanedge.order.domain.requests.MonthTotalRevenueByIdRequest;
import com.sanedge.order.domain.requests.MonthTotalRevenueMerchantRequest;
import com.sanedge.order.domain.requests.YearTotalRevenueByIdRequest;
import com.sanedge.order.domain.requests.YearTotalRevenueMerchantRequest;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.service.stats.OrderTotalRevenueService;
import com.sanedge.order.service.statsbyid.OrderTotalRevenueByIdService;
import com.sanedge.order.service.statsbymerchant.OrderTotalRevenueByMerchantService;

import com.sanedge.common.grpc.GrpcErrorMapper;
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

    @Inject
    OrderTotalRevenueByIdService orderTotalRevenueByIdService;

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
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
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
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseOrderMonthlyTotalRevenue> findMonthlyTotalRevenueById(FindYearMonthTotalRevenueById request) {
        MonthTotalRevenueByIdRequest domainReq = new MonthTotalRevenueByIdRequest();
        domainReq.setOrderId((long) request.getOrderId());
        domainReq.setYear(request.getYear());
        domainReq.setMonth(request.getMonth());

        return orderTotalRevenueByIdService.findMonthlyStatsById(domainReq)
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
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
    }

    @Override
    public Uni<ApiResponseOrderYearlyTotalRevenue> findYearlyTotalRevenueById(FindYearTotalRevenueById request) {
        YearTotalRevenueByIdRequest domainReq = new YearTotalRevenueByIdRequest();
        domainReq.setOrderId((long) request.getOrderId());
        domainReq.setYear(request.getYear());

        return orderTotalRevenueByIdService.findYearlyStatsById(domainReq)
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
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
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
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
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
                .onFailure().transform(GrpcErrorMapper::toStatusRuntimeException);
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
