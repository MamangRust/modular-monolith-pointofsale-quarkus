package com.sanedge.order.service.impl.statsbyid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.sanedge.order.domain.requests.MonthTotalRevenueByIdRequest;
import com.sanedge.order.domain.requests.YearTotalRevenueByIdRequest;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.entity.OrderMonthTotalRevenue;
import com.sanedge.order.entity.OrderYearTotalRevenue;
import com.sanedge.order.repository.stats.OrderTotalRevenueRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class OrderTotalRevenueByIdServiceImplTest {

    @Mock
    private OrderTotalRevenueRepository repository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private OrderTotalRevenueByIdServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new OrderTotalRevenueByIdServiceImpl(repository, redisService, objectMapper, tracingMetrics);
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
    @DisplayName("findMonthlyStatsById tests")
    class FindMonthlyStatsByIdTests {
        @Test
        void nullParams_returnsError() {
            MonthTotalRevenueByIdRequest req = new MonthTotalRevenueByIdRequest();
            req.setOrderId(null);
            ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStatsById(req).await()
                    .indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void invalidMonth_returnsError() {
            MonthTotalRevenueByIdRequest req = new MonthTotalRevenueByIdRequest();
            req.setOrderId(42L);
            req.setYear(2024);
            req.setMonth(13);
            ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStatsById(req).await()
                    .indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void cacheMiss_fetchesFromDb() {
            MonthTotalRevenueByIdRequest req = new MonthTotalRevenueByIdRequest();
            req.setOrderId(42L);
            req.setYear(2024);
            req.setMonth(6);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyTotalRevenueById(anyLong(), any(), any()))
                    .thenReturn(Uni.createFrom().item(List.of(new OrderMonthTotalRevenue("2024", "Jun", 500000))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStatsById(req).await()
                    .indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).getMonth()).isEqualTo("Jun");
            assertThat(result.data().get(0).getTotalRevenue()).isEqualTo(500000);
        }

        @Test
        void cacheHit_returnsCached() {
            MonthTotalRevenueByIdRequest req = new MonthTotalRevenueByIdRequest();
            req.setOrderId(42L);
            req.setYear(2024);
            req.setMonth(6);
            ApiResponse<List<OrderMonthlyTotalRevenueResponse>> cached = ApiResponse.success("Success",
                    List.of(new OrderMonthlyTotalRevenueResponse("2024", "Jun", 500000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStatsById(req).await()
                    .indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        void failure_returnsError() {
            MonthTotalRevenueByIdRequest req = new MonthTotalRevenueByIdRequest();
            req.setOrderId(42L);
            req.setYear(2024);
            req.setMonth(6);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyTotalRevenueById(anyLong(), any(), any()))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));
            ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStatsById(req).await()
                    .indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }

    @Nested
    @DisplayName("findYearlyStatsById tests")
    class FindYearlyStatsByIdTests {
        @Test
        void nullParams_returnsError() {
            YearTotalRevenueByIdRequest req = new YearTotalRevenueByIdRequest();
            req.setOrderId(null);
            ApiResponse<List<OrderYearlyTotalRevenueResponse>> result = service.findYearlyStatsById(req).await()
                    .indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void cacheMiss_fetchesFromDb() {
            YearTotalRevenueByIdRequest req = new YearTotalRevenueByIdRequest();
            req.setOrderId(42L);
            req.setYear(2024);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyTotalRevenueById(anyLong(), any()))
                    .thenReturn(Uni.createFrom().item(List.of(new OrderYearTotalRevenue("2024", 1200000))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<OrderYearlyTotalRevenueResponse>> result = service.findYearlyStatsById(req).await()
                    .indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).getTotalRevenue()).isEqualTo(1200000);
        }

        @Test
        void cacheHit_returnsCached() {
            YearTotalRevenueByIdRequest req = new YearTotalRevenueByIdRequest();
            req.setOrderId(42L);
            req.setYear(2024);
            ApiResponse<List<OrderYearlyTotalRevenueResponse>> cached = ApiResponse.success("Success",
                    List.of(new OrderYearlyTotalRevenueResponse("2024", 1200000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<OrderYearlyTotalRevenueResponse>> result = service.findYearlyStatsById(req).await()
                    .indefinitely();
            assertThat(result.status()).isEqualTo("success");
        }

        @Test
        void failure_returnsError() {
            YearTotalRevenueByIdRequest req = new YearTotalRevenueByIdRequest();
            req.setOrderId(42L);
            req.setYear(2024);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyTotalRevenueById(anyLong(), any()))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));
            ApiResponse<List<OrderYearlyTotalRevenueResponse>> result = service.findYearlyStatsById(req).await()
                    .indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }
}
