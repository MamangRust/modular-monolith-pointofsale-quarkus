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

import com.sanedge.category.domain.requests.MonthTotalPriceCategory;
import com.sanedge.category.domain.requests.YearTotalPriceCategory;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.entity.CategoryMonthTotalPrice;
import com.sanedge.category.entity.CategoryYearTotalPrice;
import com.sanedge.category.repository.statsbyid.CategoryTotalPriceByIdRepository;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CategoryTotalPriceByIdServiceImplTest {

    @Mock
    private CategoryTotalPriceByIdRepository repository;

    @Mock
    private TracingMetrics tracingMetrics;

    private CategoryTotalPriceByIdServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryTotalPriceByIdServiceImpl(repository, tracingMetrics);
        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
    }

    @Nested
    @DisplayName("findMonthlyTotalPriceById tests")
    class FindMonthlyTotalPriceByIdTests {
        @Test
        void nullParams_returnsError() {
            MonthTotalPriceCategory req = new MonthTotalPriceCategory();
            req.setCategoryId(null);
            ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> result = service.findMonthlyTotalPriceById(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void success() {
            MonthTotalPriceCategory req = new MonthTotalPriceCategory();
            req.setCategoryId(1);
            req.setYear(2024);
            req.setMonth(6);

            when(repository.findMonthlyTotalPriceByCategoryId(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new CategoryMonthTotalPrice("2024", "Jun", 500000L))));

            ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> result = service.findMonthlyTotalPriceById(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).getMonth()).isEqualTo("Jun");
        }

        @Test
        void failure_returnsError() {
            MonthTotalPriceCategory req = new MonthTotalPriceCategory();
            req.setCategoryId(1);
            req.setYear(2024);
            req.setMonth(6);
            when(repository.findMonthlyTotalPriceByCategoryId(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

            ApiResponse<List<CategoriesMonthlyTotalPriceResponse>> result = service.findMonthlyTotalPriceById(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }

    @Nested
    @DisplayName("findYearlyTotalPriceById tests")
    class FindYearlyTotalPriceByIdTests {
        @Test
        void nullParams_returnsError() {
            YearTotalPriceCategory req = new YearTotalPriceCategory();
            req.setCategoryId(null);
            ApiResponse<List<CategoriesYearlyTotalPriceResponse>> result = service.findYearlyTotalPriceById(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }

        @Test
        void success() {
            YearTotalPriceCategory req = new YearTotalPriceCategory();
            req.setCategoryId(1);
            req.setYear(2024);

            when(repository.findYearlyTotalPriceByCategoryId(any()))
                    .thenReturn(Uni.createFrom().item(List.of(new CategoryYearTotalPrice("2024", 1000000L))));

            ApiResponse<List<CategoriesYearlyTotalPriceResponse>> result = service.findYearlyTotalPriceById(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        void failure_returnsError() {
            YearTotalPriceCategory req = new YearTotalPriceCategory();
            req.setCategoryId(1);
            req.setYear(2024);
            when(repository.findYearlyTotalPriceByCategoryId(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("fail")));

            ApiResponse<List<CategoriesYearlyTotalPriceResponse>> result = service.findYearlyTotalPriceById(req).await().indefinitely();
            assertThat(result.status()).isEqualTo("error");
        }
    }
}