package com.sanedge.category.service.impl.statsbyid;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.category.domain.requests.MonthPriceId;
import com.sanedge.category.domain.requests.YearPriceId;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.repository.statsbyid.CategoryPriceByIdRepository;
import com.sanedge.category.service.statsbyid.CategoryPriceByIdService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CategoryPriceByIdServiceImpl implements CategoryPriceByIdService {
        private static final Logger logger = LoggerFactory.getLogger(CategoryPriceByIdServiceImpl.class);

        private final CategoryPriceByIdRepository categoryPriceByIdRepository;
        private final TracingMetrics tracingMetrics;

        @Inject
        public CategoryPriceByIdServiceImpl(CategoryPriceByIdRepository categoryPriceByIdRepository,
                        TracingMetrics tracingMetrics) {
                this.categoryPriceByIdRepository = categoryPriceByIdRepository;
                this.tracingMetrics = tracingMetrics;
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceById(MonthPriceId req) {
                if (req.getCategoryId() == null || req.getYear() == null) {
                        logger.error("CategoryId or Year is null | req: {}", req);
                        return Uni.createFrom().item(
                                        new ApiResponse<>("error", "CategoryId and Year must not be null", List.of()));
                }

                logger.info("Fetching monthly price by category | CategoryId: {}, Year: {}", req.getCategoryId(),
                                req.getYear());
                Attributes attrs = Attributes.builder()
                                .put("category.id", req.getCategoryId().toString())
                                .put("category.year", req.getYear().toString())
                                .build();

                return runTraced("findMonthPriceById", "find_month_price_by_id", attrs,
                                () -> categoryPriceByIdRepository
                                                .findMonthlyCategoryPriceById(req.getCategoryId().longValue(),
                                                                req.getYear())
                                                .map(results -> {
                                                        List<CategoriesMonthPriceResponse> response = results.stream()
                                                                        .map(CategoriesMonthPriceResponse::from)
                                                                        .collect(Collectors.toList());

                                                        logger.info("Found {} monthly price stats for categoryId {}",
                                                                        response.size(), req.getCategoryId());
                                                        return ApiResponse.success(
                                                                        "Monthly category price stats retrieved successfully",
                                                                        response);
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        logger.error("Failed to fetch monthly price by category | CategoryId: {}, Year: {}",
                                                                        req.getCategoryId(), req.getYear(), e);
                                                        return new ApiResponse<>("error",
                                                                        "Failed to fetch monthly price by category",
                                                                        List.of());
                                                }));
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceById(YearPriceId req) {
                if (req.getCategoryId() == null || req.getYear() == null) {
                        logger.error("CategoryId or Year is null | req: {}", req);
                        return Uni.createFrom().item(
                                        new ApiResponse<>("error", "CategoryId and Year must not be null", List.of()));
                }

                logger.info("Fetching yearly price by category | CategoryId: {}, Year: {}", req.getCategoryId(),
                                req.getYear());
                Attributes attrs = Attributes.builder()
                                .put("category.id", req.getCategoryId().toString())
                                .put("category.year", req.getYear().toString())
                                .build();

                return runTraced("findYearPriceById", "find_year_price_by_id", attrs,
                                () -> categoryPriceByIdRepository
                                                .findYearlyCategoryPriceById(req.getCategoryId().longValue(),
                                                                req.getYear())
                                                .map(results -> {
                                                        List<CategoriesYearPriceResponse> response = results.stream()
                                                                        .map(CategoriesYearPriceResponse::from)
                                                                        .collect(Collectors.toList());

                                                        logger.info("Found {} yearly price stats for categoryId {}",
                                                                        response.size(), req.getCategoryId());
                                                        return ApiResponse.success(
                                                                        "Yearly category price stats retrieved successfully",
                                                                        response);
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        logger.error("Failed to fetch yearly price by category | CategoryId: {}, Year: {}",
                                                                        req.getCategoryId(), req.getYear(), e);
                                                        return new ApiResponse<>("error",
                                                                        "Failed to fetch yearly price by category",
                                                                        List.of());
                                                }));
        }

        private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
                        java.util.function.Supplier<Uni<T>> supplier) {
                return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
        }
}