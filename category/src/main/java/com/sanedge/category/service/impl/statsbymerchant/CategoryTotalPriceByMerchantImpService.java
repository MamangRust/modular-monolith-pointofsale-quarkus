package com.sanedge.category.service.impl.statsbymerchant;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.category.domain.requests.FindCategoryMonthTotalPriceByMerchant;
import com.sanedge.category.domain.requests.FindCategoryYearTotalPriceByMerchant;
import com.sanedge.category.domain.requests.MonthTotalPriceMerchant;
import com.sanedge.category.domain.requests.YearTotalPriceMerchant;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.repository.statsbymerchant.CategoryTotalPriceByMerchantRepository;
import com.sanedge.category.service.statsbymerchant.CategoryTotalPriceByMerchantService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CategoryTotalPriceByMerchantImpService implements CategoryTotalPriceByMerchantService {

    private static final Logger log = LoggerFactory.getLogger(CategoryTotalPriceByMerchantImpService.class);

    private final CategoryTotalPriceByMerchantRepository categoryTotalPriceByMerchantRepository;
    private final TracingMetrics tracingMetrics;

    @Inject
    public CategoryTotalPriceByMerchantImpService(
            CategoryTotalPriceByMerchantRepository categoryTotalPriceByMerchantRepository,
            TracingMetrics tracingMetrics) {
        this.categoryTotalPriceByMerchantRepository = categoryTotalPriceByMerchantRepository;
        this.tracingMetrics = tracingMetrics;
    }

    @Override
    public Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPriceByMerchant(
            MonthTotalPriceMerchant req) {
        if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
            log.error("MerchantId, Year or Month is null | req: {}", req);
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "MerchantId, Year and Month must not be null", List.of()));
        }

        log.info("Fetching monthly total price by merchant | MerchantId: {}, Year: {}, Month: {}",
                req.getMerchantId(), req.getYear(), req.getMonth());
        Attributes attrs = Attributes.builder()
                .put("merchant.id", req.getMerchantId().toString())
                .put("category.year", req.getYear().toString())
                .put("category.month", req.getMonth().toString())
                .build();

        try {
            LocalDate currentMonth = LocalDate.of(req.getYear(), req.getMonth(), 1);
            LocalDate nextMonth = currentMonth.plusMonths(1);

            return runTraced("findMonthlyTotalPriceByMerchant", "find_monthly_total_price_by_merchant", attrs,
                    () -> {
                        FindCategoryMonthTotalPriceByMerchant dreq = new FindCategoryMonthTotalPriceByMerchant();
                        dreq.setMerchantId(req.getMerchantId().longValue());
                        dreq.setStartYear(req.getYear());
                        dreq.setStartMonth(req.getMonth());
                        dreq.setEndYear(nextMonth.getYear());
                        dreq.setEndMonth(nextMonth.getMonthValue());

                        return categoryTotalPriceByMerchantRepository.findMonthlyTotalPriceByMerchant(dreq)
                                .map(results -> {
                                    List<CategoriesMonthlyTotalPriceResponse> response = results.stream()
                                            .map(CategoriesMonthlyTotalPriceResponse::from)
                                            .collect(Collectors.toList());

                                    log.info("Found {} monthly total price stats for merchant {}", response.size(),
                                            req.getMerchantId());
                                    return new ApiResponse<>("success",
                                            "Monthly total price by merchant retrieved successfully", response);
                                })
                                .onFailure().recoverWithItem(e -> {
                                    log.error(
                                            "Failed to fetch monthly total price by merchant | MerchantId: {}, Year: {}, Month: {}",
                                            req.getMerchantId(), req.getYear(), req.getMonth(), e);
                                    return new ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>("error",
                                            "Unable to fetch monthly total price by merchant at the moment", List.of());
                                });
                    });
        } catch (Exception e) {
            log.error("Failed to parse dates | MerchantId: {}, Year: {}, Month: {}",
                    req.getMerchantId(), req.getYear(), req.getMonth(), e);
            return Uni.createFrom().item(new ApiResponse<>("error", "Invalid year or month format", List.of()));
        }
    }

    @Override
    public Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPriceByMerchant(
            YearTotalPriceMerchant req) {
        if (req.getMerchantId() == null || req.getYear() == null) {
            log.error("MerchantId or Year is null | req: {}", req);
            return Uni.createFrom().item(new ApiResponse<>("error", "MerchantId and Year must not be null", List.of()));
        }

        log.info("Fetching yearly total price by merchant | MerchantId: {}, Year: {}", req.getMerchantId(),
                req.getYear());
        Attributes attrs = Attributes.builder()
                .put("merchant.id", req.getMerchantId().toString())
                .put("category.year", req.getYear().toString())
                .build();

        return runTraced("findYearlyTotalPriceByMerchant", "find_yearly_total_price_by_merchant", attrs,
                () -> {
                    FindCategoryYearTotalPriceByMerchant dreq = new FindCategoryYearTotalPriceByMerchant();
                    dreq.setMerchantId(req.getMerchantId().longValue());
                    dreq.setYear(req.getYear());
                    dreq.setYearMinusOne(req.getYear() - 1);

                    return categoryTotalPriceByMerchantRepository.findYearlyTotalPriceByMerchant(dreq)
                            .map(results -> {
                                List<CategoriesYearlyTotalPriceResponse> response = results.stream()
                                        .map(CategoriesYearlyTotalPriceResponse::from)
                                        .collect(Collectors.toList());

                                log.info("Found {} yearly total price stats for merchant {}", response.size(),
                                        req.getMerchantId());
                                return new ApiResponse<>("success", "Yearly total price by merchant retrieved successfully",
                                        response);
                            })
                            .onFailure().recoverWithItem(e -> {
                                log.error("Failed to fetch yearly total price by merchant | MerchantId: {}, Year: {}",
                                        req.getMerchantId(), req.getYear(), e);
                                return new ApiResponse<List<CategoriesYearlyTotalPriceResponse>>("error",
                                        "Unable to fetch yearly total price by merchant at the moment", List.of());
                            });
                });
    }

    private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
            java.util.function.Supplier<Uni<T>> supplier) {
        return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
    }
}