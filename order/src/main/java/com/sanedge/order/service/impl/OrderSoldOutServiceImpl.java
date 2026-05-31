package com.sanedge.order.service.impl;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.order.domain.response.OrderMonthlyResponse;
import com.sanedge.order.domain.response.OrderYearlyResponse;
import com.sanedge.order.repository.stats.OrderSoldOutRepository;
import com.sanedge.order.service.stats.OrderSoldoutService;

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
public class OrderSoldOutServiceImpl implements OrderSoldoutService {
    private static final Logger logger = LoggerFactory.getLogger(OrderSoldOutServiceImpl.class);

    OrderSoldOutRepository orderSoldOutRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long CACHE_TTL_SECONDS = 3600; // 1 hour for stats

    @Inject
    public OrderSoldOutServiceImpl(OrderSoldOutRepository orderSoldOutRepository,
                                   OpenTelemetry openTelemetry,
                                   RedisService redisService,
                                   ObjectMapper objectMapper) {
        this.orderSoldOutRepository = orderSoldOutRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("order-soldout-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("order-soldout-service");

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
    public Uni<ApiResponse<List<OrderMonthlyResponse>>> findMonthlyOrders(Integer yearMonth) {
        String cacheKey = "orders:soldout:monthly:" + yearMonth;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderMonthlyResponse>> cached = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderMonthlyResponse>>>() {});
                        return Uni.createFrom().item(cached);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findMonthlyOrders")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_monthly_orders")
                            .setAttribute("yearMonth", yearMonth != null ? yearMonth.toString() : "null")
                            .startSpan();

                    return orderSoldOutRepository.findMonthlyOrdersByYear(yearMonth)
                            .chain(rawData -> {
                                List<OrderMonthlyResponse> responses = OrderMonthlyResponse.fromList(rawData);
                                ApiResponse<List<OrderMonthlyResponse>> apiResponse = ApiResponse.success(
                                        "Monthly order data retrieved successfully", responses);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(apiResponse), CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_monthly_orders",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return apiResponse;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch monthly orders for yearMonth={}: {}", yearMonth, e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_orders",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to retrieve monthly order data: " + e.getMessage(), Collections.emptyList());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_orders"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<List<OrderYearlyResponse>>> findYearlyOrders(Integer yearMonth) {
        String cacheKey = "orders:soldout:yearly:" + yearMonth;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderYearlyResponse>> cached = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderYearlyResponse>>>() {});
                        return Uni.createFrom().item(cached);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findYearlyOrders")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_yearly_orders")
                            .setAttribute("yearMonth", yearMonth != null ? yearMonth.toString() : "null")
                            .startSpan();

                    return orderSoldOutRepository.findYearlyOrders(yearMonth)
                            .chain(rawData -> {
                                List<OrderYearlyResponse> responses = OrderYearlyResponse.fromList(rawData);
                                ApiResponse<List<OrderYearlyResponse>> apiResponse = ApiResponse.success(
                                        "Yearly order data retrieved successfully", responses);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(apiResponse), CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_yearly_orders",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return apiResponse;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch yearly orders for yearMonth={}: {}", yearMonth, e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_orders",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to retrieve yearly order data: " + e.getMessage(), Collections.emptyList());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_orders"));
                            });
                });
    }
}
