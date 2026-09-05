package com.sanedge.order.handler;

import com.sanedge.order.domain.requests.MonthOrderMerchantRequest;
import com.sanedge.order.domain.requests.YearOrderMerchantRequest;
import com.sanedge.order.domain.response.OrderMonthlyResponse;
import com.sanedge.order.domain.response.OrderYearlyResponse;
import com.sanedge.order.service.stats.OrderSoldoutService;
import com.sanedge.order.service.statsbymerchant.OrderSoldOutByMerchantService;

import com.sanedge.common.grpc.GrpcErrorMapper;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import pb.order.Order.FindYearOrder;
import pb.order.Order.FindYearOrderByMerchant;
import pb.order.Order.ApiResponseOrderMonthly;
import pb.order.Order.ApiResponseOrderYearly;
import pb.order.stats.MutinyOrderSoldoutServiceGrpc;

@GrpcService
@Singleton
public class OrderSoldoutGrpcHandler extends MutinyOrderSoldoutServiceGrpc.OrderSoldoutServiceImplBase {

    @Inject
    OrderSoldoutService orderSoldoutService;

    @Inject
    OrderSoldOutByMerchantService orderSoldOutByMerchantService;

    @Override
    public Uni<ApiResponseOrderMonthly> findMonthlyRevenue(FindYearOrder request) {
        return orderSoldoutService.findMonthlyOrders(request.getYear())
                .map(apiResp -> {
                    ApiResponseOrderMonthly.Builder builder = ApiResponseOrderMonthly.newBuilder()
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
    public Uni<ApiResponseOrderYearly> findYearlyRevenue(FindYearOrder request) {
        return orderSoldoutService.findYearlyOrders(request.getYear())
                .map(apiResp -> {
                    ApiResponseOrderYearly.Builder builder = ApiResponseOrderYearly.newBuilder()
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
    public Uni<ApiResponseOrderMonthly> findMonthlyRevenueByMerchant(FindYearOrderByMerchant request) {
        MonthOrderMerchantRequest domainReq = new MonthOrderMerchantRequest();
        domainReq.setMerchantId(request.getMerchantId());
        domainReq.setYear(request.getYear());

        return orderSoldOutByMerchantService.findMonthlyOrdersByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseOrderMonthly.Builder builder = ApiResponseOrderMonthly.newBuilder()
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
    public Uni<ApiResponseOrderYearly> findYearlyRevenueByMerchant(FindYearOrderByMerchant request) {
        YearOrderMerchantRequest domainReq = new YearOrderMerchantRequest();
        domainReq.setMerchantId(request.getMerchantId());
        domainReq.setYear(request.getYear());

        return orderSoldOutByMerchantService.findYearlyOrdersByMerchant(domainReq)
                .map(apiResp -> {
                    ApiResponseOrderYearly.Builder builder = ApiResponseOrderYearly.newBuilder()
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

    private pb.order.Order.OrderMonthlyResponse toProto(OrderMonthlyResponse r) {
        if (r == null) {
            return pb.order.Order.OrderMonthlyResponse.getDefaultInstance();
        }
        return pb.order.Order.OrderMonthlyResponse.newBuilder()
                .setMonth(r.getMonth() != null ? r.getMonth() : "")
                .setOrderCount(r.getOrderCount() != null ? r.getOrderCount() : 0)
                .setTotalRevenue(r.getTotalRevenue() != null ? r.getTotalRevenue().intValue() : 0)
                .setTotalItemsSold(r.getTotalItemsSold() != null ? r.getTotalItemsSold() : 0)
                .build();
    }

    private pb.order.Order.OrderYearlyResponse toProto(OrderYearlyResponse r) {
        if (r == null) {
            return pb.order.Order.OrderYearlyResponse.getDefaultInstance();
        }
        return pb.order.Order.OrderYearlyResponse.newBuilder()
                .setYear(r.getYear() != null ? r.getYear() : "")
                .setOrderCount(r.getOrderCount() != null ? r.getOrderCount() : 0)
                .setTotalRevenue(r.getTotalRevenue() != null ? r.getTotalRevenue().intValue() : 0)
                .setTotalItemsSold(r.getTotalItemsSold() != null ? r.getTotalItemsSold() : 0)
                .setActiveCashiers(r.getActiveCashiers() != null ? r.getActiveCashiers() : 0)
                .setUniqueProductsSold(r.getUniqueProductsSold() != null ? r.getUniqueProductsSold() : 0)
                .build();
    }
}
