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

import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.entity.CategoryMonthPrice;
import com.sanedge.category.entity.CategoryYearPrice;
import com.sanedge.category.repository.stats.CategoryPriceRepository;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CategoryPriceServiceImplTest {

    @Mock
    private CategoryPriceRepository repository;

    @Mock
    private TracingMetrics tracingMetrics;

    private CategoryPriceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryPriceServiceImpl(repository, tracingMetrics);
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
    }

    @Nested
    @DisplayName("findMonthPrice tests")
    class FindMonthPriceTests {
        @Test
        void nullYear_returnsError() {
            ApiResponse<List<CategoriesMonthPriceResponse>> result = service.findMonthPrice(null).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
            assertThat(result.message()).contains("Year must not be null");
        }

        @Test
        void success() {
            when(repository.findMonthlyCategoryPrice(2024))
                    .thenReturn(Uni.createFrom().item(List.of(new CategoryMonthPrice("Jan", 1, "Cat", 10, 50, 5000L))));

            ApiResponse<List<CategoriesMonthPriceResponse>> result = service.findMonthPrice(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).getMonth()).isEqualTo("Jan");
        }

        @Test
        void failure_returnsError() {
            when(repository.findMonthlyCategoryPrice(2024))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

            ApiResponse<List<CategoriesMonthPriceResponse>> result = service.findMonthPrice(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
            assertThat(result.data()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findYearPrice tests")
    class FindYearPriceTests {
        @Test
        void nullYear_returnsError() {
            ApiResponse<List<CategoriesYearPriceResponse>> result = service.findYearPrice(null).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void success() {
            when(repository.findYearlyCategoryPrice(2024))
                    .thenReturn(Uni.createFrom().item(List.of(new CategoryYearPrice("2024", 1, "Cat", 20, 100, 20000L, 5))));

            ApiResponse<List<CategoriesYearPriceResponse>> result = service.findYearPrice(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        void failure_returnsError() {
            when(repository.findYearlyCategoryPrice(2024))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

            ApiResponse<List<CategoriesYearPriceResponse>> result = service.findYearPrice(2024).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
            assertThat(result.data()).isEmpty();
        }
    }
}