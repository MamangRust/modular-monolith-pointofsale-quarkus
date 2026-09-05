package com.sanedge.cashier.service.impl.stats;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.cashier.domain.response.CashierResponseMonthSales;
import com.sanedge.cashier.domain.response.CashierResponseYearSales;
import com.sanedge.cashier.repository.stats.CashierSalesRepository;
import com.sanedge.cashier.service.stats.CashierSalesService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CashierSalesServiceImpl implements CashierSalesService {
        private static final Logger logger = LoggerFactory.getLogger(CashierSalesServiceImpl.class);

        private final CashierSalesRepository cashierSalesRepository;
        private final TracingMetrics tracingMetrics;

        @Inject
        public CashierSalesServiceImpl(CashierSalesRepository cashierSalesRepository, TracingMetrics tracingMetrics) {
                this.cashierSalesRepository = cashierSalesRepository;
                this.tracingMetrics = tracingMetrics;
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CashierResponseMonthSales>>> findMonthlySales(Integer year) {
                Attributes attrs = Attributes.builder()
                                .put("year", year.toString())
                                .build();

                return runTraced("findMonthlySales", "find_monthly_sales", attrs, () -> {
                        logger.info("Fetching monthly cashier sales | Year: {}", year);

                        com.sanedge.cashier.domain.requests.FindCashierMonthSales req = new com.sanedge.cashier.domain.requests.FindCashierMonthSales();
                        req.setYear(year);
                        req.setStartMonth(1);
                        req.setEndMonth(12);

                        return cashierSalesRepository.findMonthSales(req)
                                        .map(results -> {
                                                List<CashierResponseMonthSales> response = results.stream()
                                                                .map(CashierResponseMonthSales::from)
                                                                .collect(Collectors.toList());

                                                logger.info("Found {} monthly cashier sales records", response.size());
                                                return ApiResponse.success(
                                                                "Monthly cashier sales retrieved successfully",
                                                                response);
                                        })
                                        .onFailure().recoverWithItem(e -> {
                                                logger.error("Failed to fetch monthly cashier sales | Year: {}", year,
                                                                e);
                                                return new ApiResponse<>("error", e.getMessage(), null);
                                        });
                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<CashierResponseYearSales>>> findYearlySales(Integer year) {
                Attributes attrs = Attributes.builder()
                                .put("year", year.toString())
                                .build();

                return runTraced("findYearlySales", "find_yearly_sales", attrs, () -> {
                        logger.info("Fetching yearly cashier sales | Year: {}", year);

                        return cashierSalesRepository.findYearSales(year)
                                        .map(results -> {
                                                List<CashierResponseYearSales> response = results.stream()
                                                                .map(CashierResponseYearSales::from)
                                                                .collect(Collectors.toList());

                                                logger.info("Found {} yearly cashier sales records", response.size());
                                                return ApiResponse.success(
                                                                "Yearly cashier sales retrieved successfully",
                                                                response);
                                        })
                                        .onFailure().recoverWithItem(e -> {
                                                logger.error("Failed to fetch yearly cashier sales | Year: {}", year,
                                                                e);
                                                return new ApiResponse<>("error", e.getMessage(), null);
                                        });
                });
        }

        private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
                        Supplier<Uni<T>> supplier) {
                return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
        }
}