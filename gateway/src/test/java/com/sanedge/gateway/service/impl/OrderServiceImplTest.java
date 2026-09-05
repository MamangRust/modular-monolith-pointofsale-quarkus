package com.sanedge.gateway.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.gateway.dto.OrderDto;
import com.sanedge.gateway.telemetry.TelemetryHelper;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private TelemetryHelper telemetryHelper;
    @Mock
    private pb.order.MutinyOrderQueryServiceGrpc.MutinyOrderQueryServiceStub orderQueryService;
    @Mock
    private pb.order.MutinyOrderCommandServiceGrpc.MutinyOrderCommandServiceStub orderCommandService;
    @Mock
    private pb.order.stats.MutinyOrderTotalRevenueServiceGrpc.MutinyOrderTotalRevenueServiceStub orderTotalRevenueServiceStub;
    @Mock
    private pb.order.stats.MutinyOrderSoldoutServiceGrpc.MutinyOrderSoldoutServiceStub orderSoldoutServiceStub;

    private OrderServiceImpl service;

    private void inject(String name, Object value) throws Exception {
        Field f = OrderServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(telemetryHelper.traceAndMetric(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Uni<?>> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
        service = new OrderServiceImpl();
        inject("telemetryHelper", telemetryHelper);
        inject("orderQueryService", orderQueryService);
        inject("orderCommandService", orderCommandService);
        inject("orderTotalRevenueServiceStub", orderTotalRevenueServiceStub);
        inject("orderSoldoutServiceStub", orderSoldoutServiceStub);
    }

    @Test
    void listOrders_PropagatesResponse() {
        pb.order.OrderQuery.ApiResponsePaginationOrder proto = pb.order.OrderQuery.ApiResponsePaginationOrder.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(orderQueryService.findAll(any(pb.order.Order.FindAllOrderRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.listOrders(new com.sanedge.gateway.domain.requests.FindAllOrdersRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getOrder_PropagatesResponse() {
        pb.order.Order.ApiResponseOrder proto = pb.order.Order.ApiResponseOrder.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(orderQueryService.findById(any(pb.order.Order.FindByIdOrderRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getOrder(1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getActiveOrders_PropagatesResponse() {
        pb.order.OrderQuery.ApiResponsePaginationOrderDeleteAt proto = pb.order.OrderQuery.ApiResponsePaginationOrderDeleteAt.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(orderQueryService.findByActive(any(pb.order.Order.FindAllOrderRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getActiveOrders(new com.sanedge.gateway.domain.requests.FindAllOrdersRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getTrashedOrders_PropagatesResponse() {
        pb.order.OrderQuery.ApiResponsePaginationOrderDeleteAt proto = pb.order.OrderQuery.ApiResponsePaginationOrderDeleteAt.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(orderQueryService.findByTrashed(any(pb.order.Order.FindAllOrderRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getTrashedOrders(new com.sanedge.gateway.domain.requests.FindAllOrdersRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void createOrder_PropagatesResponse() {
        pb.order.Order.ApiResponseOrder proto = pb.order.Order.ApiResponseOrder.newBuilder()
                .setStatus("success").setMessage("created").build();
        lenient().when(orderCommandService.create(any(pb.order.Order.CreateOrderRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.createOrder(new OrderDto.CreateRequest(1, 1, null)).await().indefinitely();
        assertThat(result.message()).isEqualTo("created");
    }

    @Test
    void deleteOrder_TrashStub_Propagates() {
        pb.order.Order.ApiResponseOrderDeleteAt proto = pb.order.Order.ApiResponseOrderDeleteAt.newBuilder()
                .setStatus("success").setMessage("trashed").build();
        lenient().when(orderCommandService.trashedOrder(any(pb.order.Order.FindByIdOrderRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.deleteOrder(1).await().indefinitely();
        assertThat(result.message()).isEqualTo("trashed");
    }

    @Test
    void restoreOrder_RestoreStub_Propagates() {
        pb.order.Order.ApiResponseOrderDeleteAt proto = pb.order.Order.ApiResponseOrderDeleteAt.newBuilder()
                .setStatus("success").setMessage("restored").build();
        lenient().when(orderCommandService.restoreOrder(any(pb.order.Order.FindByIdOrderRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.restoreOrder(1).await().indefinitely();
        assertThat(result.message()).isEqualTo("restored");
    }
}
