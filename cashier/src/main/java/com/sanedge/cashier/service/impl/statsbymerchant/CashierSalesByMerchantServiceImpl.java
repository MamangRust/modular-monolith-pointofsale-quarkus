package com.sanedge.cashier.service.impl.statsbymerchant;

import java.time.LocalDate;
import java.util.List;
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
public class CashierSalesByMerchantServiceImpl implements CashierSalesByMerchantService {
    private static final Logger logger = LoggerFactory.getLogger(CashierSalesByMerchantServiceImpl.class);

    CashierSalesByMerchantRepository cashierSalesByMerchantRepository;
    OpenTelemetry openTelemetry;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public CashierSalesByMerchantServiceImpl(CashierSalesByMerchantRepository cashierSalesByMerchantRepository, OpenTelemetry openTelemetry) {
        this.cashierSalesByMerchantRepository = cashierSalesByMerchantRepository;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("cashier-sales-by-merchant-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("cashier-sales-by-merchant-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    @Override
    public Uni<ApiResponse<List<CashierResponseMonthSales>>> findMonthlyCashierByMerchant(MonthCashierMerchantRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findMonthlyCashierByMerchant")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-sales-by-merchant-service")
                .setAttribute("operation", "find_monthly_cashier_by_merchant")
                .setAttribute("merchant.id", req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                .startSpan();

        if (req.getMerchantId() == null || req.getYear() == null) {
            logger.error("❌ MerchantId or Year is null | req: {}", req);
            span.setStatus(StatusCode.ERROR, "MerchantId and Year must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_monthly_cashier_by_merchant",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "MerchantId and Year must not be null", null));
        }

        logger.info("📊 Fetching monthly cashier sales by merchant | MerchantID: {}, Year: {}", req.getMerchantId(), req.getYear());

        LocalDate startMonth = LocalDate.of(req.getYear(), 1, 1);
        LocalDate nextYear = startMonth.plusYears(1);

        return cashierSalesByMerchantRepository.findMonthSalesByMerchant(
                req.getMerchantId().longValue(),
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
                            AttributeKey.stringKey("operation"), "find_monthly_cashier_by_merchant",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} monthly cashier sales for merchant {}", response.size(), req.getMerchantId());
                    return ApiResponse.success("Monthly cashier sales by merchant retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch monthly cashier sales by merchant | MerchantID: {}, Year: {}", req.getMerchantId(), req.getYear(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_cashier_by_merchant",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_cashier_by_merchant"));
                });
    }

    @Override
    public Uni<ApiResponse<List<CashierResponseYearSales>>> findYearlyCashierByMerchant(YearCashierMerchantRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findYearlyCashierByMerchant")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-sales-by-merchant-service")
                .setAttribute("operation", "find_yearly_cashier_by_merchant")
                .setAttribute("merchant.id", req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                .startSpan();

        if (req.getMerchantId() == null || req.getYear() == null) {
            logger.error("❌ MerchantId or Year is null | req: {}", req);
            span.setStatus(StatusCode.ERROR, "MerchantId and Year must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_yearly_cashier_by_merchant",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "MerchantId and Year must not be null", null));
        }

        logger.info("📊 Fetching yearly cashier sales by merchant | MerchantID: {}, Year: {}", req.getMerchantId(), req.getYear());

        return cashierSalesByMerchantRepository.findYearSalesByMerchant(
                req.getMerchantId().longValue(),
                req.getYear())
                .map(results -> {
                    List<CashierResponseYearSales> response = results.stream()
                            .map(CashierResponseYearSales::from)
                            .collect(Collectors.toList());

                    span.setAttribute("sales.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_cashier_by_merchant",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} yearly cashier sales for merchant {}", response.size(), req.getMerchantId());
                    return ApiResponse.success("Yearly cashier sales by merchant retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch yearly cashier sales by merchant | MerchantID: {}, Year: {}", req.getMerchantId(), req.getYear(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_cashier_by_merchant",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_cashier_by_merchant"));
                });
    }
}
