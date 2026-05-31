package com.sanedge.cashier.service.impl.stats;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.cashier.domain.response.CashierResponseMonthSales;
import com.sanedge.cashier.domain.response.CashierResponseYearSales;
import com.sanedge.cashier.repository.stats.CashierSalesRepository;
import com.sanedge.cashier.service.stats.CashierSalesService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CashierSalesServiceImpl implements CashierSalesService {
    private static final Logger logger = LoggerFactory.getLogger(CashierSalesServiceImpl.class);

    CashierSalesRepository cashierSalesRepository;
    OpenTelemetry openTelemetry;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public CashierSalesServiceImpl(CashierSalesRepository cashierSalesRepository, OpenTelemetry openTelemetry) {
        this.cashierSalesRepository = cashierSalesRepository;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("cashier-sales-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("cashier-sales-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    @Override
    public Uni<ApiResponse<List<CashierResponseMonthSales>>> findMonthlySales(Integer year) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findMonthlySales")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-sales-service")
                .setAttribute("operation", "find_monthly_sales")
                .setAttribute("year", year.toString())
                .startSpan();

        logger.info("📊 Fetching monthly cashier sales | Year: {}", year);

        return cashierSalesRepository.findMonthSales(year, 1, 12)
                .map(results -> {
                    List<CashierResponseMonthSales> response = results.stream()
                            .map(CashierResponseMonthSales::from)
                            .collect(Collectors.toList());

                    span.setAttribute("sales.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_sales",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} monthly cashier sales records", response.size());
                    return ApiResponse.success("Monthly cashier sales retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch monthly cashier sales | Year: {}", year, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_sales",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_sales"));
                });
    }

    @Override
    public Uni<ApiResponse<List<CashierResponseYearSales>>> findYearlySales(Integer year) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findYearlySales")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-sales-service")
                .setAttribute("operation", "find_yearly_sales")
                .setAttribute("year", year.toString())
                .startSpan();

        logger.info("📊 Fetching yearly cashier sales | Year: {}", year);

        return cashierSalesRepository.findYearSales(year)
                .map(results -> {
                    List<CashierResponseYearSales> response = results.stream()
                            .map(CashierResponseYearSales::from)
                            .collect(Collectors.toList());

                    span.setAttribute("sales.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_sales",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} yearly cashier sales records", response.size());
                    return ApiResponse.success("Yearly cashier sales retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch yearly cashier sales | Year: {}", year, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_sales",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_sales"));
                });
    }
}
