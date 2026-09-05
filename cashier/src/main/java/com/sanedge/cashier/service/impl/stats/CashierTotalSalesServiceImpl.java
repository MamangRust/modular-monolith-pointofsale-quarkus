package com.sanedge.cashier.service.impl.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.cashier.domain.requests.MonthTotalSales;
import com.sanedge.cashier.domain.response.CashierResponseMonthTotalSales;
import com.sanedge.cashier.domain.response.CashierResponseYearTotalSales;
import com.sanedge.cashier.repository.stats.CashierTotalSalesRepository;
import com.sanedge.cashier.service.stats.CashierTotalSalesService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CashierTotalSalesServiceImpl implements CashierTotalSalesService {
        private static final Logger logger = LoggerFactory.getLogger(CashierTotalSalesServiceImpl.class);

        private final CashierTotalSalesRepository cashierTotalSalesRepository;
        private final TracingMetrics tracingMetrics;

        @Inject
        public CashierTotalSalesServiceImpl(CashierTotalSalesRepository cashierTotalSalesRepository,
                        TracingMetrics tracingMetrics) {
                this.cashierTotalSalesRepository = cashierTotalSalesRepository;
                this.tracingMetrics = tracingMetrics;
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CashierResponseMonthTotalSales>>> findMonthlyTotalSales(MonthTotalSales req) {
                Attributes attrs = Attributes.builder()
                                .put("year", req.getYear() != null ? req.getYear().toString() : "null")
                                .put("month", req.getMonth() != null ? req.getMonth().toString() : "null")
                                .build();

                return runTraced("findMonthlyTotalSales", "find_monthly_total_sales", attrs, () -> {
                        if (req.getYear() == null || req.getMonth() == null) {
                                logger.error("Year or Month is null | req: {}", req);
                                return Uni.createFrom().item(
                                                new ApiResponse<>("error", "Year and Month must not be null", null));
                        }

                        logger.info("Fetching monthly total cashier sales | Year: {}, Month: {}", req.getYear(),
                                        req.getMonth());

                        LocalDate currentMonth = LocalDate.of(req.getYear(), req.getMonth(), 1);
                        LocalDate nextMonth = currentMonth.plusMonths(1);

                        com.sanedge.cashier.domain.requests.FindMonthTotalSalesRange rangeReq = new com.sanedge.cashier.domain.requests.FindMonthTotalSalesRange();
                        rangeReq.setStartYear(req.getYear());
                        rangeReq.setStartMonth(req.getMonth());
                        rangeReq.setEndYear(nextMonth.getYear());
                        rangeReq.setEndMonth(nextMonth.getMonthValue());

                        return cashierTotalSalesRepository.findMonthTotalSales(rangeReq)
                                        .map(results -> {
                                                List<CashierResponseMonthTotalSales> response = results.stream()
                                                                .map(CashierResponseMonthTotalSales::from)
                                                                .collect(Collectors.toList());

                                                logger.info("Found {} monthly total cashier sales", response.size());
                                                return ApiResponse.success(
                                                                "Monthly total cashier sales retrieved successfully",
                                                                response);
                                        })
                                        .onFailure().recoverWithItem(e -> {
                                                logger.error("Failed to fetch monthly total cashier sales | Year: {}, Month: {}",
                                                                req.getYear(), req.getMonth(), e);
                                                return new ApiResponse<>("error", e.getMessage(), null);
                                        });
                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CashierResponseYearTotalSales>>> findYearlyTotalSales(Integer year) {
                Attributes attrs = Attributes.builder()
                                .put("year", year != null ? year.toString() : "null")
                                .build();

                return runTraced("findYearlyTotalSales", "find_yearly_total_sales", attrs, () -> {
                        if (year == null) {
                                logger.error("Year is null");
                                return Uni.createFrom().item(new ApiResponse<>("error", "Year must not be null", null));
                        }

                        logger.info("Fetching yearly total cashier sales | Year: {}", year);

                        return cashierTotalSalesRepository.findYearTotalSales(year, year - 1)
                                        .map(results -> {
                                                List<CashierResponseYearTotalSales> response = results.stream()
                                                                .map(CashierResponseYearTotalSales::from)
                                                                .collect(Collectors.toList());

                                                logger.info("Found {} yearly total cashier sales", response.size());
                                                return ApiResponse.success(
                                                                "Yearly total cashier sales retrieved successfully",
                                                                response);
                                        })
                                        .onFailure().recoverWithItem(e -> {
                                                logger.error("Failed to fetch yearly total cashier sales | Year: {}",
                                                                year, e);
                                                return new ApiResponse<>("error", e.getMessage(), null);
                                        });
                });
        }

        private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
                        Supplier<Uni<T>> supplier) {
                return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
        }
}