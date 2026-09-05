package com.sanedge.cashier.service.impl.statsbyid;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.cashier.domain.requests.MonthTotalSalesCashier;
import com.sanedge.cashier.domain.requests.YearTotalSalesCashier;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.cashier.domain.response.CashierResponseMonthTotalSales;
import com.sanedge.cashier.domain.response.CashierResponseYearTotalSales;
import com.sanedge.cashier.repository.statsbyid.CashierTotalSalesByIdRepository;
import com.sanedge.cashier.service.statsbyid.CashierTotalSalesByIdService;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CashierTotalSalesByIdServiceImpl implements CashierTotalSalesByIdService {
        private static final Logger logger = LoggerFactory.getLogger(CashierTotalSalesByIdServiceImpl.class);

        private final CashierTotalSalesByIdRepository cashierTotalSalesByIdRepository;
        private final TracingMetrics tracingMetrics;

        @Inject
        public CashierTotalSalesByIdServiceImpl(CashierTotalSalesByIdRepository cashierTotalSalesByIdRepository,
                        TracingMetrics tracingMetrics) {
                this.cashierTotalSalesByIdRepository = cashierTotalSalesByIdRepository;
                this.tracingMetrics = tracingMetrics;
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CashierResponseMonthTotalSales>>> findMonthlyTotalSalesById(
                        MonthTotalSalesCashier req) {
                Attributes attrs = Attributes.builder()
                                .put("cashier.id", req.getCashierId() != null ? req.getCashierId().toString() : "null")
                                .build();

                return runTraced("findMonthlyTotalSalesById", "find_monthly_total_sales_by_id", attrs, () -> {
                        if (req.getCashierId() == null || req.getYear() == null || req.getMonth() == null) {
                                logger.error("CashierId, Year, or Month is null | req: {}", req);
                                return Uni.createFrom().item(new ApiResponse<List<CashierResponseMonthTotalSales>>("error",
                                                "CashierId, Year, and Month must not be null", null));
                        }

                        logger.info("Fetching monthly total cashier sales by cashierId | CashierID: {}, Year: {}, Month: {}",
                                        req.getCashierId(), req.getYear(), req.getMonth());

                        LocalDate currentMonth = LocalDate.of(req.getYear(), req.getMonth(), 1);
                        LocalDate nextMonth = currentMonth.plusMonths(1);

                        com.sanedge.cashier.domain.requests.FindCashierMonthTotalSalesById rangeReq = new com.sanedge.cashier.domain.requests.FindCashierMonthTotalSalesById();
                        rangeReq.setCashierId(req.getCashierId().longValue());
                        rangeReq.setStartYear(req.getYear());
                        rangeReq.setStartMonth(req.getMonth());
                        rangeReq.setEndYear(nextMonth.getYear());
                        rangeReq.setEndMonth(nextMonth.getMonthValue());

                        return cashierTotalSalesByIdRepository.findMonthTotalSalesById(rangeReq)
                                        .map(results -> {
                                                List<CashierResponseMonthTotalSales> response = results.stream()
                                                                .map(CashierResponseMonthTotalSales::from)
                                                                .collect(Collectors.toList());

                                                logger.info("Found {} monthly total cashier sales for cashier {}",
                                                                response.size(), req.getCashierId());
                                                return ApiResponse.success(
                                                                "Monthly total cashier sales by cashier retrieved successfully",
                                                                response);
                                        })
                                        .onFailure().recoverWithItem(e -> {
                                                logger.error("Failed to fetch monthly total cashier sales by cashier | CashierID: {}, Year: {}, Month: {}",
                                                                req.getCashierId(), req.getYear(), req.getMonth(), e);
                                                return new ApiResponse<>("error", e.getMessage(), null);
                                        });
                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CashierResponseYearTotalSales>>> findYearlyTotalSalesById(
                        YearTotalSalesCashier req) {
                Attributes attrs = Attributes.builder()
                                .put("cashier.id", req.getCashierId() != null ? req.getCashierId().toString() : "null")
                                .build();

                return runTraced("findYearlyTotalSalesById", "find_yearly_total_sales_by_id", attrs, () -> {
                        if (req.getCashierId() == null || req.getYear() == null) {
                                logger.error("CashierId or Year is null | req: {}", req);
                                return Uni.createFrom().item(new ApiResponse<List<CashierResponseYearTotalSales>>("error",
                                                "CashierId and Year must not be null", null));
                        }

                        logger.info("Fetching yearly total cashier sales by cashierId | CashierID: {}, Year: {}",
                                        req.getCashierId(), req.getYear());

                        com.sanedge.cashier.domain.requests.FindCashierYearTotalSalesById yearReq = new com.sanedge.cashier.domain.requests.FindCashierYearTotalSalesById();
                        yearReq.setCashierId(req.getCashierId().longValue());
                        yearReq.setYear(req.getYear());
                        yearReq.setYearMinusOne(req.getYear() - 1);

                        return cashierTotalSalesByIdRepository.findYearTotalSalesById(yearReq)
                                        .map(results -> {
                                                List<CashierResponseYearTotalSales> response = results.stream()
                                                                .map(CashierResponseYearTotalSales::from)
                                                                .collect(Collectors.toList());

                                                logger.info("Found {} yearly total cashier sales for cashier {}",
                                                                response.size(), req.getCashierId());
                                                return ApiResponse.success(
                                                                "Yearly total cashier sales by cashier retrieved successfully",
                                                                response);
                                        })
                                        .onFailure().recoverWithItem(e -> {
                                                logger.error("Failed to fetch yearly total cashier sales by cashier | CashierID: {}, Year: {}",
                                                                req.getCashierId(), req.getYear(), e);
                                                return new ApiResponse<>("error", e.getMessage(), null);
                                        });
                });
        }

        private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
                        Supplier<Uni<T>> supplier) {
                return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
        }
}