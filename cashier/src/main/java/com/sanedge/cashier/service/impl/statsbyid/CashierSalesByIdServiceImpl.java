package com.sanedge.cashier.service.impl.statsbyid;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.cashier.domain.requests.MonthCashierIdRequest;
import com.sanedge.cashier.domain.requests.YearCashierIdRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.cashier.domain.response.CashierResponseMonthSales;
import com.sanedge.cashier.domain.response.CashierResponseYearSales;
import com.sanedge.cashier.repository.statsbyid.CashierSalesByIdRepository;
import com.sanedge.cashier.service.statsbyid.CashierSalesByIdService;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CashierSalesByIdServiceImpl implements CashierSalesByIdService {
        private static final Logger logger = LoggerFactory.getLogger(CashierSalesByIdServiceImpl.class);

        private final CashierSalesByIdRepository cashierSalesByIdRepository;
        private final TracingMetrics tracingMetrics;

        @Inject
        public CashierSalesByIdServiceImpl(CashierSalesByIdRepository cashierSalesByIdRepository,
                        TracingMetrics tracingMetrics) {
                this.cashierSalesByIdRepository = cashierSalesByIdRepository;
                this.tracingMetrics = tracingMetrics;
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CashierResponseMonthSales>>> findMonthlyCashierById(MonthCashierIdRequest req) {
                Attributes attrs = Attributes.builder()
                                .put("cashier.id", req.getCashierId() != null ? req.getCashierId().toString() : "null")
                                .build();

                return runTraced("findMonthlyCashierById", "find_monthly_cashier_by_id", attrs, () -> {
                        if (req.getCashierId() == null || req.getYear() == null) {
                                logger.error("CashierId or Year is null | req: {}", req);
                                return Uni.createFrom().item(new ApiResponse<List<CashierResponseMonthSales>>("error",
                                                "CashierId and Year must not be null", null));
                        }

                        logger.info("Fetching monthly cashier sales by cashierId | CashierID: {}, Year: {}",
                                        req.getCashierId(), req.getYear());

                        LocalDate startMonth = LocalDate.of(req.getYear(), 1, 1);
                        LocalDate nextYear = startMonth.plusYears(1);

                        com.sanedge.cashier.domain.requests.FindCashierMonthSalesById rangeReq = new com.sanedge.cashier.domain.requests.FindCashierMonthSalesById();
                        rangeReq.setCashierId(req.getCashierId().longValue());
                        rangeReq.setYear(req.getYear());
                        rangeReq.setStartMonth(startMonth.getMonthValue());
                        rangeReq.setEndMonth(nextYear.getMonthValue());

                        return cashierSalesByIdRepository.findMonthSalesById(rangeReq)
                                        .map(results -> {
                                                List<CashierResponseMonthSales> response = results.stream()
                                                                .map(CashierResponseMonthSales::from)
                                                                .collect(Collectors.toList());

                                                logger.info("Found {} monthly cashier sales for cashier {}",
                                                                response.size(), req.getCashierId());
                                                return ApiResponse.success(
                                                                "Monthly cashier sales by cashier retrieved successfully",
                                                                response);
                                        })
                                        .onFailure().recoverWithItem(e -> {
                                                logger.error("Failed to fetch monthly cashier sales by cashier | CashierID: {}, Year: {}",
                                                                req.getCashierId(), req.getYear(), e);
                                                return new ApiResponse<>("error", e.getMessage(), null);
                                        });
                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CashierResponseYearSales>>> findYearlyCashierById(YearCashierIdRequest req) {
                Attributes attrs = Attributes.builder()
                                .put("cashier.id", req.getCashierId() != null ? req.getCashierId().toString() : "null")
                                .build();

                return runTraced("findYearlyCashierById", "find_yearly_cashier_by_id", attrs, () -> {
                        if (req.getCashierId() == null || req.getYear() == null) {
                                logger.error("CashierId or Year is null | req: {}", req);
                                return Uni.createFrom().item(new ApiResponse<List<CashierResponseYearSales>>("error",
                                                "CashierId and Year must not be null", null));
                        }

                        logger.info("Fetching yearly cashier sales by cashierId | CashierID: {}, Year: {}",
                                        req.getCashierId(), req.getYear());

                        return cashierSalesByIdRepository.findYearSalesById(
                                        req.getCashierId().longValue(),
                                        req.getYear())
                                        .map(results -> {
                                                List<CashierResponseYearSales> response = results.stream()
                                                                .map(CashierResponseYearSales::from)
                                                                .collect(Collectors.toList());

                                                logger.info("Found {} yearly cashier sales for cashier {}",
                                                                response.size(), req.getCashierId());
                                                return ApiResponse.success(
                                                                "Yearly cashier sales by cashier retrieved successfully",
                                                                response);
                                        })
                                        .onFailure().recoverWithItem(e -> {
                                                logger.error("Failed to fetch yearly cashier sales by cashier | CashierID: {}, Year: {}",
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