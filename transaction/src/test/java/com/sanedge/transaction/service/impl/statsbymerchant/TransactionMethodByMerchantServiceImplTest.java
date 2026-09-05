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
import com.sanedge.transaction.domain.requests.MonthMethodTransactionMerchantRequest;
import com.sanedge.transaction.domain.requests.YearMethodTransactionMerchantRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;
import com.sanedge.transaction.entity.TransactionMonthlyMethod;
import com.sanedge.transaction.entity.TransactionYearMethod;
import com.sanedge.transaction.repository.statsbymerchant.TransactionMethodByMerchantRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class TransactionMethodByMerchantServiceImplTest {

    @Mock
    private TransactionMethodByMerchantRepository repository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private TransactionMethodByMerchantServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new TransactionMethodByMerchantServiceImpl(repository, redisService, objectMapper, tracingMetrics);
        // Lenient stub to execute the supplier directly
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private MonthMethodTransactionMerchantRequest monthReq(Integer merchantId, Integer year, Integer month) {
        MonthMethodTransactionMerchantRequest req = new MonthMethodTransactionMerchantRequest();
        req.setMerchantId(merchantId);
        req.setYear(year);
        req.setMonth(month);
        return req;
    }

    private YearMethodTransactionMerchantRequest yearReq(Integer merchantId, Integer year) {
        YearMethodTransactionMerchantRequest req = new YearMethodTransactionMerchantRequest();
        req.setMerchantId(merchantId);
        req.setYear(year);
        return req;
    }

    // ----- findMonthlyMethodByMerchantSuccess -----
    @Nested
    @DisplayName("findMonthlyMethodByMerchantSuccess tests")
    class FindMonthlyMethodByMerchantSuccessTests {
        @Test void nullFields_returnsError() {
            MonthMethodTransactionMerchantRequest req = monthReq(null, null, null);
            ApiResponse<List<TransactionMonthlyMethodResponse>> resp = service.findMonthlyMethodByMerchantSuccess(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
        }
        @Test void invalidMonth_returnsError() {
            MonthMethodTransactionMerchantRequest req = monthReq(1, 2024, 13);
            ApiResponse<List<TransactionMonthlyMethodResponse>> resp = service.findMonthlyMethodByMerchantSuccess(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            MonthMethodTransactionMerchantRequest req = monthReq(100, 2024, 6);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyTransactionMethodsSuccessByMerchant(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionMonthlyMethod("Jun","CREDIT",5,50000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionMonthlyMethodResponse>> resp = service.findMonthlyMethodByMerchantSuccess(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).hasSize(1);
            assertThat(resp.data().get(0).getPaymentMethod()).isEqualTo("CREDIT");
        }
        @Test void cacheHit_returnsCached() {
            MonthMethodTransactionMerchantRequest req = monthReq(100, 2024, 6);
            ApiResponse<List<TransactionMonthlyMethodResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionMonthlyMethodResponse("Jun","CREDIT",5,50000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionMonthlyMethodResponse>> resp = service.findMonthlyMethodByMerchantSuccess(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).hasSize(1);
        }
    }

    // ----- findMonthlyMethodByMerchantFailed -----
    @Nested
    @DisplayName("findMonthlyMethodByMerchantFailed tests")
    class FindMonthlyMethodByMerchantFailedTests {
        @Test void nullFields_returnsError() {
            MonthMethodTransactionMerchantRequest req = monthReq(null, null, null);
            ApiResponse<List<TransactionMonthlyMethodResponse>> resp = service.findMonthlyMethodByMerchantFailed(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
        }
        @Test void invalidMonth_returnsError() {
            MonthMethodTransactionMerchantRequest req = monthReq(1, 2024, 13);
            ApiResponse<List<TransactionMonthlyMethodResponse>> resp = service.findMonthlyMethodByMerchantFailed(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            MonthMethodTransactionMerchantRequest req = monthReq(200, 2024, 7);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findMonthlyTransactionMethodsFailedByMerchant(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionMonthlyMethod("Jul","CASH",2,20000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionMonthlyMethodResponse>> resp = service.findMonthlyMethodByMerchantFailed(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            MonthMethodTransactionMerchantRequest req = monthReq(200, 2024, 7);
            ApiResponse<List<TransactionMonthlyMethodResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionMonthlyMethodResponse("Jul","CASH",2,20000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionMonthlyMethodResponse>> resp = service.findMonthlyMethodByMerchantFailed(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
        }
    }

    // ----- findYearlyMethodByMerchantSuccess -----
    @Nested
    @DisplayName("findYearlyMethodByMerchantSuccess tests")
    class FindYearlyMethodByMerchantSuccessTests {
        @Test void nullFields_returnsError() {
            YearMethodTransactionMerchantRequest req = yearReq(null, null);
            ApiResponse<List<TransactionYearlyMethodResponse>> resp = service.findYearlyMethodByMerchantSuccess(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            YearMethodTransactionMerchantRequest req = yearReq(100, 2024);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyTransactionMethodsSuccessByMerchant(anyLong(), any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionYearMethod("2024","CREDIT",10,100000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionYearlyMethodResponse>> resp = service.findYearlyMethodByMerchantSuccess(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            YearMethodTransactionMerchantRequest req = yearReq(100, 2024);
            ApiResponse<List<TransactionYearlyMethodResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionYearlyMethodResponse("2024","CREDIT",10,100000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionYearlyMethodResponse>> resp = service.findYearlyMethodByMerchantSuccess(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
        }
    }

    // ----- findYearlyMethodByMerchantFailed -----
    @Nested
    @DisplayName("findYearlyMethodByMerchantFailed tests")
    class FindYearlyMethodByMerchantFailedTests {
        @Test void nullFields_returnsError() {
            YearMethodTransactionMerchantRequest req = yearReq(null, null);
            ApiResponse<List<TransactionYearlyMethodResponse>> resp = service.findYearlyMethodByMerchantFailed(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("error");
        }
        @Test void cacheMiss_fetchesFromDb() {
            YearMethodTransactionMerchantRequest req = yearReq(200, 2024);
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(repository.findYearlyTransactionMethodsFailedByMerchant(anyLong(), any()))
                    .thenReturn(Uni.createFrom().item(List.of(new TransactionYearMethod("2024","CASH",3,30000L))));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong())).thenReturn(Uni.createFrom().voidItem());

            ApiResponse<List<TransactionYearlyMethodResponse>> resp = service.findYearlyMethodByMerchantFailed(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
            assertThat(resp.data()).hasSize(1);
        }
        @Test void cacheHit_returnsCached() {
            YearMethodTransactionMerchantRequest req = yearReq(200, 2024);
            ApiResponse<List<TransactionYearlyMethodResponse>> cached = ApiResponse.success("Success",
                    List.of(new TransactionYearlyMethodResponse("2024","CASH",3,30000L)));
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<List<TransactionYearlyMethodResponse>> resp = service.findYearlyMethodByMerchantFailed(req).await().indefinitely();
            assertThat(resp.status()).isEqualTo("success");
        }
    }
}