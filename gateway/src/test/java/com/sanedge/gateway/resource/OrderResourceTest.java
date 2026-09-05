package com.sanedge.gateway.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.gateway.dto.OrderDto;
import com.sanedge.gateway.service.OrderService;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class OrderResourceTest {
    @Mock
    OrderService orderService;
    private OrderResource orderResource;

    @BeforeEach
    void setUp() throws Exception {
        orderResource = new OrderResource();
        Field f = OrderResource.class.getDeclaredField("orderService");
        f.setAccessible(true);
        f.set(orderResource, orderService);
    }

    @Test
    void listOrders_Success() {
        OrderDto.ApiResponsePaginationOrder dto = new OrderDto.ApiResponsePaginationOrder(
                "success", "ok", List.of(), null);
        lenient().when(orderService.listOrders(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = orderResource.listOrders(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getOrder_Success() {
        OrderDto.ApiResponseOrder dto = new OrderDto.ApiResponseOrder("success", "ok", null);
        lenient().when(orderService.getOrder(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = orderResource.getOrder(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getActiveOrders_Success() {
        OrderDto.ApiResponsePaginationOrderDeleteAt dto = new OrderDto.ApiResponsePaginationOrderDeleteAt(
                "success", "ok", List.of(), null);
        lenient().when(orderService.getActiveOrders(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = orderResource.getActiveOrders(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getTrashedOrders_Success() {
        OrderDto.ApiResponsePaginationOrderDeleteAt dto = new OrderDto.ApiResponsePaginationOrderDeleteAt(
                "success", "ok", List.of(), null);
        lenient().when(orderService.getTrashedOrders(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = orderResource.getTrashedOrders(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void createOrder_Success_Returns201() {
        OrderDto.ApiResponseOrder dto = new OrderDto.ApiResponseOrder("success", "created", null);
        lenient().when(orderService.createOrder(any())).thenReturn(Uni.createFrom().item(dto));
        OrderDto.CreateRequest req = new OrderDto.CreateRequest(1, 1, null);
        Response r = orderResource.createOrder(req).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(201);
    }

    @Test
    void deleteOrder_Success() {
        OrderDto.ApiResponseOrderDeleteAt dto = new OrderDto.ApiResponseOrderDeleteAt("success", "trashed", null);
        lenient().when(orderService.deleteOrder(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = orderResource.deleteOrder(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void restoreOrder_Success() {
        OrderDto.ApiResponseOrderDeleteAt dto = new OrderDto.ApiResponseOrderDeleteAt("success", "restored", null);
        lenient().when(orderService.restoreOrder(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = orderResource.restoreOrder(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void deleteOrderPermanent_Success() {
        OrderDto.SimpleResponse dto = new OrderDto.SimpleResponse("success", "deleted");
        lenient().when(orderService.deleteOrderPermanent(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = orderResource.deleteOrderPermanent(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void restoreAllOrder_Success() {
        OrderDto.SimpleResponse dto = new OrderDto.SimpleResponse("success", "restored");
        lenient().when(orderService.restoreAllOrder()).thenReturn(Uni.createFrom().item(dto));
        Response r = orderResource.restoreAllOrder().await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }
}
