package com.sanedge.transaction.service.impl.statsbymerchant;

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
import com.sanedge.transaction.domain.requests.MonthAmountTransactionMerchant;
import com.sanedge.transaction.domain.requests.YearAmountTransactionMerchant;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountSuccessResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountSuccessResponse;
import com.sanedge.transaction.entity.TransactionMonthlyAmountFailed;
import com.sanedge.transaction.entity.TransactionMonthlyAmountSuccess;
import com.sanedge.transaction.entity.TransactionYearlyAmountFailed;
import com.sanedge.transaction.entity.TransactionYearlyAmountSuccess;
import com.sanedge.transaction.repository.statsbymerchant.TransactionAmountByMerchantRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class TransactionAmountByMerchantServiceImplTest {

    @Mock
    private TransactionAmountByMerchantRepository repository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private TransactionAmountByMerchantServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new TransactionAmountByMerchantServiceImpl(repository, redisService, objectMapper, tracingMetrics);
        // Lenient stub to execute the supplier directly
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private MonthAmountTransactionMerchant monthReq(Long merchantId, Integer year, Integer month) {
        MonthAmountTransactionMerchant req = new MonthAmountTransactionMerchant();
        if (merchantId != null) {
            req.setMerchantId(merchantId.intValue());
        }
        req.setYear(year);
        req.setMonth(month);
        return req;
    }

    private YearAmountTransactionMerchant yearReq(Long merchantId, Integer year) {
        YearAmountTransactionMerchant req = new YearAmountTransactionMerchant();
        if (merchantId != null) {
            req.setMerchantId(merchantId.intValue());
        }
        req.setYear(year);
        return req;
    }

    // ----- findMonthlyAmountSuccessByMerchant -----
    @Nested
    @DisplayName("findMonthlyAmountSuccessByMerchant tests")
    class FindMonthlyAmountSuccessByMerchantTests {
        @Test void nullFields_returnsError() {
            MonthAmountTransactionMerchant req = monthReq(null, null, null);
            ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> resp = service.findMonthlyAmountSuccessByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
        }
        @Test void invalidMonth_returnsError() {
            MonthAmountTransactionMerchant req = monthReq(1L, 2024, 13);
            ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> resp = service.findMonthlyAmountSuccessByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            MonthAmountTransactionMerchant req = monthReq(100L, 2024, 6);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlySuccessByMerchant(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionMonthlyAmountSuccess("2024","Jun",5,50000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> resp = service.findMonthlyAmountSuccessByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).hasSize(1);
            assertThat(resp.data().get(0).getTotalSuccess()).isEqualTo(5);
        }
        @Test void cacheHit_returnsCached() {
            MonthAmountTransactionMerchant req = monthReq(100L, 2024, 6);
            ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionMonthlyAmountSuccessResponse("2024","Jun",5,50000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> resp = service.findMonthlyAmountSuccessByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).hasSize(1);
        }
    }

    // ----- findYearlyAmountSuccessByMerchant -----
    @Nested
    @DisplayName("findYearlyAmountSuccessByMerchant tests")
    class FindYearlyAmountSuccessByMerchantTests {
        @Test void nullFields_returnsError() {
            YearAmountTransactionMerchant req = yearReq(null, null);
            ApiResponse<List<TransactionYearlyAmountSuccessResponse>> resp = service.findYearlyAmountSuccessByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            YearAmountTransactionMerchant req = yearReq(100L, 2024);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlySuccessByMerchant(anyLong(), any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionYearlyAmountSuccess("2024",10,100000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionYearlyAmountSuccessResponse>> resp = service.findYearlyAmountSuccessByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            YearAmountTransactionMerchant req = yearReq(100L, 2024);
            ApiResponse<List<TransactionYearlyAmountSuccessResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionYearlyAmountSuccessResponse("2024",10,100000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionYearlyAmountSuccessResponse>> resp = service.findYearlyAmountSuccessByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
        }
    }

    // ----- findMonthlyAmountFailedByMerchant -----
    @Nested
    @DisplayName("findMonthlyAmountFailedByMerchant tests")
    class FindMonthlyAmountFailedByMerchantTests {
        @Test void nullFields_returnsError() {
            MonthAmountTransactionMerchant req = monthReq(null, null, null);
            ApiResponse<List<TransactionMonthlyAmountFailedResponse>> resp = service.findMonthlyAmountFailedByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
        }
        @Test void invalidMonth_returnsError() {
            MonthAmountTransactionMerchant req = monthReq(1L, 2024, 13);
            ApiResponse<List<TransactionMonthlyAmountFailedResponse>> resp = service.findMonthlyAmountFailedByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            MonthAmountTransactionMerchant req = monthReq(200L, 2024, 7);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyFailedByMerchant(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionMonthlyAmountFailed("2024","Jul",2,20000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionMonthlyAmountFailedResponse>> resp = service.findMonthlyAmountFailedByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            MonthAmountTransactionMerchant req = monthReq(200L, 2024, 7);
            ApiResponse<List<TransactionMonthlyAmountFailedResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionMonthlyAmountFailedResponse("2024","Jul",2,20000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionMonthlyAmountFailedResponse>> resp = service.findMonthlyAmountFailedByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
        }
    }

    // ----- findYearlyAmountFailedByMerchant -----
    @Nested
    @DisplayName("findYearlyAmountFailedByMerchant tests")
    class FindYearlyAmountFailedByMerchantTests {
        @Test void nullFields_returnsError() {
            YearAmountTransactionMerchant req = yearReq(null, null);
            ApiResponse<List<TransactionYearlyAmountFailedResponse>> resp = service.findYearlyAmountFailedByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            YearAmountTransactionMerchant req = yearReq(200L, 2024);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyFailedByMerchant(anyLong(), any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionYearlyAmountFailed("2024",3,30000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionYearlyAmountFailedResponse>> resp = service.findYearlyAmountFailedByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            YearAmountTransactionMerchant req = yearReq(200L, 2024);
            ApiResponse<List<TransactionYearlyAmountFailedResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionYearlyAmountFailedResponse("2024",3,30000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionYearlyAmountFailedResponse>> resp = service.findYearlyAmountFailedByMerchant(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
        }
    }
}