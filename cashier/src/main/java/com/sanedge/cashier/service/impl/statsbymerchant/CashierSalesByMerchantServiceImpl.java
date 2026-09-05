package com.sanedge.cashier.service.impl.statsbymerchant;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.cashier.domain.requests.MonthCashierMerchantRequest;
import com.sanedge.cashier.domain.requests.YearCashierMerchantRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.cashier.domain.response.CashierResponseMonthSales;
import com.sanedge.cashier.domain.response.CashierResponseYearSales;
import com.sanedge.cashier.repository.statsbymerchant.CashierSalesByMerchantRepository;
import com.sanedge.cashier.service.statsbymerchant.CashierSalesByMerchantService;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CashierSalesByMerchantServiceImpl implements CashierSalesByMerchantService {
        private static final Logger logger = LoggerFactory.getLogger(CashierSalesByMerchantServiceImpl.class);

        private final CashierSalesByMerchantRepository cashierSalesByMerchantRepository;
        private final TracingMetrics tracingMetrics;

        @Inject
        public CashierSalesByMerchantServiceImpl(CashierSalesByMerchantRepository cashierSalesByMerchantRepository,
                        TracingMetrics tracingMetrics) {
                this.cashierSalesByMerchantRepository = cashierSalesByMerchantRepository;
                this.tracingMetrics = tracingMetrics;
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CashierResponseMonthSales>>> findMonthlyCashierByMerchant(
                        MonthCashierMerchantRequest req) {
                Attributes attrs = Attributes.builder()
                                .put("merchant.id",
                                                req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                                .build();

                return runTraced("findMonthlyCashierByMerchant", "find_monthly_cashier_by_merchant", attrs, () -> {
                        if (req.getMerchantId() == null || req.getYear() == null) {
                                logger.error("MerchantId or Year is null | req: {}", req);
                                return Uni.createFrom().item(new ApiResponse<List<CashierResponseMonthSales>>("error",
                                                "MerchantId and Year must not be null", null));
                        }

                        logger.info("Fetching monthly cashier sales by merchant | MerchantID: {}, Year: {}",
                                        req.getMerchantId(), req.getYear());

                        LocalDate startMonth = LocalDate.of(req.getYear(), 1, 1);
                        LocalDate nextYear = startMonth.plusYears(1);

                        com.sanedge.cashier.domain.requests.FindCashierMonthSalesByMerchant rangeReq = new com.sanedge.cashier.domain.requests.FindCashierMonthSalesByMerchant();
                        rangeReq.setMerchantId(req.getMerchantId().longValue());
                        rangeReq.setYear(req.getYear());
                        rangeReq.setStartMonth(startMonth.getMonthValue());
                        rangeReq.setEndMonth(nextYear.getMonthValue());

                        return cashierSalesByMerchantRepository.findMonthSalesByMerchant(rangeReq)
                                        .map(results -> {
                                                List<CashierResponseMonthSales> response = results.stream()
                                                                .map(CashierResponseMonthSales::from)
                                                                .collect(Collectors.toList());

                                                logger.info("Found {} monthly cashier sales for merchant {}",
                                                                response.size(), req.getMerchantId());
                                                return ApiResponse.success(
                                                                "Monthly cashier sales by merchant retrieved successfully",
                                                                response);
                                        })
                                        .onFailure().recoverWithItem(e -> {
                                                logger.error("Failed to fetch monthly cashier sales by merchant | MerchantID: {}, Year: {}",
                                                                req.getMerchantId(), req.getYear(), e);
                                                return new ApiResponse<>("error", e.getMessage(), null);
                                        });
                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CashierResponseYearSales>>> findYearlyCashierByMerchant(
                        YearCashierMerchantRequest req) {
                Attributes attrs = Attributes.builder()
                                .put("merchant.id",
                                                req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                                .build();

                return runTraced("findYearlyCashierByMerchant", "find_yearly_cashier_by_merchant", attrs, () -> {
                        if (req.getMerchantId() == null || req.getYear() == null) {
                                logger.error("MerchantId or Year is null | req: {}", req);
                                return Uni.createFrom().item(new ApiResponse<List<CashierResponseYearSales>>("error",
                                                "MerchantId and Year must not be null", null));
                        }

                        logger.info("Fetching yearly cashier sales by merchant | MerchantID: {}, Year: {}",
                                        req.getMerchantId(), req.getYear());

                        return cashierSalesByMerchantRepository.findYearSalesByMerchant(
                                        req.getMerchantId().longValue(),
                                        req.getYear())
                                        .map(results -> {
                                                List<CashierResponseYearSales> response = results.stream()
                                                                .map(CashierResponseYearSales::from)
                                                                .collect(Collectors.toList());

                                                logger.info("Found {} yearly cashier sales for merchant {}",
                                                                response.size(), req.getMerchantId());
                                                return ApiResponse.success(
                                                                "Yearly cashier sales by merchant retrieved successfully",
                                                                response);
                                        })
                                        .onFailure().recoverWithItem(e -> {
                                                logger.error("Failed to fetch yearly cashier sales by merchant | MerchantID: {}, Year: {}",
                                                                req.getMerchantId(), req.getYear(), e);
                                                return new ApiResponse<>("error", e.getMessage(), null);
                                        });
                });
        }

        private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
                        Supplier<Uni<T>> supplier) {
                return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
        }
}