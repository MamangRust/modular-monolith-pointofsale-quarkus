package com.sanedge.order.service.impl;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.order.domain.requests.MonthTotalRevenue;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.repository.stats.OrderTotalRevenueRepository;
import com.sanedge.order.service.stats.OrderTotalRevenueService;

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
public class OrderTotalRevenueServiceImpl implements OrderTotalRevenueService {
    private static final Logger logger = LoggerFactory.getLogger(OrderTotalRevenueServiceImpl.class);

    OrderTotalRevenueRepository orderTotalRevenueRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long CACHE_TTL_SECONDS = 3600; // 1 hour for stats

    @Inject
    public OrderTotalRevenueServiceImpl(OrderTotalRevenueRepository orderTotalRevenueRepository,
                                       OpenTelemetry openTelemetry,
                                       RedisService redisService,
                                       ObjectMapper objectMapper) {
        this.orderTotalRevenueRepository = orderTotalRevenueRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("order-revenue-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("order-revenue-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing to JSON", e);
            throw new RuntimeException("Failed to serialize", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON", e);
            throw new RuntimeException("Failed to deserialize", e);
        }
    }

    @Override
    public Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyStats(MonthTotalRevenue req) {
        logger.info("📊 Fetching monthly order stats | Year: {}, Month: {}", req.getYear(), req.getMonth());

        if (req.getYear() == null || req.getMonth() == null) {
            logger.error("❌ Year or Month is null | req: {}", req);
            return Uni.createFrom().item(new ApiResponse<>("error", "Year and Month must not be null", Collections.emptyList()));
        }

        if (req.getMonth() < 1 || req.getMonth() > 12) {
            logger.error("❌ Invalid Month={}", req.getMonth());
            return Uni.createFrom().item(new ApiResponse<>("error", "Month must be between 1 and 12", Collections.emptyList()));
        }

        String cacheKey = String.format("orders:revenue:monthly:%d:%d", req.getYear(), req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> cached = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>>() {});
                        return Uni.createFrom().item(cached);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findMonthlyRevenueStats")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_monthly_revenue")
                            .setAttribute("year", req.getYear().toString())
                            .setAttribute("month", req.getMonth().toString())
                            .startSpan();

                    LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                    LocalDate next = current.plusMonths(1);

                    return orderTotalRevenueRepository.findMonthlyTotalRevenue(
                            req.getYear(),
                            req.getMonth(),
                            next.getYear(),
                            next.getMonthValue()
                    )
                    .chain(rawData -> {
                        List<OrderMonthlyTotalRevenueResponse> responses = OrderMonthlyTotalRevenueResponse.fromList(rawData);
                        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> apiResponse = ApiResponse.success(
                                "Monthly order stats retrieved successfully", responses);

                        return redisService.setWithExpirationReactive(cacheKey, toJson(apiResponse), CACHE_TTL_SECONDS)
                                .map(v -> {
                                    span.setStatus(StatusCode.OK);
                                    requestsTotal.add(1, Attributes.of(
                                            AttributeKey.stringKey("operation"), "find_monthly_revenue",
                                            AttributeKey.stringKey("status"), "success"));
                                    logger.info("✅ Found {} monthly order stats", responses.size());
                                    return apiResponse;
                                });
                    })
                    .onFailure().recoverWithItem(e -> {
                        logger.error("💥 Failed to fetch monthly order stats | Year: {}, Month: {}: {}",
                                req.getYear(), req.getMonth(), e.getMessage(), e);
                        span.recordException(e);
                        span.setStatus(StatusCode.ERROR, e.getMessage());

                        requestsTotal.add(1, Attributes.of(
                                AttributeKey.stringKey("operation"), "find_monthly_revenue",
                                AttributeKey.stringKey("status"), "failed",
                                AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                        return new ApiResponse<>("error", "Failed to fetch monthly order stats: " + e.getMessage(), Collections.emptyList());
                    })
                    .eventually(() -> {
                        span.end();
                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                        requestDurationSeconds.record(duration, Attributes.of(
                                AttributeKey.stringKey("operation"), "find_monthly_revenue"));
                    });
                });
    }

    @Override
    public Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyStats(Integer year) {
        logger.info("📈 Fetching yearly order stats | Year: {}", year);

        if (year == null) {
            logger.error("❌ Year is null");
            return Uni.createFrom().item(new ApiResponse<>("error", "Year must not be null", Collections.emptyList()));
        }

        String cacheKey = "orders:revenue:yearly:" + year;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderYearlyTotalRevenueResponse>> cached = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderYearlyTotalRevenueResponse>>>() {});
                        return Uni.createFrom().item(cached);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findYearlyRevenueStats")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_yearly_revenue")
                            .setAttribute("year", year.toString())
                            .startSpan();

                    return orderTotalRevenueRepository.findYearlyTotalRevenue(year)
                            .chain(rawData -> {
                                List<OrderYearlyTotalRevenueResponse> responses = OrderYearlyTotalRevenueResponse.fromList(rawData);
                                ApiResponse<List<OrderYearlyTotalRevenueResponse>> apiResponse = ApiResponse.success(
                                        "Yearly order stats retrieved successfully", responses);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(apiResponse), CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_yearly_revenue",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} yearly order stats", responses.size());
                                            return apiResponse;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch yearly order stats | Year: {}: {}", year, e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_revenue",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to fetch yearly order stats: " + e.getMessage(), Collections.emptyList());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_revenue"));
                            });
                });
    }
}
