package com.sanedge.cashier.service.impl.statsbymerchant;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.cashier.domain.requests.MonthTotalSalesMerchant;
import com.sanedge.cashier.domain.requests.YearTotalSalesMerchant;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.cashier.domain.response.CashierResponseMonthTotalSales;
import com.sanedge.cashier.domain.response.CashierResponseYearTotalSales;
import com.sanedge.cashier.repository.statsbymerchant.CashierTotalSalesByMerchantRepository;
import com.sanedge.cashier.service.statsbymerchant.CashierTotalSalesByMerchantService;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CashierTotalSalesByMerchantServiceImpl implements CashierTotalSalesByMerchantService {
        private static final Logger logger = LoggerFactory.getLogger(CashierTotalSalesByMerchantServiceImpl.class);

        private final CashierTotalSalesByMerchantRepository cashierTotalSalesByMerchantRepository;
        private final TracingMetrics tracingMetrics;

        @Inject
        public CashierTotalSalesByMerchantServiceImpl(
                        CashierTotalSalesByMerchantRepository cashierTotalSalesByMerchantRepository,
                        TracingMetrics tracingMetrics) {
                this.cashierTotalSalesByMerchantRepository = cashierTotalSalesByMerchantRepository;
                this.tracingMetrics = tracingMetrics;
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CashierResponseMonthTotalSales>>> findMonthlyTotalSalesByMerchant(
                        MonthTotalSalesMerchant req) {
                Attributes attrs = Attributes.builder()
                                .put("merchant.id",
                                                req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                                .build();

                return runTraced("findMonthlyTotalSalesByMerchant", "find_monthly_total_sales_by_merchant", attrs,
                                () -> {
                                        if (req.getMerchantId() == null || req.getYear() == null
                                                        || req.getMonth() == null) {
                                                logger.error("MerchantId, Year, or Month is null | req: {}", req);
                                                return Uni.createFrom().item(new ApiResponse<List<CashierResponseMonthTotalSales>>("error",
                                                                "MerchantId, Year, and Month must not be null", null));
                                        }

                                        logger.info("Fetching monthly total cashier sales by merchant | MerchantID: {}, Year: {}, Month: {}",
                                                        req.getMerchantId(), req.getYear(), req.getMonth());

                                        LocalDate currentMonth = LocalDate.of(req.getYear(), req.getMonth(), 1);
                                        LocalDate nextMonth = currentMonth.plusMonths(1);

                                        com.sanedge.cashier.domain.requests.FindCashierMonthTotalSalesByMerchant rangeReq = new com.sanedge.cashier.domain.requests.FindCashierMonthTotalSalesByMerchant();
                                        rangeReq.setMerchantId(req.getMerchantId().longValue());
                                        rangeReq.setStartYear(req.getYear());
                                        rangeReq.setStartMonth(req.getMonth());
                                        rangeReq.setEndYear(nextMonth.getYear());
                                        rangeReq.setEndMonth(nextMonth.getMonthValue());

                                        return cashierTotalSalesByMerchantRepository.findMonthTotalSalesByMerchant(rangeReq)
                                                        .map(results -> {
                                                                List<CashierResponseMonthTotalSales> response = results
                                                                                .stream()
                                                                                .map(CashierResponseMonthTotalSales::from)
                                                                                .collect(Collectors.toList());

                                                                logger.info("Found {} monthly total cashier sales for merchant {}",
                                                                                response.size(), req.getMerchantId());
                                                                return ApiResponse.success(
                                                                                "Monthly total cashier sales by merchant retrieved successfully",
                                                                                response);
                                                        })
                                                        .onFailure().recoverWithItem(e -> {
                                                                logger.error("Failed to fetch monthly total cashier sales by merchant | MerchantID: {}, Year: {}, Month: {}",
                                                                                req.getMerchantId(), req.getYear(),
                                                                                req.getMonth(), e);
                                                                return new ApiResponse<>("error", e.getMessage(), null);
                                                        });
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CashierResponseYearTotalSales>>> findYearlyTotalSalesByMerchant(
                        YearTotalSalesMerchant req) {
                Attributes attrs = Attributes.builder()
                                .put("merchant.id",
                                                req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                                .build();

                return runTraced("findYearlyTotalSalesByMerchant", "find_yearly_total_sales_by_merchant", attrs, () -> {
                        if (req.getMerchantId() == null || req.getYear() == null) {
                                logger.error("MerchantId or Year is null | req: {}", req);
                                return Uni.createFrom().item(new ApiResponse<List<CashierResponseYearTotalSales>>("error",
                                                "MerchantId and Year must not be null", null));
                        }

                        logger.info("Fetching yearly total cashier sales by merchant | MerchantID: {}, Year: {}",
                                        req.getMerchantId(), req.getYear());

                        com.sanedge.cashier.domain.requests.FindCashierYearTotalSalesByMerchant yearReq = new com.sanedge.cashier.domain.requests.FindCashierYearTotalSalesByMerchant();
                        yearReq.setMerchantId(req.getMerchantId().longValue());
                        yearReq.setYear(req.getYear());
                        yearReq.setYearMinusOne(req.getYear() - 1);

                        return cashierTotalSalesByMerchantRepository.findYearTotalSalesByMerchant(yearReq)
                                        .map(results -> {
                                                List<CashierResponseYearTotalSales> response = results.stream()
                                                                .map(CashierResponseYearTotalSales::from)
                                                                .collect(Collectors.toList());

                                                logger.info("Found {} yearly total cashier sales for merchant {}",
                                                                response.size(), req.getMerchantId());
                                                return ApiResponse.success(
                                                                "Yearly total cashier sales by merchant retrieved successfully",
                                                                response);
                                        })
                                        .onFailure().recoverWithItem(e -> {
                                                logger.error("Failed to fetch yearly total cashier sales by merchant | MerchantID: {}, Year: {}, Month: {}",
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