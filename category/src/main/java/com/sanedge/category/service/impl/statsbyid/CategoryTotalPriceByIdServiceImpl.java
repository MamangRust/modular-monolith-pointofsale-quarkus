package com.sanedge.category.service.impl.statsbyid;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.category.domain.requests.FindCategoryMonthTotalPriceById;
import com.sanedge.category.domain.requests.FindCategoryYearTotalPriceById;
import com.sanedge.category.domain.requests.MonthTotalPriceCategory;
import com.sanedge.category.domain.requests.YearTotalPriceCategory;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.repository.statsbyid.CategoryTotalPriceByIdRepository;
import com.sanedge.category.service.statsbyid.CategoryTotalPriceByIdService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CategoryTotalPriceByIdServiceImpl implements CategoryTotalPriceByIdService {
        private static final Logger logger = LoggerFactory.getLogger(CategoryTotalPriceByIdServiceImpl.class);

        private final CategoryTotalPriceByIdRepository categoryTotalPriceByIdRepository;
        private final TracingMetrics tracingMetrics;

        @Inject
        public CategoryTotalPriceByIdServiceImpl(CategoryTotalPriceByIdRepository categoryTotalPriceByIdRepository,
                        TracingMetrics tracingMetrics) {
                this.categoryTotalPriceByIdRepository = categoryTotalPriceByIdRepository;
                this.tracingMetrics = tracingMetrics;
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPriceById(
                        MonthTotalPriceCategory req) {
                if (req.getCategoryId() == null || req.getYear() == null || req.getMonth() == null) {
                        logger.error("CategoryId, Year or Month is null | req: {}", req);
                        return Uni.createFrom().item(new ApiResponse<>("error",
                                        "CategoryId, Year and Month must not be null", List.of()));
                }

                logger.info("Fetching monthly total price by category | CategoryId: {}, Year: {}, Month: {}",
                                req.getCategoryId(), req.getYear(), req.getMonth());
                Attributes attrs = Attributes.builder()
                                .put("category.id", req.getCategoryId().toString())
                                .put("category.year", req.getYear().toString())
                                .put("category.month", req.getMonth().toString())
                                .build();

                LocalDate currentMonth = LocalDate.of(req.getYear(), req.getMonth(), 1);
                LocalDate nextMonth = currentMonth.plusMonths(1);

                return runTraced("findMonthlyTotalPriceById", "find_monthly_total_price_by_id", attrs,
                                () -> {
                                        FindCategoryMonthTotalPriceById dreq = new FindCategoryMonthTotalPriceById();
                                        dreq.setCategoryId(req.getCategoryId().longValue());
                                        dreq.setStartYear(req.getYear());
                                        dreq.setStartMonth(req.getMonth());
                                        dreq.setEndYear(nextMonth.getYear());
                                        dreq.setEndMonth(nextMonth.getMonthValue());

                                        return categoryTotalPriceByIdRepository.findMonthlyTotalPriceByCategoryId(dreq)
                                                .map(results -> {
                                                        List<CategoriesMonthlyTotalPriceResponse> response = results
                                                                        .stream()
                                                                        .map(CategoriesMonthlyTotalPriceResponse::from)
                                                                        .collect(Collectors.toList());

                                                        logger.info("Found {} monthly total price stats for categoryId {}",
                                                                        response.size(), req.getCategoryId());
                                                        return ApiResponse.success(
                                                                        "Monthly total price by category retrieved successfully",
                                                                        response);
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        logger.error("Failed to fetch monthly total price by category | CategoryId: {}, Year: {}, Month: {}",
                                                                        req.getCategoryId(), req.getYear(),
                                                                        req.getMonth(), e);
                                                        return new ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>("error",
                                                                        "Failed to fetch monthly total price by category",
                                                                        List.of());
                                                });
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPriceById(
                        YearTotalPriceCategory req) {
                if (req.getCategoryId() == null || req.getYear() == null) {
                        logger.error("CategoryId or Year is null | req: {}", req);
                        return Uni.createFrom().item(
                                        new ApiResponse<>("error", "CategoryId and Year must not be null", List.of()));
                }

                logger.info("Fetching yearly total price by category | CategoryId: {}, Year: {}", req.getCategoryId(),
                                req.getYear());
                Attributes attrs = Attributes.builder()
                                .put("category.id", req.getCategoryId().toString())
                                .put("category.year", req.getYear().toString())
                                .build();

                return runTraced("findYearlyTotalPriceById", "find_yearly_total_price_by_id", attrs,
                                () -> {
                                        FindCategoryYearTotalPriceById dreq = new FindCategoryYearTotalPriceById();
                                        dreq.setCategoryId(req.getCategoryId().longValue());
                                        dreq.setYear(req.getYear());
                                        dreq.setYearMinusOne(req.getYear() - 1);

                                        return categoryTotalPriceByIdRepository.findYearlyTotalPriceByCategoryId(dreq)
                                                .map(results -> {
                                                        List<CategoriesYearlyTotalPriceResponse> response = results
                                                                        .stream()
                                                                        .map(CategoriesYearlyTotalPriceResponse::from)
                                                                        .collect(Collectors.toList());

                                                        logger.info("Found {} yearly total price stats for categoryId {}",
                                                                        response.size(), req.getCategoryId());
                                                        return ApiResponse.success(
                                                                        "Yearly total price by category retrieved successfully",
                                                                        response);
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        logger.error("Failed to fetch yearly total price by category | CategoryId: {}, Year: {}",
                                                                        req.getCategoryId(), req.getYear(), e);
                                                        return new ApiResponse<List<CategoriesYearlyTotalPriceResponse>>("error",
                                                                        "Failed to fetch yearly total price by category",
                                                                        List.of());
                                                });
                                });
        }

        private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
                        java.util.function.Supplier<Uni<T>> supplier) {
                return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
        }
}