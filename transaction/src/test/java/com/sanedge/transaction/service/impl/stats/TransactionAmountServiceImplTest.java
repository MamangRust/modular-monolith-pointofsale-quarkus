package com.sanedge.transaction.service.impl.stats;

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
import com.sanedge.transaction.domain.requests.MonthAmountTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountSuccessResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountSuccessResponse;
import com.sanedge.transaction.entity.TransactionMonthlyAmountFailed;
import com.sanedge.transaction.entity.TransactionMonthlyAmountSuccess;
import com.sanedge.transaction.entity.TransactionYearlyAmountFailed;
import com.sanedge.transaction.entity.TransactionYearlyAmountSuccess;
import com.sanedge.transaction.repository.stats.TransactionAmountStatusRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class TransactionAmountServiceImplTest {

    @Mock
    private TransactionAmountStatusRepository repository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private TransactionAmountServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new TransactionAmountServiceImpl(repository, redisService, objectMapper, tracingMetrics);
        // Lenient stub to execute the supplier directly
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    @Nested
    @DisplayName("findMonthlyAmountSuccess tests")
    class FindMonthlyAmountSuccessTests {
        @Test void nullYear_returnsError() {
            MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
            req.setYear(null); req.setMonth(6);
            ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> result = service.findMonthlyAmountSuccess(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void invalidMonth_returnsError() {
            MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
            req.setYear(2024); req.setMonth(13);
            ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> result = service.findMonthlyAmountSuccess(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
            req.setYear(2024); req.setMonth(6);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyTransactionSuccess(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionMonthlyAmountSuccess("2024","Jun",5,50000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> result = service.findMonthlyAmountSuccess(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).getMonth()).isEqualTo("Jun");
            assertThat(result.data().get(0).getTotalSuccess()).isEqualTo(5);
        }
        @Test void cacheHit_returnsCached() {
            MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
            req.setYear(2024); req.setMonth(6);
            ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionMonthlyAmountSuccessResponse("2024","Jun",5,50000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> result = service.findMonthlyAmountSuccess(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findYearlyAmountSuccess tests")
    class FindYearlyAmountSuccessTests {
        @Test void nullYear_returnsError() {
            ApiResponse<List<TransactionYearlyAmountSuccessResponse>> result = service.findYearlyAmountSuccess(null).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyTransactionSuccess(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionYearlyAmountSuccess("2024",10,100000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionYearlyAmountSuccessResponse>> result = service.findYearlyAmountSuccess(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            ApiResponse<List<TransactionYearlyAmountSuccessResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionYearlyAmountSuccessResponse("2024",10,100000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionYearlyAmountSuccessResponse>> result = service.findYearlyAmountSuccess(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
        }
    }

    @Nested
    @DisplayName("findMonthlyAmountFailed tests")
    class FindMonthlyAmountFailedTests {
        @Test void nullYear_returnsError() {
            MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
            req.setYear(null); req.setMonth(6);
            ApiResponse<List<TransactionMonthlyAmountFailedResponse>> result = service.findMonthlyAmountFailed(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void invalidMonth_returnsError() {
            MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
            req.setYear(2024); req.setMonth(13);
            ApiResponse<List<TransactionMonthlyAmountFailedResponse>> result = service.findMonthlyAmountFailed(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
            req.setYear(2024); req.setMonth(6);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyTransactionFailed(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionMonthlyAmountFailed("2024","Jun",2,20000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionMonthlyAmountFailedResponse>> result = service.findMonthlyAmountFailed(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            MonthAmountTransactionRequest req = new MonthAmountTransactionRequest();
            req.setYear(2024); req.setMonth(6);
            ApiResponse<List<TransactionMonthlyAmountFailedResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionMonthlyAmountFailedResponse("2024","Jun",2,20000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionMonthlyAmountFailedResponse>> result = service.findMonthlyAmountFailed(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
        }
    }

    @Nested
    @DisplayName("findYearlyAmountFailed tests")
    class FindYearlyAmountFailedTests {
        @Test void nullYear_returnsError() {
            ApiResponse<List<TransactionYearlyAmountFailedResponse>> result = service.findYearlyAmountFailed(null).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyTransactionFailed(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionYearlyAmountFailed("2024",3,30000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionYearlyAmountFailedResponse>> result = service.findYearlyAmountFailed(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            ApiResponse<List<TransactionYearlyAmountFailedResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionYearlyAmountFailedResponse("2024",3,30000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionYearlyAmountFailedResponse>> result = service.findYearlyAmountFailed(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
        }
    }
}