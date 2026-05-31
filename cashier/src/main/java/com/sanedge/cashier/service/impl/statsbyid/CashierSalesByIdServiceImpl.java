package com.sanedge.cashier.service.impl.statsbyid;

import java.time.LocalDate;
import java.util.List;
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
public class CashierSalesByIdServiceImpl implements CashierSalesByIdService {
    private static final Logger logger = LoggerFactory.getLogger(CashierSalesByIdServiceImpl.class);

    CashierSalesByIdRepository cashierSalesByIdRepository;
    OpenTelemetry openTelemetry;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public CashierSalesByIdServiceImpl(CashierSalesByIdRepository cashierSalesByIdRepository, OpenTelemetry openTelemetry) {
        this.cashierSalesByIdRepository = cashierSalesByIdRepository;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("cashier-sales-by-id-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("cashier-sales-by-id-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    @Override
    public Uni<ApiResponse<List<CashierResponseMonthSales>>> findMonthlyCashierById(MonthCashierIdRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findMonthlyCashierById")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-sales-by-id-service")
                .setAttribute("operation", "find_monthly_cashier_by_id")
                .setAttribute("cashier.id", req.getCashierId() != null ? req.getCashierId().toString() : "null")
                .startSpan();

        if (req.getCashierId() == null || req.getYear() == null) {
            logger.error("❌ CashierId or Year is null | req: {}", req);
            span.setStatus(StatusCode.ERROR, "CashierId and Year must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_monthly_cashier_by_id",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "CashierId and Year must not be null", null));
        }

        logger.info("📊 Fetching monthly cashier sales by cashierId | CashierID: {}, Year: {}", req.getCashierId(), req.getYear());

        LocalDate startMonth = LocalDate.of(req.getYear(), 1, 1);
        LocalDate nextYear = startMonth.plusYears(1);

        return cashierSalesByIdRepository.findMonthSalesById(
                req.getCashierId().longValue(),
                req.getYear(),
                startMonth.getMonthValue(),
                nextYear.getMonthValue())
                .map(results -> {
                    List<CashierResponseMonthSales> response = results.stream()
                            .map(CashierResponseMonthSales::from)
                            .collect(Collectors.toList());

                    span.setAttribute("sales.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_cashier_by_id",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} monthly cashier sales for cashier {}", response.size(), req.getCashierId());
                    return ApiResponse.success("Monthly cashier sales by cashier retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch monthly cashier sales by cashier | CashierID: {}, Year: {}", req.getCashierId(), req.getYear(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_cashier_by_id",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_cashier_by_id"));
                });
    }

    @Override
    public Uni<ApiResponse<List<CashierResponseYearSales>>> findYearlyCashierById(YearCashierIdRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findYearlyCashierById")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-sales-by-id-service")
                .setAttribute("operation", "find_yearly_cashier_by_id")
                .setAttribute("cashier.id", req.getCashierId() != null ? req.getCashierId().toString() : "null")
                .startSpan();

        if (req.getCashierId() == null || req.getYear() == null) {
            logger.error("❌ CashierId or Year is null | req: {}", req);
            span.setStatus(StatusCode.ERROR, "CashierId and Year must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_yearly_cashier_by_id",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "CashierId and Year must not be null", null));
        }

        logger.info("📊 Fetching yearly cashier sales by cashierId | CashierID: {}, Year: {}", req.getCashierId(), req.getYear());

        return cashierSalesByIdRepository.findYearSalesById(
                req.getCashierId().longValue(),
                req.getYear())
                .map(results -> {
                    List<CashierResponseYearSales> response = results.stream()
                            .map(CashierResponseYearSales::from)
                            .collect(Collectors.toList());

                    span.setAttribute("sales.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_cashier_by_id",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} yearly cashier sales for cashier {}", response.size(), req.getCashierId());
                    return ApiResponse.success("Yearly cashier sales by cashier retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch yearly cashier sales by cashier | CashierID: {}, Year: {}", req.getCashierId(), req.getYear(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_cashier_by_id",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_cashier_by_id"));
                });
    }
}
