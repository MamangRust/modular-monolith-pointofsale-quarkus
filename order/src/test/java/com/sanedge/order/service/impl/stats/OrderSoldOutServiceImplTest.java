package com.sanedge.order.service.impl.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order.domain.response.OrderMonthlyResponse;
import com.sanedge.order.domain.response.OrderYearlyResponse;
import com.sanedge.order.entity.OrderMonth;
import com.sanedge.order.entity.OrderYear;
import com.sanedge.order.repository.stats.OrderSoldOutRepository;
import com.sanedge.order.service.impl.OrderSoldOutServiceImpl;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class OrderSoldOutServiceImplTest {

    @Mock
    private OrderSoldOutRepository repository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private OrderSoldOutServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new OrderSoldOutServiceImpl(repository, redisService, objectMapper, tracingMetrics);
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("findMonthlyOrders tests")
    class FindMonthlyOrdersTests {
        @Test
        void nullYearMonth_returnsError() {
            ApiResponse<List<OrderMonthlyResponse>> result = service.findMonthlyOrders(null).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void cacheMiss_fetchesFromDb() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyOrdersByYear(anyInt()))
                    .thenReturn(Uni.createFrom().item(List.of(new OrderMonth("Jan", 10, 50000L, 20))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<OrderMonthlyResponse>> result = service.findMonthlyOrders(202401).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        void cacheHit_returnsCached() {
            ApiResponse<List<OrderMonthlyResponse>> cached = ApiResponse.success("Success",
                    List.of(new OrderMonthlyResponse("Jan", 10, 50000L, 20)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<OrderMonthlyResponse>> result = service.findMonthlyOrders(202401).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
        }

        @Test
        void failure_returnsError() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyOrdersByYear(anyInt()))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));
            ApiResponse<List<OrderMonthlyResponse>> result = service.findMonthlyOrders(202401).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }

    @Nested
    @DisplayName("findYearlyOrders tests")
    class FindYearlyOrdersTests {
        @Test
        void nullYearMonth_returnsError() {
            ApiResponse<List<OrderYearlyResponse>> result = service.findYearlyOrders(null).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void cacheMiss_fetchesFromDb() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyOrders(anyInt()))
                    .thenReturn(Uni.createFrom().item(List.of(new OrderYear("2024", 100, 500000L, 200, 5, 10))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<OrderYearlyResponse>> result = service.findYearlyOrders(202401).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        void cacheHit_returnsCached() {
            ApiResponse<List<OrderYearlyResponse>> cached = ApiResponse.success("Success",
                    List.of(new OrderYearlyResponse("2024", 100, 500000L, 200, 5, 10)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<OrderYearlyResponse>> result = service.findYearlyOrders(202401).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
        }

        @Test
        void failure_returnsError() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyOrders(anyInt()))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));
            ApiResponse<List<OrderYearlyResponse>> result = service.findYearlyOrders(202401).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }
}