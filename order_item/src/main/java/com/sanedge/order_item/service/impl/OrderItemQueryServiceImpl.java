package com.sanedge.order_item.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.order_item.entity.OrderItem;
import com.sanedge.order_item.repository.OrderItemRepository;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;
import com.sanedge.order_item.service.OrderItemQueryService;

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
public class OrderItemQueryServiceImpl implements OrderItemQueryService {
    private static final Logger logger = LoggerFactory.getLogger(OrderItemQueryServiceImpl.class);

    OrderItemRepository orderItemRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long CACHE_TTL_SECONDS = 300;

    @Inject
    public OrderItemQueryServiceImpl(OrderItemRepository orderItemRepository,
                                     OpenTelemetry openTelemetry,
                                     RedisService redisService,
                                     ObjectMapper objectMapper) {
        this.orderItemRepository = orderItemRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("order-item-query-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("order-item-query-service");

        this.requestsTotal = meter.counterBuilder("order_item_query_requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("order_item_query_request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing object to JSON", e);
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON to object", e);
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    @Override
    public Uni<ApiResponse<PagedResult<OrderItemResponse>>> findAll(String search, int page, int pageSize) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findAllOrderItems")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-item-service")
                .setAttribute("operation", "find_all")
                .setAttribute("search", search != null ? search : "null")
                .setAttribute("page", String.valueOf(page))
                .setAttribute("pageSize", String.valueOf(pageSize))
                .startSpan();

        logger.info("🔍 Querying all order items: search={}, page={}, pageSize={}", search, page, pageSize);

        String cacheKey = String.format("order_item:all:%s:%d:%d", search != null ? search : "", page, pageSize);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("✨ Serving all order items from cache");
                        span.setAttribute("cache.hit", true);
                        ApiResponse<PagedResult<OrderItemResponse>> cached = fromJson(
                                cachedJson,
                                new TypeReference<ApiResponse<PagedResult<OrderItemResponse>>>() {}
                        );
                        return Uni.createFrom().item(cached);
                    }
                    span.setAttribute("cache.hit", false);

                    return orderItemRepository.findOrderItems(search, page, pageSize)
                            .map(pagedResult -> {
                                List<OrderItemResponse> responses = pagedResult.getData().stream()
                                        .map(OrderItemResponse::from)
                                        .collect(Collectors.toList());
                                PagedResult<OrderItemResponse> result = new PagedResult<>(responses, pagedResult.getTotalRecords());
                                return ApiResponse.success("Order items retrieved successfully", result);
                            })
                            .chain(res -> redisService.setWithExpirationReactive(cacheKey, toJson(res), CACHE_TTL_SECONDS).map(v -> res));
                })
                .map(res -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_all",
                            AttributeKey.stringKey("status"), "success"));
                    return res;
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to query all order items", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_all",
                            AttributeKey.stringKey("status"), "failed"));
                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_all"));
                });
    }

    @Override
    public Uni<ApiResponse<PagedResult<OrderItemResponseDeleteAt>>> findByActive(String search, int page, int pageSize) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findActiveOrderItems")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-item-service")
                .setAttribute("operation", "find_active")
                .setAttribute("search", search != null ? search : "null")
                .setAttribute("page", String.valueOf(page))
                .setAttribute("pageSize", String.valueOf(pageSize))
                .startSpan();

        logger.info("🔍 Querying active order items: search={}, page={}, pageSize={}", search, page, pageSize);

        String cacheKey = String.format("order_item:active:%s:%d:%d", search != null ? search : "", page, pageSize);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("✨ Serving active order items from cache");
                        span.setAttribute("cache.hit", true);
                        ApiResponse<PagedResult<OrderItemResponseDeleteAt>> cached = fromJson(
                                cachedJson,
                                new TypeReference<ApiResponse<PagedResult<OrderItemResponseDeleteAt>>>() {}
                        );
                        return Uni.createFrom().item(cached);
                    }
                    span.setAttribute("cache.hit", false);

                    return orderItemRepository.findActiveOrderItems(search, page, pageSize)
                            .map(pagedResult -> {
                                List<OrderItemResponseDeleteAt> responses = pagedResult.getData().stream()
                                        .map(OrderItemResponseDeleteAt::from)
                                        .collect(Collectors.toList());
                                PagedResult<OrderItemResponseDeleteAt> cleanResult = new PagedResult<>(responses, pagedResult.getTotalRecords());
                                return ApiResponse.success("Active order items retrieved successfully", cleanResult);
                            })
                            .chain(res -> redisService.setWithExpirationReactive(cacheKey, toJson(res), CACHE_TTL_SECONDS).map(v -> res));
                })
                .map(res -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_active",
                            AttributeKey.stringKey("status"), "success"));
                    return res;
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to query active order items", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_active",
                            AttributeKey.stringKey("status"), "failed"));
                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_active"));
                });
    }

    @Override
    public Uni<ApiResponse<PagedResult<OrderItemResponseDeleteAt>>> findByTrashed(String search, int page, int pageSize) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findTrashedOrderItems")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-item-service")
                .setAttribute("operation", "find_trashed")
                .setAttribute("search", search != null ? search : "null")
                .setAttribute("page", String.valueOf(page))
                .setAttribute("pageSize", String.valueOf(pageSize))
                .startSpan();

        logger.info("🔍 Querying trashed order items: search={}, page={}, pageSize={}", search, page, pageSize);

        String cacheKey = String.format("order_item:trashed:%s:%d:%d", search != null ? search : "", page, pageSize);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("✨ Serving trashed order items from cache");
                        span.setAttribute("cache.hit", true);
                        ApiResponse<PagedResult<OrderItemResponseDeleteAt>> cached = fromJson(
                                cachedJson,
                                new TypeReference<ApiResponse<PagedResult<OrderItemResponseDeleteAt>>>() {}
                        );
                        return Uni.createFrom().item(cached);
                    }
                    span.setAttribute("cache.hit", false);

                    return orderItemRepository.findTrashedOrderItems(search, page, pageSize)
                            .map(pagedResult -> {
                                List<OrderItemResponseDeleteAt> responses = pagedResult.getData().stream()
                                        .map(OrderItemResponseDeleteAt::from)
                                        .collect(Collectors.toList());
                                PagedResult<OrderItemResponseDeleteAt> cleanResult = new PagedResult<>(responses, pagedResult.getTotalRecords());
                                return ApiResponse.success("Trashed order items retrieved successfully", cleanResult);
                            })
                            .chain(res -> redisService.setWithExpirationReactive(cacheKey, toJson(res), CACHE_TTL_SECONDS).map(v -> res));
                })
                .map(res -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_trashed",
                            AttributeKey.stringKey("status"), "success"));
                    return res;
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to query trashed order items", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_trashed",
                            AttributeKey.stringKey("status"), "failed"));
                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_trashed"));
                });
    }

    @Override
    public Uni<ApiResponse<List<OrderItemResponse>>> findOrderItemByOrder(Integer orderId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("findOrderItemByOrder")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-item-service")
                .setAttribute("operation", "find_by_order")
                .setAttribute("order.id", String.valueOf(orderId))
                .startSpan();

        logger.info("🔍 Querying order items by order ID={}", orderId);

        String cacheKey = String.format("order_item:by_order:%d", orderId);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("✨ Serving order items by order from cache");
                        span.setAttribute("cache.hit", true);
                        ApiResponse<List<OrderItemResponse>> cached = fromJson(
                                cachedJson,
                                new TypeReference<ApiResponse<List<OrderItemResponse>>>() {}
                        );
                        return Uni.createFrom().item(cached);
                    }
                    span.setAttribute("cache.hit", false);

                    return orderItemRepository.findOrderItemByOrder(orderId.longValue())
                            .map(list -> {
                                List<OrderItemResponse> responses = list.stream()
                                        .map(OrderItemResponse::from)
                                        .collect(Collectors.toList());
                                return ApiResponse.success("Order items by order retrieved successfully", responses);
                            })
                            .chain(res -> redisService.setWithExpirationReactive(cacheKey, toJson(res), CACHE_TTL_SECONDS).map(v -> res));
                })
                .map(res -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_by_order",
                            AttributeKey.stringKey("status"), "success"));
                    return res;
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to query order items by order ID={}", orderId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_by_order",
                            AttributeKey.stringKey("status"), "failed"));
                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "find_by_order"));
                });
    }
}
