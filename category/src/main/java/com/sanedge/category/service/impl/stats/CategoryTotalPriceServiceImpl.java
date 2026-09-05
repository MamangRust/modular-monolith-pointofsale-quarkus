package com.sanedge.category.service.impl.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.category.domain.requests.MonthTotalPrice;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.repository.stats.CategoryTotalPriceRepository;
import com.sanedge.category.service.stats.CategoryTotalPriceService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CategoryTotalPriceServiceImpl implements CategoryTotalPriceService {
        private static final Logger logger = LoggerFactory.getLogger(CategoryTotalPriceServiceImpl.class);

        private final CategoryTotalPriceRepository categoryTotalPriceRepository;
        private final TracingMetrics tracingMetrics;

        @Inject
        public CategoryTotalPriceServiceImpl(CategoryTotalPriceRepository categoryTotalPriceRepository,
                        TracingMetrics tracingMetrics) {
                this.categoryTotalPriceRepository = categoryTotalPriceRepository;
                this.tracingMetrics = tracingMetrics;
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPrice(MonthTotalPrice req) {
                if (req.getYear() == null || req.getMonth() == null) {
                        logger.error("Year or Month is null | req: {}", req);
                        return Uni.createFrom()
                                        .item(new ApiResponse<>("error", "Year and Month must not be null", List.of()));
                }

                logger.info("Fetching monthly total category price | Year: {}, Month: {}", req.getYear(),
                                req.getMonth());
                Attributes attrs = Attributes.builder()
                                .put("category.year", req.getYear().toString())
                                .put("category.month", req.getMonth().toString())
                                .build();

                LocalDate currentMonth = LocalDate.of(req.getYear(), req.getMonth(), 1);
                LocalDate nextMonth = currentMonth.plusMonths(1);

                return runTraced("findMonthlyTotalPrice", "find_monthly_total_price", attrs,
                                () -> {
                                        com.sanedge.category.domain.requests.FindCategoryMonthTotalPriceRange rangeReq = new com.sanedge.category.domain.requests.FindCategoryMonthTotalPriceRange();
                                        rangeReq.setStartYear(req.getYear());
                                        rangeReq.setStartMonth(req.getMonth());
                                        rangeReq.setEndYear(nextMonth.getYear());
                                        rangeReq.setEndMonth(nextMonth.getMonthValue());

                                        return categoryTotalPriceRepository.findMonthlyTotalPrice(rangeReq)
                                                .map(results -> {
                                                        List<CategoriesMonthlyTotalPriceResponse> response = results
                                                                        .stream()
                                                                        .map(CategoriesMonthlyTotalPriceResponse::from)
                                                                        .collect(Collectors.toList());

                                                        logger.info("Found {} monthly total price stats",
                                                                        response.size());
                                                        return ApiResponse.success(
                                                                        "Monthly total price stats retrieved successfully",
                                                                        response);
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        logger.error("Failed to fetch monthly total category price | Year: {}, Month: {}",
                                                                        req.getYear(), req.getMonth(), e);
                                                        return new ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>("error",
                                                                        "Failed to fetch monthly total category price",
                                                                        List.of());
                                                });
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPrice(Integer year) {
                if (year == null) {
                        logger.error("Year is null");
                        return Uni.createFrom().item(new ApiResponse<>("error", "Year must not be null", List.of()));
                }

                logger.info("Fetching yearly total category price | Year: {}", year);
                Attributes attrs = Attributes.builder()
                                .put("category.year", year.toString())
                                .build();

                return runTraced("findYearlyTotalPrice", "find_yearly_total_price", attrs,
                                () -> categoryTotalPriceRepository.findYearlyTotalPrice(year, year - 1)
                                                .map(results -> {
                                                        List<CategoriesYearlyTotalPriceResponse> response = results
                                                                        .stream()
                                                                        .map(CategoriesYearlyTotalPriceResponse::from)
                                                                        .collect(Collectors.toList());

                                                        logger.info("Found {} yearly total price stats",
                                                                        response.size());
                                                        return ApiResponse.success(
                                                                        "Yearly total price stats retrieved successfully",
                                                                        response);
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        logger.error("Failed to fetch yearly total category price | Year: {}",
                                                                        year, e);
                                                        return new ApiResponse<>("error",
                                                                        "Failed to fetch yearly total category price",
                                                                        List.of());
                                                }));
        }

        private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
                        java.util.function.Supplier<Uni<T>> supplier) {
                return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
        }
}