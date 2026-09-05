package com.sanedge.order.service.impl.statsbyid;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order.domain.requests.MonthTotalRevenueByIdRequest;
import com.sanedge.order.domain.requests.YearTotalRevenueByIdRequest;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.repository.stats.OrderTotalRevenueRepository;
import com.sanedge.order.service.statsbyid.OrderTotalRevenueByIdService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrderTotalRevenueByIdServiceImpl implements OrderTotalRevenueByIdService {
    private static final Logger logger = LoggerFactory.getLogger(OrderTotalRevenueByIdServiceImpl.class);

    private final OrderTotalRevenueRepository repository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final TracingMetrics tracingMetrics;

    private static final long CACHE_TTL_SECONDS = 3600; // 1 hour for stats

    @Inject
    public OrderTotalRevenueByIdServiceImpl(OrderTotalRevenueRepository repository,
            RedisService redisService,
            ObjectMapper objectMapper,
            TracingMetrics tracingMetrics) {
        this.repository = repository;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracingMetrics = tracingMetrics;
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
    @WithTransaction
    public Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyStatsById(MonthTotalRevenueByIdRequest req) {
        if (req.getOrderId() == null || req.getYear() == null || req.getMonth() == null) {
            logger.error("OrderId, Year, or Month is null | req: {}", req);
            return Uni.createFrom().item(new ApiResponse<>("error",
                    "OrderId, Year, and Month must not be null", Collections.emptyList()));
        }

        if (req.getMonth() < 1 || req.getMonth() > 12) {
            logger.error("Invalid Month={}", req.getMonth());
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "Month must be between 1 and 12", Collections.emptyList()));
        }

        String cacheKey = String.format("orders:revenue:monthly:byid:%d:%d:%d", req.getOrderId(), req.getYear(),
                req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> cached = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>>() {
                                });
                        return Uni.createFrom().item(cached);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    Attributes attrs = Attributes.builder()
                            .put("order.id", req.getOrderId().toString())
                            .put("year", req.getYear().toString())
                            .put("month", req.getMonth().toString())
                            .build();

                    return runTraced("findMonthlyStatsById", "find_order_byid_monthly_revenue", attrs,
                            () -> repository.findMonthlyTotalRevenueById(req.getOrderId(), req.getYear(), req.getMonth())
                                    .chain(rawData -> {
                                        List<OrderMonthlyTotalRevenueResponse> responses = OrderMonthlyTotalRevenueResponse
                                                .fromList(rawData);
                                        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> apiResponse = ApiResponse
                                                .success(
                                                        "Monthly order stats for order retrieved successfully",
                                                        responses);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(apiResponse),
                                                        CACHE_TTL_SECONDS)
                                                .map(v -> {
                                                    logger.info("Found {} monthly order stats for orderId={}",
                                                            responses.size(), req.getOrderId());
                                                    return apiResponse;
                                                });
                                    })
                                    .onFailure().recoverWithItem(e -> {
                                        logger.error("Failed to fetch monthly order stats for orderId={}: {}",
                                                req.getOrderId(), e.getMessage(), e);
                                        return new ApiResponse<>("error",
                                                "Failed to fetch monthly order stats: " + e.getMessage(),
                                                Collections.emptyList());
                                    }));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyStatsById(YearTotalRevenueByIdRequest req) {
        if (req.getOrderId() == null || req.getYear() == null) {
            logger.error("OrderId or Year is null | req: {}", req);
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "OrderId and Year must not be null", Collections.emptyList()));
        }

        String cacheKey = String.format("orders:revenue:yearly:byid:%d:%d", req.getOrderId(), req.getYear());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderYearlyTotalRevenueResponse>> cached = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderYearlyTotalRevenueResponse>>>() {
                                });
                        return Uni.createFrom().item(cached);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    Attributes attrs = Attributes.builder()
                            .put("order.id", req.getOrderId().toString())
                            .put("year", req.getYear().toString())
                            .build();

                    return runTraced("findYearlyStatsById", "find_order_byid_yearly_revenue", attrs,
                            () -> repository.findYearlyTotalRevenueById(req.getOrderId(), req.getYear())
                                    .chain(rawData -> {
                                        List<OrderYearlyTotalRevenueResponse> responses = OrderYearlyTotalRevenueResponse
                                                .fromList(rawData);
                                        ApiResponse<List<OrderYearlyTotalRevenueResponse>> apiResponse = ApiResponse
                                                .success(
                                                        "Yearly order stats for order retrieved successfully",
                                                        responses);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(apiResponse),
                                                        CACHE_TTL_SECONDS)
                                                .map(v -> {
                                                    logger.info("Found {} yearly order stats for orderId={}",
                                                            responses.size(), req.getOrderId());
                                                    return apiResponse;
                                                });
                                    })
                                    .onFailure().recoverWithItem(e -> {
                                        logger.error("Failed to fetch yearly order stats for orderId={}: {}",
                                                req.getOrderId(), e.getMessage(), e);
                                        return new ApiResponse<>("error",
                                                "Failed to fetch yearly order stats: " + e.getMessage(),
                                                Collections.emptyList());
                                    }));
                });
    }

    private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
            Supplier<Uni<T>> supplier) {
        return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
    }
}
