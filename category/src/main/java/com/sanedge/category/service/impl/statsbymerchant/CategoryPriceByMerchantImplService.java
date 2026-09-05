package com.sanedge.category.service.impl.statsbymerchant;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.category.domain.requests.MonthPriceMerchant;
import com.sanedge.category.domain.requests.YearPriceMerchant;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.repository.statsbymerchant.CategoryPriceByMerchantRepository;
import com.sanedge.category.service.statsbymerchant.CategoryPriceByMerchantService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;


@ApplicationScoped
public class CategoryPriceByMerchantImplService implements CategoryPriceByMerchantService {

        private static final Logger log = LoggerFactory.getLogger(CategoryPriceByMerchantImplService.class);

        private final CategoryPriceByMerchantRepository categoryPriceByMerchantRepository;
        private final TracingMetrics tracingMetrics;

        @Inject
        public CategoryPriceByMerchantImplService(CategoryPriceByMerchantRepository categoryPriceByMerchantRepository,
                        TracingMetrics tracingMetrics) {
                this.categoryPriceByMerchantRepository = categoryPriceByMerchantRepository;
                this.tracingMetrics = tracingMetrics;
        }

        @Override
        @WithTransaction

        public Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceByMerchant(MonthPriceMerchant req) {
                if (req.getMerchantId() == null || req.getYear() == null) {
                        log.error("MerchantId or Year is null | req: {}", req);
                        return Uni.createFrom().item(
                                        new ApiResponse<>("error", "MerchantId and Year must not be null", List.of()));
                }

                log.info("Fetching monthly price by merchant | MerchantId: {}, Year: {}", req.getMerchantId(),
                                req.getYear());
                Attributes attrs = Attributes.builder()
                                .put("merchant.id", req.getMerchantId().toString())
                                .put("category.year", req.getYear().toString())
                                .build();

                return runTraced("findMonthPriceByMerchant", "find_month_price_by_merchant", attrs,
                                () -> categoryPriceByMerchantRepository
                                                .findMonthlyCategoryPriceByMerchant(req.getMerchantId().longValue(),
                                                                req.getYear())
                                                .map(results -> {
                                                        List<CategoriesMonthPriceResponse> response = results.stream()
                                                                        .map(CategoriesMonthPriceResponse::from)
                                                                        .collect(Collectors.toList());

                                                        log.info("Found {} monthly price stats for merchant {}",
                                                                        response.size(), req.getMerchantId());
                                                        return new ApiResponse<>("success",
                                                                        "Monthly price by merchant retrieved successfully",
                                                                        response);
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        log.error("Error fetching monthly price by merchant | MerchantId: {}, Year: {}",
                                                                        req.getMerchantId(), req.getYear(), e);
                                                        return new ApiResponse<>("error",
                                                                        "Unable to fetch monthly price data at the moment",
                                                                        List.of());
                                                }));
        }

        @Override
        @WithTransaction

        public Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceByMerchant(YearPriceMerchant req) {
                if (req.getMerchantId() == null || req.getYear() == null) {
                        log.error("MerchantId or Year is null | req: {}", req);
                        return Uni.createFrom().item(
                                        new ApiResponse<>("error", "MerchantId and Year must not be null", List.of()));
                }

                log.info("Fetching yearly price by merchant | MerchantId: {}, Year: {}", req.getMerchantId(),
                                req.getYear());
                Attributes attrs = Attributes.builder()
                                .put("merchant.id", req.getMerchantId().toString())
                                .put("category.year", req.getYear().toString())
                                .build();

                return runTraced("findYearPriceByMerchant", "find_year_price_by_merchant", attrs,
                                () -> categoryPriceByMerchantRepository
                                                .findYearlyCategoryPriceByMerchant(req.getMerchantId().longValue(),
                                                                req.getYear())
                                                .map(results -> {
                                                        List<CategoriesYearPriceResponse> response = results.stream()
                                                                        .map(CategoriesYearPriceResponse::from)
                                                                        .collect(Collectors.toList());

                                                        log.info("Found {} yearly price stats for merchant {}",
                                                                        response.size(), req.getMerchantId());
                                                        return new ApiResponse<>("success",
                                                                        "Yearly price by merchant retrieved successfully",
                                                                        response);
                                                })
                                                .onFailure().recoverWithItem(e -> {
                                                        log.error("Error fetching yearly price by merchant | MerchantId: {}, Year: {}",
                                                                        req.getMerchantId(), req.getYear(), e);
                                                        return new ApiResponse<>("error",
                                                                        "Unable to fetch yearly price data at the moment",
                                                                        List.of());
                                                }));
        }

        private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
                        java.util.function.Supplier<Uni<T>> supplier) {
                return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
        }
}