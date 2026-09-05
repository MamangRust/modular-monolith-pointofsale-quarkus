package com.sanedge.category.service.impl.stats;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.repository.stats.CategoryPriceRepository;
import com.sanedge.category.service.stats.CategoryPriceService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CategoryPriceServiceImpl implements CategoryPriceService {
        private static final Logger logger = LoggerFactory.getLogger(CategoryPriceServiceImpl.class);

        private final CategoryPriceRepository categoryPriceRepository;
        private final TracingMetrics tracingMetrics;

        @Inject
        public CategoryPriceServiceImpl(CategoryPriceRepository categoryPriceRepository,
                        TracingMetrics tracingMetrics) {
                this.categoryPriceRepository = categoryPriceRepository;
                this.tracingMetrics = tracingMetrics;
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPrice(Integer year) {
                if (year == null) {
                        logger.error("Year is null");
                        return Uni.createFrom().item(new ApiResponse<>("error", "Year must not be null", List.of()));
                }

                logger.info("Fetching monthly category price stats | Year: {}", year);
                Attributes attrs = Attributes.builder()
                                .put("category.year", year.toString())
                                .build();

                return runTraced("findMonthPrice", "find_month_price", attrs,
                                () -> categoryPriceRepository.findMonthlyCategoryPrice(year)
                                                .map(results -> {
                                                        List<CategoriesMonthPriceResponse> response = results.stream()
                                                                        .map(CategoriesMonthPriceResponse::from)
                                                                        .collect(Collectors.toList());

                                                        logger.info("Found {} monthly category price stats",
                                                                        response.size());
                                                        return ApiResponse.success(
                                                                        "Monthly category price stats retrieved successfully",
                                                                        response);
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        logger.error("Failed to fetch monthly category price stats | Year: {}",
                                                                        year, e);
                                                        return new ApiResponse<>("error",
                                                                        "Failed to fetch monthly category price stats",
                                                                        List.of());
                                                }));
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPrice(Integer year) {
                if (year == null) {
                        logger.error("Year is null");
                        return Uni.createFrom().item(new ApiResponse<>("error", "Year must not be null", List.of()));
                }

                logger.info("Fetching yearly category price stats | Year: {}", year);
                Attributes attrs = Attributes.builder()
                                .put("category.year", year.toString())
                                .build();

                return runTraced("findYearPrice", "find_year_price", attrs,
                                () -> categoryPriceRepository.findYearlyCategoryPrice(year)
                                                .map(results -> {
                                                        List<CategoriesYearPriceResponse> response = results.stream()
                                                                        .map(CategoriesYearPriceResponse::from)
                                                                        .collect(Collectors.toList());

                                                        logger.info("Found {} yearly category price stats",
                                                                        response.size());
                                                        return ApiResponse.success(
                                                                        "Yearly category price stats retrieved successfully",
                                                                        response);
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        logger.error("Failed to fetch yearly category price stats | Year: {}",
                                                                        year, e);
                                                        return new ApiResponse<>("error",
                                                                        "Failed to fetch yearly category price stats",
                                                                        List.of());
                                                }));
        }

        private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
                        java.util.function.Supplier<Uni<T>> supplier) {
                return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
        }
}