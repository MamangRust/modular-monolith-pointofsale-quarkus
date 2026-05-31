package com.sanedge.gateway.service.impl;

import com.sanedge.gateway.dto.OrderDto;
import com.sanedge.gateway.service.OrderService;
import com.sanedge.gateway.telemetry.TelemetryHelper;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.stream.Collectors;

@ApplicationScoped
public class OrderServiceImpl implements OrderService {

    private static final Logger LOG = Logger.getLogger(OrderServiceImpl.class);

    @Inject
    TelemetryHelper telemetryHelper;

    @GrpcClient("order")
    pb.order.MutinyOrderQueryServiceGrpc.MutinyOrderQueryServiceStub orderQueryService;

    @GrpcClient("order")
    pb.order.MutinyOrderCommandServiceGrpc.MutinyOrderCommandServiceStub orderCommandService;

    @GrpcClient("order")
    pb.order.stats.MutinyOrderTotalRevenueServiceGrpc.MutinyOrderTotalRevenueServiceStub orderTotalRevenueServiceStub;

    @GrpcClient("order")
    pb.order.stats.MutinyOrderSoldoutServiceGrpc.MutinyOrderSoldoutServiceStub orderSoldoutServiceStub;

    @Override
    public Uni<OrderDto.ApiResponsePaginationOrder> listOrders(int page, int size, String search) {
        return telemetryHelper.traceAndMetric("order.listOrders",
                () -> orderQueryService.findAll(pb.order.Order.FindAllOrderRequest.newBuilder()
                        .setPage(page)
                        .setPageSize(size)
                        .setSearch(search == null ? "" : search)
                        .build())
                        .map(OrderDto.ApiResponsePaginationOrder::from)
                        .onFailure()
                        .invoke(throwable -> LOG.error("Failed to list orders: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponseOrder> getOrder(int id) {
        return telemetryHelper.traceAndMetric("order.getOrder",
                () -> orderQueryService.findById(pb.order.Order.FindByIdOrderRequest.newBuilder()
                        .setId(id)
                        .build())
                        .map(OrderDto.ApiResponseOrder::from)
                        .onFailure()
                        .invoke(throwable -> LOG.error("Failed to get order " + id + ": " + throwable.getMessage(),
                                throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponsePaginationOrder> listOrdersByMerchant(int merchantId, int page, int size,
            String search) {
        return telemetryHelper.traceAndMetric("order.listOrdersByMerchant",
                () -> orderQueryService
                        .findByMerchant(pb.order.Order.FindAllOrderMerchantRequest.newBuilder()
                                .setMerchantId(merchantId)
                                .setPage(page)
                                .setPageSize(size)
                                .setSearch(search == null ? "" : search)
                                .build())
                        .map(OrderDto.ApiResponsePaginationOrder::from)
                        .onFailure()
                        .invoke(throwable -> LOG.error("Failed to list orders by merchant " + merchantId + ": "
                                + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponsePaginationOrderDeleteAt> getActiveOrders(int page, int size, String search) {
        return telemetryHelper.traceAndMetric("order.getActiveOrders",
                () -> orderQueryService
                        .findByActive(pb.order.Order.FindAllOrderRequest.newBuilder()
                                .setPage(page)
                                .setPageSize(size)
                                .setSearch(search == null ? "" : search)
                                .build())
                        .map(OrderDto.ApiResponsePaginationOrderDeleteAt::from)
                        .onFailure()
                        .invoke(throwable -> LOG.error("Failed to list active orders: " + throwable.getMessage(),
                                throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponsePaginationOrderDeleteAt> getTrashedOrders(int page, int size, String search) {
        return telemetryHelper.traceAndMetric("order.getTrashedOrders",
                () -> orderQueryService.findByTrashed(pb.order.Order.FindAllOrderRequest.newBuilder()
                        .setPage(page)
                        .setPageSize(size)
                        .setSearch(search == null ? "" : search)
                        .build())
                        .map(OrderDto.ApiResponsePaginationOrderDeleteAt::from)
                        .onFailure()
                        .invoke(throwable -> LOG.error("Failed to list trashed orders: " + throwable.getMessage(),
                                throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponseOrder> createOrder(OrderDto.CreateRequest body) {
        return telemetryHelper.traceAndMetric("order.createOrder", () -> {
            pb.order.Order.CreateOrderRequest.Builder builder = pb.order.Order.CreateOrderRequest.newBuilder()
                    .setMerchantId(body.merchantId())
                    .setCashierId(body.cashierId());

            if (body.items() != null) {
                builder.addAllItems(body.items().stream()
                        .map(item -> pb.order.Order.CreateOrderItemRequest.newBuilder()
                                .setProductId(item.productId())
                                .setQuantity(item.quantity())
                                .build())
                        .collect(Collectors.toList()));
            }

            return orderCommandService.create(builder.build())
                    .map(OrderDto.ApiResponseOrder::from)
                    .onFailure()
                    .invoke(throwable -> LOG.error("Failed to create order: " + throwable.getMessage(), throwable));
        });
    }

    @Override
    public Uni<OrderDto.ApiResponseOrder> updateOrder(int id, OrderDto.UpdateRequest body) {
        return telemetryHelper.traceAndMetric("order.updateOrder", () -> {
            pb.order.Order.UpdateOrderRequest.Builder builder = pb.order.Order.UpdateOrderRequest.newBuilder()
                    .setOrderId(id)
                    .setCashierId(body.cashierId());

            if (body.items() != null) {
                builder.addAllItems(body.items().stream()
                        .map(item -> pb.order.Order.UpdateOrderItemRequest.newBuilder()
                                .setOrderItemId(item.orderItemId())
                                .setProductId(item.productId())
                                .setQuantity(item.quantity())
                                .build())
                        .collect(Collectors.toList()));
            }

            return orderCommandService.update(builder.build())
                    .map(OrderDto.ApiResponseOrder::from)
                    .onFailure()
                    .invoke(throwable -> LOG.error("Failed to update order " + id + ": " + throwable.getMessage(),
                            throwable));
        });
    }

    @Override
    public Uni<OrderDto.ApiResponseOrderDeleteAt> deleteOrder(int id) {
        return telemetryHelper.traceAndMetric("order.deleteOrder", () -> {
            return orderCommandService
                    .trashedOrder(pb.order.Order.FindByIdOrderRequest.newBuilder()
                            .setId(id)
                            .build())
                    .map(OrderDto.ApiResponseOrderDeleteAt::from)
                    .onFailure()
                    .invoke(throwable -> LOG.error("Failed to soft-delete order " + id + ": " + throwable.getMessage(),
                            throwable));
        });
    }

    @Override
    public Uni<OrderDto.ApiResponseOrderDeleteAt> restoreOrder(int id) {
        return telemetryHelper.traceAndMetric("order.restoreOrder", () -> orderCommandService
                .restoreOrder(pb.order.Order.FindByIdOrderRequest.newBuilder()
                        .setId(id)
                        .build())
                .map(OrderDto.ApiResponseOrderDeleteAt::from)
                .onFailure().invoke(throwable -> LOG
                        .error("Failed to restore order " + id + ": " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.SimpleResponse> deleteOrderPermanent(int id) {
        return telemetryHelper.traceAndMetric("order.deleteOrderPermanent", () -> orderCommandService
                .deleteOrderPermanent(pb.order.Order.FindByIdOrderRequest.newBuilder()
                        .setId(id)
                        .build())
                .map(OrderDto.SimpleResponse::from)
                .onFailure().invoke(throwable -> LOG.error(
                        "Failed to permanently delete order " + id + ": " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.SimpleResponse> restoreAllOrder() {
        return telemetryHelper.traceAndMetric("order.restoreAllOrder", () -> orderCommandService
                .restoreAllOrder(com.google.protobuf.Empty.getDefaultInstance())
                .map(OrderDto.SimpleResponse::from)
                .onFailure().invoke(throwable -> LOG
                        .error("Failed to restore all orders: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.SimpleResponse> deleteAllOrderPermanent() {
        return telemetryHelper.traceAndMetric("order.deleteAllOrderPermanent", () -> orderCommandService
                .deleteAllOrderPermanent(com.google.protobuf.Empty.getDefaultInstance())
                .map(OrderDto.SimpleResponse::from)
                .onFailure().invoke(throwable -> LOG
                        .error("Failed to permanently delete all orders: " + throwable.getMessage(), throwable)));
    }

    // stats
    @Override
    public Uni<OrderDto.ApiResponseOrderMonthlyTotalRevenue> getMonthlyTotalRevenue(int year, int month) {
        return telemetryHelper.traceAndMetric("order.getMonthlyTotalRevenue", () -> orderTotalRevenueServiceStub
                .findMonthlyTotalRevenue(pb.order.Order.FindYearMonthTotalRevenue.newBuilder()
                        .setYear(year)
                        .setMonth(month)
                        .build())
                .map(OrderDto.ApiResponseOrderMonthlyTotalRevenue::from)
                .onFailure().invoke(throwable -> LOG
                        .error("Failed to get monthly total revenue: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponseOrderYearlyTotalRevenue> getYearlyTotalRevenue(int year) {
        return telemetryHelper.traceAndMetric("order.getYearlyTotalRevenue", () -> orderTotalRevenueServiceStub
                .findYearlyTotalRevenue(pb.order.Order.FindYearTotalRevenue.newBuilder()
                        .setYear(year)
                        .build())
                .map(OrderDto.ApiResponseOrderYearlyTotalRevenue::from)
                .onFailure().invoke(throwable -> LOG
                        .error("Failed to get yearly total revenue: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponseOrderMonthlyTotalRevenue> getMonthlyTotalRevenueById(int orderId, int year,
            int month) {
        return telemetryHelper.traceAndMetric("order.getMonthlyTotalRevenueById", () -> orderTotalRevenueServiceStub
                .findMonthlyTotalRevenueById(pb.order.Order.FindYearMonthTotalRevenueById.newBuilder()
                        .setOrderId(orderId)
                        .setYear(year)
                        .setMonth(month)
                        .build())
                .map(OrderDto.ApiResponseOrderMonthlyTotalRevenue::from)
                .onFailure().invoke(throwable -> LOG
                        .error("Failed to get monthly total revenue by id: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponseOrderYearlyTotalRevenue> getYearlyTotalRevenueById(int orderId, int year) {
        return telemetryHelper.traceAndMetric("order.getYearlyTotalRevenueById", () -> orderTotalRevenueServiceStub
                .findYearlyTotalRevenueById(pb.order.Order.FindYearTotalRevenueById.newBuilder()
                        .setOrderId(orderId)
                        .setYear(year)
                        .build())
                .map(OrderDto.ApiResponseOrderYearlyTotalRevenue::from)
                .onFailure().invoke(throwable -> LOG
                        .error("Failed to get yearly total revenue by id: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponseOrderMonthlyTotalRevenue> getMonthlyTotalRevenueByMerchant(int merchantId, int year,
            int month) {
        return telemetryHelper.traceAndMetric("order.getMonthlyTotalRevenueByMerchant", () -> orderTotalRevenueServiceStub
                .findMonthlyTotalRevenueByMerchant(pb.order.Order.FindYearMonthTotalRevenueByMerchant.newBuilder()
                        .setMerchantId(merchantId)
                        .setYear(year)
                        .setMonth(month)
                        .build())
                .map(OrderDto.ApiResponseOrderMonthlyTotalRevenue::from)
                .onFailure().invoke(throwable -> LOG.error(
                        "Failed to get monthly total revenue by merchant: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponseOrderYearlyTotalRevenue> getYearlyTotalRevenueByMerchant(int merchantId, int year) {
        return telemetryHelper.traceAndMetric("order.getYearlyTotalRevenueByMerchant", () -> orderTotalRevenueServiceStub
                .findYearlyTotalRevenueByMerchant(pb.order.Order.FindYearTotalRevenueByMerchant.newBuilder()
                        .setMerchantId(merchantId)
                        .setYear(year)
                        .build())
                .map(OrderDto.ApiResponseOrderYearlyTotalRevenue::from)
                .onFailure().invoke(throwable -> LOG.error(
                        "Failed to get yearly total revenue by merchant: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponseOrderMonthly> getMonthlyRevenue(int year) {
        return telemetryHelper.traceAndMetric("order.getMonthlyRevenue", () -> orderSoldoutServiceStub
                .findMonthlyRevenue(pb.order.Order.FindYearOrder.newBuilder()
                        .setYear(year)
                        .build())
                .map(OrderDto.ApiResponseOrderMonthly::from)
                .onFailure().invoke(throwable -> LOG
                        .error("Failed to get monthly revenue: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponseOrderYearly> getYearlyRevenue(int year) {
        return telemetryHelper.traceAndMetric("order.getYearlyRevenue", () -> orderSoldoutServiceStub
                .findYearlyRevenue(pb.order.Order.FindYearOrder.newBuilder()
                        .setYear(year)
                        .build())
                .map(OrderDto.ApiResponseOrderYearly::from)
                .onFailure().invoke(throwable -> LOG
                        .error("Failed to get yearly revenue: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponseOrderMonthly> getMonthlyRevenueByMerchant(int merchantId, int year) {
        return telemetryHelper.traceAndMetric("order.getMonthlyRevenueByMerchant", () -> orderSoldoutServiceStub
                .findMonthlyRevenueByMerchant(pb.order.Order.FindYearOrderByMerchant.newBuilder()
                        .setMerchantId(merchantId)
                        .setYear(year)
                        .build())
                .map(OrderDto.ApiResponseOrderMonthly::from)
                .onFailure().invoke(throwable -> LOG
                        .error("Failed to get monthly revenue by merchant: " + throwable.getMessage(), throwable)));
    }

    @Override
    public Uni<OrderDto.ApiResponseOrderYearly> getYearlyRevenueByMerchant(int merchantId, int year) {
        return telemetryHelper.traceAndMetric("order.getYearlyRevenueByMerchant", () -> orderSoldoutServiceStub
                .findYearlyRevenueByMerchant(pb.order.Order.FindYearOrderByMerchant.newBuilder()
                        .setMerchantId(merchantId)
                        .setYear(year)
                        .build())
                .map(OrderDto.ApiResponseOrderYearly::from)
                .onFailure().invoke(throwable -> LOG
                        .error("Failed to get yearly revenue by merchant: " + throwable.getMessage(), throwable)));
    }
}
