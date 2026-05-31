package com.sanedge.category.service.impl.statsbyid;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.category.domain.requests.MonthPriceId;
import com.sanedge.category.domain.requests.YearPriceId;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearPriceResponse;
import com.sanedge.category.repository.statsbyid.CategoryPriceByIdRepository;
import com.sanedge.category.service.statsbyid.CategoryPriceByIdService;

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
public class CategoryPriceByIdServiceImpl implements CategoryPriceByIdService {
    private static final Logger logger = LoggerFactory.getLogger(CategoryPriceByIdServiceImpl.class);

    CategoryPriceByIdRepository categoryPriceByIdRepository;
    OpenTelemetry openTelemetry;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public CategoryPriceByIdServiceImpl(CategoryPriceByIdRepository categoryPriceByIdRepository, OpenTelemetry openTelemetry) {
        this.categoryPriceByIdRepository = categoryPriceByIdRepository;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("category-price-by-id-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("category-price-by-id-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    @Override
    public Uni<ApiResponse<List<CategoriesMonthPriceResponse>>> findMonthPriceById(MonthPriceId req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findMonthPriceById")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-price-by-id-service")
                .setAttribute("operation", "find_month_price_by_id")
                .setAttribute("category.id", req.getCategoryId() != null ? req.getCategoryId().toString() : "null")
                .setAttribute("category.year", req.getYear() != null ? req.getYear().toString() : "null")
                .startSpan();

        if (req.getCategoryId() == null || req.getYear() == null) {
            logger.error("❌ CategoryId or Year is null | req: {}", req);
            span.setStatus(StatusCode.ERROR, "CategoryId and Year must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_month_price_by_id",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "CategoryId and Year must not be null", List.of()));
        }

        logger.info("📊 Fetching monthly price by category | CategoryId: {}, Year: {}", req.getCategoryId(), req.getYear());

        return categoryPriceByIdRepository.findMonthlyCategoryPriceById(req.getCategoryId().longValue(), req.getYear())
                .map(results -> {
                    List<CategoriesMonthPriceResponse> response = results.stream()
                            .map(CategoriesMonthPriceResponse::from)
                            .collect(Collectors.toList());

                    span.setAttribute("stats.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_month_price_by_id",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} monthly price stats for categoryId {}", response.size(), req.getCategoryId());
                    return ApiResponse.success("Monthly category price stats retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch monthly price by category | CategoryId: {}, Year: {}", req.getCategoryId(), req.getYear(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_month_price_by_id",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to fetch monthly price by category", List.of());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_month_price_by_id"));
                });
    }

    @Override
    public Uni<ApiResponse<List<CategoriesYearPriceResponse>>> findYearPriceById(YearPriceId req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findYearPriceById")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-price-by-id-service")
                .setAttribute("operation", "find_year_price_by_id")
                .setAttribute("category.id", req.getCategoryId() != null ? req.getCategoryId().toString() : "null")
                .setAttribute("category.year", req.getYear() != null ? req.getYear().toString() : "null")
                .startSpan();

        if (req.getCategoryId() == null || req.getYear() == null) {
            logger.error("❌ CategoryId or Year is null | req: {}", req);
            span.setStatus(StatusCode.ERROR, "CategoryId and Year must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_year_price_by_id",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "CategoryId and Year must not be null", List.of()));
        }

        logger.info("📊 Fetching yearly price by category | CategoryId: {}, Year: {}", req.getCategoryId(), req.getYear());

        return categoryPriceByIdRepository.findYearlyCategoryPriceById(req.getCategoryId().longValue(), req.getYear())
                .map(results -> {
                    List<CategoriesYearPriceResponse> response = results.stream()
                            .map(CategoriesYearPriceResponse::from)
                            .collect(Collectors.toList());

                    span.setAttribute("stats.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_year_price_by_id",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} yearly price stats for categoryId {}", response.size(), req.getCategoryId());
                    return ApiResponse.success("Yearly category price stats retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch yearly price by category | CategoryId: {}, Year: {}", req.getCategoryId(), req.getYear(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_year_price_by_id",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to fetch yearly price by category", List.of());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_year_price_by_id"));
                });
    }
}
