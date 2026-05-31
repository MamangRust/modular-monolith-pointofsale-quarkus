package com.sanedge.category.service.impl.stats;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.repository.stats.CategoryPriceRepository;
import com.sanedge.category.service.stats.CategoryPriceService;

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
public class CategoryPriceServiceImpl implements CategoryPriceService {
    private static final Logger logger = LoggerFactory.getLogger(CategoryPriceServiceImpl.class);

    CategoryPriceRepository categoryPriceRepository;
    OpenTelemetry openTelemetry;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public CategoryPriceServiceImpl(CategoryPriceRepository categoryPriceRepository, OpenTelemetry openTelemetry) {
        this.categoryPriceRepository = categoryPriceRepository;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("category-price-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("category-price-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    @Override
    public Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPrice(Integer year) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findMonthPrice")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-price-service")
                .setAttribute("operation", "find_month_price")
                .setAttribute("category.year", year != null ? year.toString() : "null")
                .startSpan();

        if (year == null) {
            logger.error("❌ Year is null");
            span.setStatus(StatusCode.ERROR, "Year must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_month_price",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "Year must not be null", List.of()));
        }

        logger.info("📊 Fetching monthly category price stats | Year: {}", year);

        return categoryPriceRepository.findMonthlyCategoryPrice(year)
                .map(results -> {
                    List<CategoriesMonthPriceResponse> response = results.stream()
                            .map(CategoriesMonthPriceResponse::from)
                            .collect(Collectors.toList());

                    span.setAttribute("stats.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_month_price",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} monthly category price stats", response.size());
                    return ApiResponse.success("Monthly category price stats retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch monthly category price stats | Year: {}", year, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_month_price",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to fetch monthly category price stats", List.of());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_month_price"));
                });
    }

    @Override
    public Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPrice(Integer year) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findYearPrice")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-price-service")
                .setAttribute("operation", "find_year_price")
                .setAttribute("category.year", year != null ? year.toString() : "null")
                .startSpan();

        if (year == null) {
            logger.error("❌ Year is null");
            span.setStatus(StatusCode.ERROR, "Year must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_year_price",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "Year must not be null", List.of()));
        }

        logger.info("📊 Fetching yearly category price stats | Year: {}", year);

        return categoryPriceRepository.findYearlyCategoryPrice(year)
                .map(results -> {
                    List<CategoriesYearPriceResponse> response = results.stream()
                            .map(CategoriesYearPriceResponse::from)
                            .collect(Collectors.toList());

                    span.setAttribute("stats.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_year_price",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} yearly category price stats", response.size());
                    return ApiResponse.success("Yearly category price stats retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch yearly category price stats | Year: {}", year, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_year_price",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to fetch yearly category price stats", List.of());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_year_price"));
                });
    }
}
