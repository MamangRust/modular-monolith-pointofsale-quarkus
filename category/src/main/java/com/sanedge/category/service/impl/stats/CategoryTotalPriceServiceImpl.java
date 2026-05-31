package com.sanedge.category.service.impl.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.category.domain.requests.MonthTotalPrice;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.repository.stats.CategoryTotalPriceRepository;
import com.sanedge.category.service.stats.CategoryTotalPriceService;

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
public class CategoryTotalPriceServiceImpl implements CategoryTotalPriceService {
    private static final Logger logger = LoggerFactory.getLogger(CategoryTotalPriceServiceImpl.class);

    CategoryTotalPriceRepository categoryTotalPriceRepository;
    OpenTelemetry openTelemetry;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public CategoryTotalPriceServiceImpl(CategoryTotalPriceRepository categoryTotalPriceRepository, OpenTelemetry openTelemetry) {
        this.categoryTotalPriceRepository = categoryTotalPriceRepository;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("category-total-price-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("category-total-price-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    @Override
    public Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPrice(MonthTotalPrice req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findMonthlyTotalPrice")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-total-price-service")
                .setAttribute("operation", "find_monthly_total_price")
                .setAttribute("category.year", req.getYear() != null ? req.getYear().toString() : "null")
                .setAttribute("category.month", req.getMonth() != null ? req.getMonth().toString() : "null")
                .startSpan();

        if (req.getYear() == null || req.getMonth() == null) {
            logger.error("❌ Year or Month is null | req: {}", req);
            span.setStatus(StatusCode.ERROR, "Year and Month must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_monthly_total_price",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "Year and Month must not be null", List.of()));
        }

        logger.info("📊 Fetching monthly total category price | Year: {}, Month: {}", req.getYear(), req.getMonth());

        LocalDate currentMonth = LocalDate.of(req.getYear(), req.getMonth(), 1);
        LocalDate nextMonth = currentMonth.plusMonths(1);

        return categoryTotalPriceRepository.findMonthlyTotalPrice(
                req.getYear(),
                req.getMonth(),
                nextMonth.getYear(),
                nextMonth.getMonthValue())
                .map(results -> {
                    List<CategoriesMonthlyTotalPriceResponse> response = results.stream()
                            .map(CategoriesMonthlyTotalPriceResponse::from)
                            .collect(Collectors.toList());

                    span.setAttribute("stats.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_total_price",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} monthly total price stats", response.size());
                    return ApiResponse.success("Monthly total price stats retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch monthly total category price | Year: {}, Month: {}", req.getYear(), req.getMonth(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_total_price",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to fetch monthly total category price", List.of());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_total_price"));
                });
    }

    @Override
    public Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPrice(Integer year) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findYearlyTotalPrice")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-total-price-service")
                .setAttribute("operation", "find_yearly_total_price")
                .setAttribute("category.year", year != null ? year.toString() : "null")
                .startSpan();

        if (year == null) {
            logger.error("❌ Year is null");
            span.setStatus(StatusCode.ERROR, "Year must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_yearly_total_price",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "Year must not be null", List.of()));
        }

        logger.info("📊 Fetching yearly total category price | Year: {}", year);

        return categoryTotalPriceRepository.findYearlyTotalPrice(year, year - 1)
                .map(results -> {
                    List<CategoriesYearlyTotalPriceResponse> response = results.stream()
                            .map(CategoriesYearlyTotalPriceResponse::from)
                            .collect(Collectors.toList());

                    span.setAttribute("stats.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_total_price",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} yearly total price stats", response.size());
                    return ApiResponse.success("Yearly total price stats retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch yearly total category price | Year: {}", year, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_total_price",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to fetch yearly total category price", List.of());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_total_price"));
                });
    }
}
