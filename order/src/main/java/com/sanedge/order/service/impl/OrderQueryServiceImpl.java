package com.sanedge.order.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.order.domain.requests.FindAllOrderByMerchantRequest;
import com.sanedge.order.domain.requests.FindAllOrderRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.domain.response.PaginationMeta;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.domain.response.OrderResponseDeleteAt;
import com.sanedge.order.entity.Order;
import com.sanedge.order.repository.OrderQueryRepository;
import com.sanedge.order.service.OrderQueryService;

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
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class OrderQueryServiceImpl implements OrderQueryService {
    private static final Logger logger = LoggerFactory.getLogger(OrderQueryServiceImpl.class);

    OrderQueryRepository orderQueryRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long LIST_CACHE_TTL_SECONDS = 300;

    @Inject
    public OrderQueryServiceImpl(OrderQueryRepository orderQueryRepository,
                                 OpenTelemetry openTelemetry,
                                 RedisService redisService,
                                 ObjectMapper objectMapper) {
        this.orderQueryRepository = orderQueryRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("order-query-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("order-query-service");

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
            logger.error("Error serializing object to JSON", e);
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON to object", e);
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON to object with TypeReference", e);
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    @Override
    public Uni<ApiResponsePagination<List<OrderResponse>>> findAll(FindAllOrderRequest req) {
        String cacheKey = String.format("orders:all:%d:%d:%s", req.getPage(), req.getPageSize(),
                req.getSearch() != null ? req.getSearch() : "");

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<OrderResponse>> response = fromJson(
                                cachedJson,
                                new TypeReference<ApiResponsePagination<List<OrderResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findAllOrders")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_all_orders")
                            .startSpan();

                    String searchKeyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

                    return orderQueryRepository.findOrders(searchKeyword, req.getPage(), req.getPageSize())
                            .chain(pagedResult -> {
                                span.setAttribute("order.count", pagedResult.getTotalRecords());
                                span.setAttribute("order.page", req.getPage());
                                span.setAttribute("order.size", req.getPageSize());

                                ApiResponsePagination<List<OrderResponse>> response = buildPaginatedResponse(
                                        pagedResult, req.getPage(), req.getPageSize(),
                                        "Orders retrieved successfully",
                                        OrderResponse::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached response for key: {}", cacheKey);
                                            logger.info("Successfully retrieved {} orders", pagedResult.getTotalRecords());
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_all_orders",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch orders: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_all_orders",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to fetch orders: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_all_orders"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<OrderResponseDeleteAt>>> findByActive(FindAllOrderRequest req) {
        String cacheKey = String.format("orders:active:%d:%d:%s", req.getPage(), req.getPageSize(),
                req.getSearch() != null ? req.getSearch() : "");

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<OrderResponseDeleteAt>> response = fromJson(
                                cachedJson,
                                new TypeReference<ApiResponsePagination<List<OrderResponseDeleteAt>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findActiveOrders")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_active_orders")
                            .startSpan();

                    String searchKeyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

                    return orderQueryRepository.findActiveOrders(searchKeyword, req.getPage(), req.getPageSize())
                            .chain(pagedResult -> {
                                span.setAttribute("order.count", pagedResult.getTotalRecords());
                                span.setAttribute("order.page", req.getPage());
                                span.setAttribute("order.size", req.getPageSize());

                                ApiResponsePagination<List<OrderResponseDeleteAt>> response = buildPaginatedResponse(
                                        pagedResult, req.getPage(), req.getPageSize(),
                                        "Active orders retrieved successfully",
                                        OrderResponseDeleteAt::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached response for key: {}", cacheKey);
                                            logger.info("Successfully retrieved {} active orders", pagedResult.getTotalRecords());
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_active_orders",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch active orders: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_active_orders",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to fetch active orders: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_active_orders"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<OrderResponseDeleteAt>>> findByTrashed(FindAllOrderRequest req) {
        String cacheKey = String.format("orders:trashed:%d:%d:%s", req.getPage(), req.getPageSize(),
                req.getSearch() != null ? req.getSearch() : "");

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<OrderResponseDeleteAt>> response = fromJson(
                                cachedJson,
                                new TypeReference<ApiResponsePagination<List<OrderResponseDeleteAt>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findTrashedOrders")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_trashed_orders")
                            .startSpan();

                    String searchKeyword = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";

                    return orderQueryRepository.findTrashedOrders(searchKeyword, req.getPage(), req.getPageSize())
                            .chain(pagedResult -> {
                                span.setAttribute("order.count", pagedResult.getTotalRecords());
                                span.setAttribute("order.page", req.getPage());
                                span.setAttribute("order.size", req.getPageSize());

                                ApiResponsePagination<List<OrderResponseDeleteAt>> response = buildPaginatedResponse(
                                        pagedResult, req.getPage(), req.getPageSize(),
                                        "Trashed orders retrieved successfully",
                                        OrderResponseDeleteAt::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached response for key: {}", cacheKey);
                                            logger.info("Successfully retrieved {} trashed orders", pagedResult.getTotalRecords());
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_trashed_orders",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch trashed orders: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_trashed_orders",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to fetch trashed orders: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_trashed_orders"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<OrderResponse>>> findByMerchantId(FindAllOrderByMerchantRequest req) {
        String cacheKey = String.format("orders:merchant:%d:%d:%d:%s",
                req.getMerchantId(),
                req.getPage() != null ? req.getPage() : 1,
                req.getPageSize() != null ? req.getPageSize() : 10,
                req.getSearch() != null ? req.getSearch() : "");

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<OrderResponse>> response = fromJson(
                                cachedJson,
                                new TypeReference<ApiResponsePagination<List<OrderResponse>>>() {
                                });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findOrdersByMerchant")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_orders_by_merchant")
                            .setAttribute("merchant.id", req.getMerchantId() != null ? req.getMerchantId().toString() : "null")
                            .startSpan();

                    return orderQueryRepository.findOrdersByMerchant(req)
                            .chain(pagedResult -> {
                                span.setAttribute("order.count", pagedResult.getTotalRecords());
                                span.setAttribute("order.page", req.getPage());
                                span.setAttribute("order.size", req.getPageSize());

                                ApiResponsePagination<List<OrderResponse>> response = buildPaginatedResponse(
                                        pagedResult, req.getPage(), req.getPageSize(),
                                        "Orders retrieved successfully",
                                        OrderResponse::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached response for key: {}", cacheKey);
                                            logger.info("Successfully retrieved {} merchant orders", pagedResult.getTotalRecords());
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_orders_by_merchant",
                                                    AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch orders for merchantId={}: {}", req.getMerchantId(), e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_orders_by_merchant",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to fetch orders for merchant: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_orders_by_merchant"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<OrderResponse>> findById(Integer id) {
        String cacheKey = "order:" + id;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        OrderResponse response = fromJson(cachedJson, OrderResponse.class);
                        return Uni.createFrom().item(ApiResponse.success("Order retrieved successfully", response));
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findOrderById")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "order-service")
                            .setAttribute("operation", "find_order_by_id")
                            .setAttribute("order.id", id.toString())
                            .startSpan();

                    return orderQueryRepository.findOrderById(id.longValue())
                            .chain(order -> {
                                if (order == null) {
                                    logger.warn("❌ Order not found with id={}", id);
                                    span.setStatus(StatusCode.ERROR, "Order not found");
                                    span.setAttribute("order.found", false);

                                    requestsTotal.add(1, Attributes.of(
                                            AttributeKey.stringKey("operation"), "find_order_by_id",
                                            AttributeKey.stringKey("status"), "failed",
                                            AttributeKey.stringKey("error_type"), "not_found"));

                                    throw new NotFoundException("Order not found with id: " + id);
                                }

                                span.setAttribute("order.found", true);
                                OrderResponse response = OrderResponse.from(order);

                                return redisService.setReactive(cacheKey, toJson(response))
                                        .map(v -> {
                                            logger.info("Cached order for key: {}", cacheKey);
                                            logger.info("Successfully found order with id: {}", id);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_order_by_id",
                                                    AttributeKey.stringKey("status"), "success"));

                                            return ApiResponse.success("Order retrieved successfully", response);
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch order by id={}: {}", id, e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_order_by_id",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to fetch order: " + e.getMessage(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_order_by_id"));
                            });
                });
    }

    private <T, R> ApiResponsePagination<List<R>> buildPaginatedResponse(
            PagedResult<T> pagedResult,
            Integer page,
            Integer pageSize,
            String successMessage,
            Function<T, R> mapper) {

        List<R> data = pagedResult.getData().stream()
                .map(mapper)
                .collect(Collectors.toList());

        int totalRecords = pagedResult.getTotalRecords();
        int size = pageSize != null && pageSize > 0 ? pageSize : 1;
        int totalPages = (int) Math.ceil((double) totalRecords / size);

        PaginationMeta pagination = new PaginationMeta(page != null ? page : 1, size, totalPages, totalRecords);

        return new ApiResponsePagination<>("success", successMessage, data, pagination);
    }
}
