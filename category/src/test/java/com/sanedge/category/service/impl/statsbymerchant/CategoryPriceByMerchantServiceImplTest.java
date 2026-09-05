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

import com.sanedge.category.domain.requests.MonthPriceMerchant;
import com.sanedge.category.domain.requests.YearPriceMerchant;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.entity.CategoryMonthPrice;
import com.sanedge.category.entity.CategoryYearPrice;
import com.sanedge.category.repository.statsbymerchant.CategoryPriceByMerchantRepository;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CategoryPriceByMerchantImplServiceTest {

    @Mock
    private CategoryPriceByMerchantRepository repository;

    @Mock
    private TracingMetrics tracingMetrics;

    private CategoryPriceByMerchantImplService service;

    @BeforeEach
    void setUp() {
        service = new CategoryPriceByMerchantImplService(repository, tracingMetrics);
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
    }

    @Nested
    @DisplayName("findMonthPriceByMerchant tests")
    class FindMonthPriceByMerchantTests {
        @Test
        void nullParams_returnsError() {
            MonthPriceMerchant req = new MonthPriceMerchant();
            req.setMerchantId(null);
            ApiResponse<List<CategoriesMonthPriceResponse>> result = service.findMonthPriceByMerchant(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void success() {
            MonthPriceMerchant req = new MonthPriceMerchant();
            req.setMerchantId(100);
            req.setYear(2024);

            when(repository.findMonthlyCategoryPriceByMerchant(anyLong(), any()))
                    .thenReturn(Uni.createFrom().item(List.of(new CategoryMonthPrice("Mar", 2, "Cat2", 8, 30, 7000L))));

            ApiResponse<List<CategoriesMonthPriceResponse>> result = service.findMonthPriceByMerchant(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        void failure_returnsError() {
            MonthPriceMerchant req = new MonthPriceMerchant();
            req.setMerchantId(100);
            req.setYear(2024);
            when(repository.findMonthlyCategoryPriceByMerchant(anyLong(), any()))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

            ApiResponse<List<CategoriesMonthPriceResponse>> result = service.findMonthPriceByMerchant(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }

    @Nested
    @DisplayName("findYearPriceByMerchant tests")
    class FindYearPriceByMerchantTests {
        @Test
        void nullParams_returnsError() {
            YearPriceMerchant req = new YearPriceMerchant();
            req.setMerchantId(null);
            ApiResponse<List<CategoriesYearPriceResponse>> result = service.findYearPriceByMerchant(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void success() {
            YearPriceMerchant req = new YearPriceMerchant();
            req.setMerchantId(100);
            req.setYear(2024);

            when(repository.findYearlyCategoryPriceByMerchant(anyLong(), any()))
                    .thenReturn(Uni.createFrom().item(List.of(new CategoryYearPrice("2024", 2, "Cat2", 15, 70, 25000L, 4))));

            ApiResponse<List<CategoriesYearPriceResponse>> result = service.findYearPriceByMerchant(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        void failure_returnsError() {
            YearPriceMerchant req = new YearPriceMerchant();
            req.setMerchantId(100);
            req.setYear(2024);
            when(repository.findYearlyCategoryPriceByMerchant(anyLong(), any()))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

            ApiResponse<List<CategoriesYearPriceResponse>> result = service.findYearPriceByMerchant(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }
}