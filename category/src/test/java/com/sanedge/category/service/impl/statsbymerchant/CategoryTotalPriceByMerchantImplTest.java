package com.sanedge.category.service.impl.statsbymerchant;

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

import com.sanedge.category.domain.requests.MonthTotalPriceMerchant;
import com.sanedge.category.domain.requests.YearTotalPriceMerchant;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.entity.CategoryMonthTotalPrice;
import com.sanedge.category.entity.CategoryYearTotalPrice;
import com.sanedge.category.repository.statsbymerchant.CategoryTotalPriceByMerchantRepository;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CategoryTotalPriceByMerchantImpServiceTest {

    @Mock
    private CategoryTotalPriceByMerchantRepository repository;

    @Mock
    private TracingMetrics tracingMetrics;

    private CategoryTotalPriceByMerchantImpService service;

    @BeforeEach
    void setUp() {
        service = new CategoryTotalPriceByMerchantImpService(repository, tracingMetrics);
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
    }

    @Nested
    @DisplayName("findMonthlyTotalPriceByMerchant tests")
    class FindMonthlyTotalPriceByMerchantTests {
        @Test
        void nullParams_returnsError() {
            MonthTotalPriceMerchant req = new MonthTotalPriceMerchant();
            req.setMerchantId(null);
            ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> result = service.findMonthlyTotalPriceByMerchant(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void success() {
            MonthTotalPriceMerchant req = new MonthTotalPriceMerchant();
            req.setMerchantId(100);
            req.setYear(2024);
            req.setMonth(6);

            when(repository.findMonthlyTotalPriceByMerchant(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new CategoryMonthTotalPrice("2024", "Jun", 600000L))));

            ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> result = service.findMonthlyTotalPriceByMerchant(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        void failure_returnsError() {
            MonthTotalPriceMerchant req = new MonthTotalPriceMerchant();
            req.setMerchantId(100);
            req.setYear(2024);
            req.setMonth(6);
            when(repository.findMonthlyTotalPriceByMerchant(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

            ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> result = service.findMonthlyTotalPriceByMerchant(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }

    @Nested
    @DisplayName("findYearlyTotalPriceByMerchant tests")
    class FindYearlyTotalPriceByMerchantTests {
        @Test
        void nullParams_returnsError() {
            YearTotalPriceMerchant req = new YearTotalPriceMerchant();
            req.setMerchantId(null);
            ApiResponse<List<CategoriesYearlyTotalPriceResponse>> result = service.findYearlyTotalPriceByMerchant(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void success() {
            YearTotalPriceMerchant req = new YearTotalPriceMerchant();
            req.setMerchantId(100);
            req.setYear(2024);

            when(repository.findYearlyTotalPriceByMerchant(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new CategoryYearTotalPrice("2024", 1200000L))));

            ApiResponse<List<CategoriesYearlyTotalPriceResponse>> result = service.findYearlyTotalPriceByMerchant(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        void failure_returnsError() {
            YearTotalPriceMerchant req = new YearTotalPriceMerchant();
            req.setMerchantId(100);
            req.setYear(2024);
            when(repository.findYearlyTotalPriceByMerchant(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

            ApiResponse<List<CategoriesYearlyTotalPriceResponse>> result = service.findYearlyTotalPriceByMerchant(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }
}