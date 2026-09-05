package com.sanedge.category.service.impl.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import com.sanedge.category.domain.requests.MonthTotalPrice;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.entity.CategoryMonthTotalPrice;
import com.sanedge.category.entity.CategoryYearTotalPrice;
import com.sanedge.category.repository.stats.CategoryTotalPriceRepository;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CategoryTotalPriceServiceImplTest {

    @Mock
    private CategoryTotalPriceRepository repository;

    @Mock
    private TracingMetrics tracingMetrics;

    private CategoryTotalPriceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryTotalPriceServiceImpl(repository, tracingMetrics);
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
    }

    @Nested
    @DisplayName("findMonthlyTotalPrice tests")
    class FindMonthlyTotalPriceTests {
        @Test
        void nullYearOrMonth_returnsError() {
            MonthTotalPrice req = new MonthTotalPrice();
            req.setYear(null);
            req.setMonth(6);
            ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> result = service.findMonthlyTotalPrice(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void success() {
            MonthTotalPrice req = new MonthTotalPrice();
            req.setYear(2024);
            req.setMonth(6);

            when(repository.findMonthlyTotalPrice(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new CategoryMonthTotalPrice("2024", "Jun", 500000L))));

            ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> result = service.findMonthlyTotalPrice(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).getMonth()).isEqualTo("Jun");
        }

        @Test
        void failure_returnsError() {
            MonthTotalPrice req = new MonthTotalPrice();
            req.setYear(2024);
            req.setMonth(6);
            when(repository.findMonthlyTotalPrice(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

            ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> result = service.findMonthlyTotalPrice(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
            assertThat(result.data()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findYearlyTotalPrice tests")
    class FindYearlyTotalPriceTests {
        @Test
        void nullYear_returnsError() {
            ApiResponse<List<CategoriesYearlyTotalPriceResponse>> result = service.findYearlyTotalPrice(null).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void success() {
            when(repository.findYearlyTotalPrice(2024, 2023))
                    .thenReturn(Uni.createFrom().item(List.of(new CategoryYearTotalPrice("2024", 1000000L))));

            ApiResponse<List<CategoriesYearlyTotalPriceResponse>> result = service.findYearlyTotalPrice(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        void failure_returnsError() {
            when(repository.findYearlyTotalPrice(2024, 2023)).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

            ApiResponse<List<CategoriesYearlyTotalPriceResponse>> result = service.findYearlyTotalPrice(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }
}