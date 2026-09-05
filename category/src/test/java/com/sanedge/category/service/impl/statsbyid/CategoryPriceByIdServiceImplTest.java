package com.sanedge.category.service.impl.statsbyid;

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

import com.sanedge.category.domain.requests.MonthPriceId;
import com.sanedge.category.domain.requests.YearPriceId;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.entity.CategoryMonthPrice;
import com.sanedge.category.entity.CategoryYearPrice;
import com.sanedge.category.repository.statsbyid.CategoryPriceByIdRepository;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CategoryPriceByIdServiceImplTest {

    @Mock
    private CategoryPriceByIdRepository repository;

    @Mock
    private TracingMetrics tracingMetrics;

    private CategoryPriceByIdServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryPriceByIdServiceImpl(repository, tracingMetrics);
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
    }

    @Nested
    @DisplayName("findMonthPriceById tests")
    class FindMonthPriceByIdTests {
        @Test
        void nullParams_returnsError() {
            MonthPriceId req = new MonthPriceId();
            req.setCategoryId(null);
            ApiResponse<List<CategoriesMonthPriceResponse>> result = service.findMonthPriceById(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void success() {
            MonthPriceId req = new MonthPriceId();
            req.setCategoryId(1);
            req.setYear(2024);

            when(repository.findMonthlyCategoryPriceById(anyLong(), any()))
                    .thenReturn(Uni.createFrom().item(List.of(new CategoryMonthPrice("Feb", 1, "Cat", 5, 20, 3000L))));

            ApiResponse<List<CategoriesMonthPriceResponse>> result = service.findMonthPriceById(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        void failure_returnsError() {
            MonthPriceId req = new MonthPriceId();
            req.setCategoryId(1);
            req.setYear(2024);
            when(repository.findMonthlyCategoryPriceById(anyLong(), any()))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

            ApiResponse<List<CategoriesMonthPriceResponse>> result = service.findMonthPriceById(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }

    @Nested
    @DisplayName("findYearPriceById tests")
    class FindYearPriceByIdTests {
        @Test
        void nullParams_returnsError() {
            YearPriceId req = new YearPriceId();
            req.setCategoryId(null);
            ApiResponse<List<CategoriesYearPriceResponse>> result = service.findYearPriceById(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void success() {
            YearPriceId req = new YearPriceId();
            req.setCategoryId(1);
            req.setYear(2024);

            when(repository.findYearlyCategoryPriceById(anyLong(), any()))
                    .thenReturn(Uni.createFrom().item(List.of(new CategoryYearPrice("2024", 1, "Cat", 10, 50, 10000L, 3))));

            ApiResponse<List<CategoriesYearPriceResponse>> result = service.findYearPriceById(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        void failure_returnsError() {
            YearPriceId req = new YearPriceId();
            req.setCategoryId(1);
            req.setYear(2024);
            when(repository.findYearlyCategoryPriceById(anyLong(), any()))
                    .thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

            ApiResponse<List<CategoriesYearPriceResponse>> result = service.findYearPriceById(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }
}