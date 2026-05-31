package com.sanedge.category.service.impl.statsbymerchant;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.category.domain.requests.MonthTotalPriceMerchant;
import com.sanedge.category.domain.requests.YearTotalPriceMerchant;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.entity.CategoryMonthTotalPrice;
import com.sanedge.category.entity.CategoryYearTotalPrice;
import com.sanedge.category.repository.statsbymerchant.CategoryTotalPriceByMerchantRepository;
import com.sanedge.category.service.statsbymerchant.CategoryTotalPriceByMerchantService;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CategoryTotalPriceByMerchantImpService implements CategoryTotalPriceByMerchantService {

    private static final Logger log = LoggerFactory.getLogger(CategoryTotalPriceByMerchantImpService.class);

    @Inject
    CategoryTotalPriceByMerchantRepository categoryTotalPriceByMerchantRepository;

    @Override
    public Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPriceByMerchant(MonthTotalPriceMerchant req) {
        log.info("📊 Fetching monthly total price by merchant | MerchantId: {}, Year: {}, Month: {}",
                req.getMerchantId(), req.getYear(), req.getMonth());

        if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
            return Uni.createFrom().item(new ApiResponse<>("error", "MerchantId, Year and Month must not be null", List.of()));
        }

        try {
            LocalDate currentMonth = LocalDate.of(req.getYear(), req.getMonth(), 1);
            LocalDate nextMonth = currentMonth.plusMonths(1);

            return categoryTotalPriceByMerchantRepository.findMonthlyTotalPriceByMerchant(
                            req.getMerchantId().longValue(),
                            req.getYear(),
                            req.getMonth(),
                            nextMonth.getYear(),
                            nextMonth.getMonthValue())
                    .map(results -> {
                        List<CategoriesMonthlyTotalPriceResponse> response = results.stream()
                                .map(CategoriesMonthlyTotalPriceResponse::from)
                                .toList();

                        log.info("✅ Found {} monthly total price stats for merchant {}", response.size(), req.getMerchantId());

                        return new ApiResponse<>("success", "Monthly total price by merchant retrieved successfully", response);
                    })
                    .onFailure().recoverWithItem(e -> {
                        log.error("💥 Failed to fetch monthly total price by merchant | MerchantId: {}, Year: {}, Month: {}",
                                req.getMerchantId(), req.getYear(), req.getMonth(), e);

                        return new ApiResponse<>("error", "Unable to fetch monthly total price by merchant at the moment", List.of());
                    });
        } catch (Exception e) {
            log.error("💥 Failed to parse dates | MerchantId: {}, Year: {}, Month: {}",
                    req.getMerchantId(), req.getYear(), req.getMonth(), e);
            return Uni.createFrom().item(new ApiResponse<>("error", "Invalid year or month format", List.of()));
        }
    }

    @Override
    public Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPriceByMerchant(YearTotalPriceMerchant req) {
        log.info("📊 Fetching yearly total price by merchant | MerchantId: {}, Year: {}", req.getMerchantId(), req.getYear());

        if (req.getMerchantId() == null || req.getYear() == null) {
            return Uni.createFrom().item(new ApiResponse<>("error", "MerchantId and Year must not be null", List.of()));
        }

        return categoryTotalPriceByMerchantRepository.findYearlyTotalPriceByMerchant(req.getMerchantId().longValue(), req.getYear(), req.getYear() - 1)
                .map(results -> {
                    List<CategoriesYearlyTotalPriceResponse> response = results.stream()
                            .map(CategoriesYearlyTotalPriceResponse::from)
                            .toList();

                    log.info("✅ Found {} yearly total price stats for merchant {}", response.size(), req.getMerchantId());

                    return new ApiResponse<>("success", "Yearly total price by merchant retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    log.error("💥 Failed to fetch yearly total price by merchant | MerchantId: {}, Year: {}",
                            req.getMerchantId(), req.getYear(), e);

                    return new ApiResponse<>("error", "Unable to fetch yearly total price by merchant at the moment", List.of());
                });
    }
}
