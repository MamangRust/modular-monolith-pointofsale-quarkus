package com.sanedge.order.service.impl.stats;

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
import com.sanedge.order.domain.requests.MonthTotalRevenue;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.entity.OrderMonthTotalRevenue;
import com.sanedge.order.entity.OrderYearTotalRevenue;
import com.sanedge.order.repository.stats.OrderTotalRevenueRepository;
import com.sanedge.order.service.impl.OrderTotalRevenueServiceImpl;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class OrderTotalRevenueServiceImplTest {

    @Mock
    private OrderTotalRevenueRepository repository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private OrderTotalRevenueServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new OrderTotalRevenueServiceImpl(repository, redisService, objectMapper, tracingMetrics);
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    @Nested
    @DisplayName("findMonthlyStats tests")
    class FindMonthlyStatsTests {
        @Test void nullYear_returnsError() {
            MonthTotalRevenue req = new MonthTotalRevenue();
            req.setYear(null);
            req.setMonth(6);
            ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStats(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void nullMonth_returnsError() {
            MonthTotalRevenue req = new MonthTotalRevenue();
            req.setYear(2024);
            req.setMonth(null);
            ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStats(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            MonthTotalRevenue req = new MonthTotalRevenue();
            req.setYear(2024);
            req.setMonth(6);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyTotalRevenue(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new OrderMonthTotalRevenue("2024","Jun",500000))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStats(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).getMonth()).isEqualTo("Jun");
        }
        @Test void cacheHit_returnsCached() {
            MonthTotalRevenue req = new MonthTotalRevenue();
            req.setYear(2024);
            req.setMonth(6);
            ApiResponse<List<OrderMonthlyTotalRevenueResponse>> cached = ApiResponse.success("Success",
                    List.of(new OrderMonthlyTotalRevenueResponse("2024","Jun",500000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStats(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
        }
        @Test void failure_returnsError() {
            MonthTotalRevenue req = new MonthTotalRevenue();
            req.setYear(2024);
            req.setMonth(6);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyTotalRevenue(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));
            ApiResponse<List<OrderMonthlyTotalRevenueResponse>> result = service.findMonthlyStats(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }

    @Nested
    @DisplayName("findYearlyStats tests")
    class FindYearlyStatsTests {
        @Test void nullYear_returnsError() {
            ApiResponse<List<OrderYearlyTotalRevenueResponse>> result = service.findYearlyStats(null).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyTotalRevenue(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new OrderYearTotalRevenue("2024",1000000))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<OrderYearlyTotalRevenueResponse>> result = service.findYearlyStats(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            ApiResponse<List<OrderYearlyTotalRevenueResponse>> cached = ApiResponse.success("Success",
                    List.of(new OrderYearlyTotalRevenueResponse("2024",1000000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<OrderYearlyTotalRevenueResponse>> result = service.findYearlyStats(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
        }
        @Test void failure_returnsError() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyTotalRevenue(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));
            ApiResponse<List<OrderYearlyTotalRevenueResponse>> result = service.findYearlyStats(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }
}