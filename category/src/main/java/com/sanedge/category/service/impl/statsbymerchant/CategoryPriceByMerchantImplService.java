package com.sanedge.category.service.impl.statsbymerchant;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.category.domain.requests.MonthPriceMerchant;
import com.sanedge.category.domain.requests.YearPriceMerchant;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.repository.statsbymerchant.CategoryPriceByMerchantRepository;
import com.sanedge.category.service.statsbymerchant.CategoryPriceByMerchantService;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CategoryPriceByMerchantImplService implements CategoryPriceByMerchantService {

        private static final Logger log = LoggerFactory.getLogger(CategoryPriceByMerchantImplService.class);

        @Inject
        CategoryPriceByMerchantRepository categoryPriceByMerchantRepository;

        @Override
        public Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceByMerchant(MonthPriceMerchant req) {
                log.info("📊 Fetching monthly price by merchant | MerchantId: {}, Year: {}", req.getMerchantId(),
                                req.getYear());

                if (req.getMerchantId() == null || req.getYear() == null) {
                        return Uni.createFrom().item(
                                        new ApiResponse<>("error", "MerchantId and Year must not be null", List.of()));
                }

                return categoryPriceByMerchantRepository
                                .findMonthlyCategoryPriceByMerchant(req.getMerchantId().longValue(), req.getYear())
                                .map(results -> {
                                         List<CategoriesMonthPriceResponse> response = results.stream()
                                                         .map(CategoriesMonthPriceResponse::from)
                                                         .toList();

                                         log.info("✅ Found {} monthly price stats for merchant {}", response.size(),
                                                         req.getMerchantId());

                                         return new ApiResponse<>("success",
                                                         "Monthly price by merchant retrieved successfully", response);
                                 })
                                .onFailure().recoverWithItem(e -> {
                                         log.error("💥 Error fetching monthly price by merchant | MerchantId: {}, Year: {}",
                                                         req.getMerchantId(), req.getYear(), e);

                                         return new ApiResponse<>("error",
                                                         "Unable to fetch monthly price data at the moment", List.of());
                                 });
        }

        @Override
        public Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceByMerchant(YearPriceMerchant req) {
                log.info("📊 Fetching yearly price by merchant | MerchantId: {}, Year: {}", req.getMerchantId(),
                                req.getYear());

                if (req.getMerchantId() == null || req.getYear() == null) {
                        return Uni.createFrom().item(
                                        new ApiResponse<>("error", "MerchantId and Year must not be null", List.of()));
                }

                return categoryPriceByMerchantRepository
                                .findYearlyCategoryPriceByMerchant(req.getMerchantId().longValue(), req.getYear())
                                .map(results -> {
                                         List<CategoriesYearPriceResponse> response = results.stream()
                                                         .map(CategoriesYearPriceResponse::from)
                                                         .toList();

                                         log.info("✅ Found {} yearly price stats for merchant {}", response.size(),
                                                         req.getMerchantId());

                                         return new ApiResponse<>("success",
                                                         "Yearly price by merchant retrieved successfully", response);
                                 })
                                .onFailure().recoverWithItem(e -> {
                                         log.error("💥 Error fetching yearly price by merchant | MerchantId: {}, Year: {}",
                                                         req.getMerchantId(), req.getYear(), e);

                                         return new ApiResponse<>("error",
                                                         "Unable to fetch yearly price data at the moment", List.of());
                                 });
        }
}
