package com.sanedge.cashier.service.impl.statsbymerchant;

import java.time.LocalDate;
import java.util.List;
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
public class CashierTotalSalesByMerchantServiceImpl implements CashierTotalSalesByMerchantService {
    private static final Logger logger = LoggerFactory.getLogger(CashierTotalSalesByMerchantServiceImpl.class);

    CashierTotalSalesByMerchantRepository cashierTotalSalesByMerchantRepository;
    OpenTelemetry openTelemetry;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public CashierTotalSalesByMerchantServiceImpl(CashierTotalSalesByMerchantRepository cashierTotalSalesByMerchantRepository, OpenTelemetry openTelemetry) {
        this.cashierTotalSalesByMerchantRepository = cashierTotalSalesByMerchantRepository;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("cashier-total-sales-by-merchant-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("cashier-total-sales-by-merchant-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    @Override
    public Uni<ApiResponse<List<CashierResponseMonthTotalSales>>> findMonthlyTotalSalesByMerchant(MonthTotalSalesMerchant req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findMonthlyTotalSalesByMerchant")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-total-sales-by-merchant-service")
                .setAttribute("operation", "find_monthly_total_sales_by_merchant")
                .setAttribute("merchant.id", req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                .startSpan();

        if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
            logger.error("❌ MerchantId, Year, or Month is null | req: {}", req);
            span.setStatus(StatusCode.ERROR, "MerchantId, Year, and Month must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_monthly_total_sales_by_merchant",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "MerchantId, Year, and Month must not be null", null));
        }

        logger.info("📊 Fetching monthly total cashier sales by merchant | MerchantID: {}, Year: {}, Month: {}",
                req.getMerchantId(), req.getYear(), req.getMonth());

        LocalDate currentMonth = LocalDate.of(req.getYear(), req.getMonth(), 1);
        LocalDate nextMonth = currentMonth.plusMonths(1);

        return cashierTotalSalesByMerchantRepository.findMonthTotalSalesByMerchant(
                req.getMerchantId().longValue(),
                req.getYear(),
                req.getMonth(),
                nextMonth.getYear(),
                nextMonth.getMonthValue())
                .map(results -> {
                    List<CashierResponseMonthTotalSales> response = results.stream()
                            .map(CashierResponseMonthTotalSales::from)
                            .collect(Collectors.toList());

                    span.setAttribute("sales.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_total_sales_by_merchant",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} monthly total cashier sales for merchant {}", response.size(), req.getMerchantId());
                    return ApiResponse.success("Monthly total cashier sales by merchant retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch monthly total cashier sales by merchant | MerchantID: {}, Year: {}, Month: {}",
                            req.getMerchantId(), req.getYear(), req.getMonth(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_total_sales_by_merchant",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_total_sales_by_merchant"));
                });
    }

    @Override
    public Uni<ApiResponse<List<CashierResponseYearTotalSales>>> findYearlyTotalSalesByMerchant(YearTotalSalesMerchant req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findYearlyTotalSalesByMerchant")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-total-sales-by-merchant-service")
                .setAttribute("operation", "find_yearly_total_sales_by_merchant")
                .setAttribute("merchant.id", req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                .startSpan();

        if (req.getMerchantId() == null || req.getYear() == null) {
            logger.error("❌ MerchantId or Year is null | req: {}", req);
            span.setStatus(StatusCode.ERROR, "MerchantId and Year must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_yearly_total_sales_by_merchant",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "MerchantId and Year must not be null", null));
        }

        logger.info("📊 Fetching yearly total cashier sales by merchant | MerchantID: {}, Year: {}", req.getMerchantId(), req.getYear());

        return cashierTotalSalesByMerchantRepository.findYearTotalSalesByMerchant(
                req.getMerchantId().longValue(),
                req.getYear(),
                req.getYear() - 1)
                .map(results -> {
                    List<CashierResponseYearTotalSales> response = results.stream()
                            .map(CashierResponseYearTotalSales::from)
                            .collect(Collectors.toList());

                    span.setAttribute("sales.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_total_sales_by_merchant",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} yearly total cashier sales for merchant {}", response.size(), req.getMerchantId());
                    return ApiResponse.success("Yearly total cashier sales by merchant retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch yearly total cashier sales by merchant | MerchantID: {}, Year: {}, Month: {}", req.getMerchantId(), req.getYear(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_total_sales_by_merchant",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_total_sales_by_merchant"));
                });
    }
}
