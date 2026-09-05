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
import com.sanedge.transaction.domain.requests.MonthMethodTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;
import com.sanedge.transaction.entity.TransactionMonthlyMethod;
import com.sanedge.transaction.entity.TransactionYearMethod;
import com.sanedge.transaction.repository.stats.TransactionMethodRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class TransactionMethodServiceImplTest {

    @Mock
    private TransactionMethodRepository repository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private TransactionMethodServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new TransactionMethodServiceImpl(repository, redisService, objectMapper, tracingMetrics);
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
    @DisplayName("findMonthlyMethodSuccess tests")
    class FindMonthlyMethodSuccessTests {
        @Test void nullYear_returnsError() {
            MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
            req.setYear(null); req.setMonth(6);
            ApiResponse<List<TransactionMonthlyMethodResponse>> result = service.findMonthlyMethodSuccess(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void invalidMonth_returnsError() {
            MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
            req.setYear(2024); req.setMonth(13);
            ApiResponse<List<TransactionMonthlyMethodResponse>> result = service.findMonthlyMethodSuccess(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
            req.setYear(2024); req.setMonth(6);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyMethodsSuccess(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionMonthlyMethod("Jun","CREDIT",5,50000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionMonthlyMethodResponse>> result = service.findMonthlyMethodSuccess(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
            req.setYear(2024); req.setMonth(6);
            ApiResponse<List<TransactionMonthlyMethodResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionMonthlyMethodResponse("Jun","CREDIT",5,50000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionMonthlyMethodResponse>> result = service.findMonthlyMethodSuccess(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
        }
    }

    @Nested
    @DisplayName("findYearlyMethodSuccess tests")
    class FindYearlyMethodSuccessTests {
        @Test void nullYear_returnsError() {
            ApiResponse<List<TransactionYearlyMethodResponse>> result = service.findYearlyMethodSuccess(null).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyMethodsSuccess(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionYearMethod("2024","CREDIT",10,100000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionYearlyMethodResponse>> result = service.findYearlyMethodSuccess(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            ApiResponse<List<TransactionYearlyMethodResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionYearlyMethodResponse("2024","CREDIT",10,100000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionYearlyMethodResponse>> result = service.findYearlyMethodSuccess(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
        }
    }

    @Nested
    @DisplayName("findMonthlyMethodFailed tests")
    class FindMonthlyMethodFailedTests {
        @Test void nullYear_returnsError() {
            MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
            req.setYear(null); req.setMonth(6);
            ApiResponse<List<TransactionMonthlyMethodResponse>> result = service.findMonthlyMethodFailed(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void invalidMonth_returnsError() {
            MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
            req.setYear(2024); req.setMonth(13);
            ApiResponse<List<TransactionMonthlyMethodResponse>> result = service.findMonthlyMethodFailed(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
            req.setYear(2024); req.setMonth(6);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyMethodsFailed(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionMonthlyMethod("Jun","CASH",2,20000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionMonthlyMethodResponse>> result = service.findMonthlyMethodFailed(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            MonthMethodTransactionRequest req = new MonthMethodTransactionRequest();
            req.setYear(2024); req.setMonth(6);
            ApiResponse<List<TransactionMonthlyMethodResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionMonthlyMethodResponse("Jun","CASH",2,20000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionMonthlyMethodResponse>> result = service.findMonthlyMethodFailed(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
        }
    }

    @Nested
    @DisplayName("findYearlyMethodFailed tests")
    class FindYearlyMethodFailedTests {
        @Test void nullYear_returnsError() {
            ApiResponse<List<TransactionYearlyMethodResponse>> result = service.findYearlyMethodFailed(null).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyMethodsFailed(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionYearMethod("2024","CASH",3,30000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionYearlyMethodResponse>> result = service.findYearlyMethodFailed(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            ApiResponse<List<TransactionYearlyMethodResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionYearlyMethodResponse("2024","CASH",3,30000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionYearlyMethodResponse>> result = service.findYearlyMethodFailed(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
        }
    }
}