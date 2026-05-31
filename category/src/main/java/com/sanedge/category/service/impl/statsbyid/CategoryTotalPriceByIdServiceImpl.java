package com.sanedge.category.service.impl.statsbyid;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.category.domain.requests.MonthTotalPriceCategory;
import com.sanedge.category.domain.requests.YearTotalPriceCategory;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoriesMonthlyTotalPriceResponse;
import com.sanedge.category.domain.response.CategoriesYearlyTotalPriceResponse;
import com.sanedge.category.repository.statsbyid.CategoryTotalPriceByIdRepository;
import com.sanedge.category.service.statsbyid.CategoryTotalPriceByIdService;

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
public class CategoryTotalPriceByIdServiceImpl implements CategoryTotalPriceByIdService {
    private static final Logger logger = LoggerFactory.getLogger(CategoryTotalPriceByIdServiceImpl.class);

    CategoryTotalPriceByIdRepository categoryTotalPriceByIdRepository;
    OpenTelemetry openTelemetry;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public CategoryTotalPriceByIdServiceImpl(CategoryTotalPriceByIdRepository categoryTotalPriceByIdRepository, OpenTelemetry openTelemetry) {
        this.categoryTotalPriceByIdRepository = categoryTotalPriceByIdRepository;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("category-total-price-by-id-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("category-total-price-by-id-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    @Override
    public Uni<ApiResponse<List<CategoriesMonthlyTotalPriceResponse>>> findMonthlyTotalPriceById(MonthTotalPriceCategory req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findMonthlyTotalPriceById")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-total-price-by-id-service")
                .setAttribute("operation", "find_monthly_total_price_by_id")
                .setAttribute("category.id", req.getCategoryId() != null ? req.getCategoryId().toString() : "null")
                .setAttribute("category.year", req.getYear() != null ? req.getYear().toString() : "null")
                .setAttribute("category.month", req.getMonth() != null ? req.getMonth().toString() : "null")
                .startSpan();

        if (req.getCategoryId() == null || req.getYear() == null || req.getMonth() == null) {
            logger.error("❌ CategoryId, Year or Month is null | req: {}", req);
            span.setStatus(StatusCode.ERROR, "CategoryId, Year and Month must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_monthly_total_price_by_id",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "CategoryId, Year and Month must not be null", List.of()));
        }

        logger.info("📊 Fetching monthly total price by category | CategoryId: {}, Year: {}, Month: {}",
                req.getCategoryId(), req.getYear(), req.getMonth());

        LocalDate currentMonth = LocalDate.of(req.getYear(), req.getMonth(), 1);
        LocalDate nextMonth = currentMonth.plusMonths(1);

        return categoryTotalPriceByIdRepository.findMonthlyTotalPriceByCategoryId(
                req.getCategoryId().longValue(),
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
                            AttributeKey.stringKey("operation"), "find_monthly_total_price_by_id",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} monthly total price stats for categoryId {}", response.size(), req.getCategoryId());
                    return ApiResponse.success("Monthly total price by category retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch monthly total price by category | CategoryId: {}, Year: {}, Month: {}",
                            req.getCategoryId(), req.getYear(), req.getMonth(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_total_price_by_id",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to fetch monthly total price by category", List.of());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_monthly_total_price_by_id"));
                });
    }

    @Override
    public Uni<ApiResponse<List<CategoriesYearlyTotalPriceResponse>>> findYearlyTotalPriceById(YearTotalPriceCategory req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findYearlyTotalPriceById")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-total-price-by-id-service")
                .setAttribute("operation", "find_yearly_total_price_by_id")
                .setAttribute("category.id", req.getCategoryId() != null ? req.getCategoryId().toString() : "null")
                .setAttribute("category.year", req.getYear() != null ? req.getYear().toString() : "null")
                .startSpan();

        if (req.getCategoryId() == null || req.getYear() == null) {
            logger.error("❌ CategoryId or Year is null | req: {}", req);
            span.setStatus(StatusCode.ERROR, "CategoryId and Year must not be null");
            requestsTotal.add(1, Attributes.of(
                    AttributeKey.stringKey("operation"), "find_yearly_total_price_by_id",
                    AttributeKey.stringKey("status"), "failed",
                    AttributeKey.stringKey("error_type"), "invalid_argument"));
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "CategoryId and Year must not be null", List.of()));
        }

        logger.info("📊 Fetching yearly total price by category | CategoryId: {}, Year: {}", req.getCategoryId(), req.getYear());

        return categoryTotalPriceByIdRepository.findYearlyTotalPriceByCategoryId(
                req.getCategoryId().longValue(),
                req.getYear(),
                req.getYear() - 1)
                .map(results -> {
                    List<CategoriesYearlyTotalPriceResponse> response = results.stream()
                            .map(CategoriesYearlyTotalPriceResponse::from)
                            .collect(Collectors.toList());

                    span.setAttribute("stats.count", response.size());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_total_price_by_id",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Found {} yearly total price stats for categoryId {}", response.size(), req.getCategoryId());
                    return ApiResponse.success("Yearly total price by category retrieved successfully", response);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to fetch yearly total price by category | CategoryId: {}, Year: {}", req.getCategoryId(), req.getYear(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_total_price_by_id",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to fetch yearly total price by category", List.of());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_yearly_total_price_by_id"));
                });
    }
}
