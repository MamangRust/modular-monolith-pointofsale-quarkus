package com.sanedge.order.service.impl;

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
import com.sanedge.order.domain.response.OrderMonthlyResponse;
import com.sanedge.order.domain.response.OrderYearlyResponse;
import com.sanedge.order.repository.stats.OrderSoldOutRepository;
import com.sanedge.order.service.stats.OrderSoldoutService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;


@ApplicationScoped
public class OrderSoldOutServiceImpl implements OrderSoldoutService {
    private static final Logger logger = LoggerFactory.getLogger(OrderSoldOutServiceImpl.class);

    private final OrderSoldOutRepository orderSoldOutRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final TracingMetrics tracingMetrics;

    private static final long CACHE_TTL_SECONDS = 3600; // 1 hour for stats

    @Inject
    public OrderSoldOutServiceImpl(OrderSoldOutRepository orderSoldOutRepository,
            RedisService redisService,
            ObjectMapper objectMapper,
            TracingMetrics tracingMetrics) {
        this.orderSoldOutRepository = orderSoldOutRepository;
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

    public Uni<ApiResponse<List<OrderMonthlyResponse>>> findMonthlyOrders(Integer yearMonth) {
        if (yearMonth == null) {
            logger.error("YearMonth is null");
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "YearMonth must not be null", Collections.emptyList()));
        }

        String cacheKey = "orders:soldout:monthly:" + yearMonth;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderMonthlyResponse>> cached = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderMonthlyResponse>>>() {
                                });
                        return Uni.createFrom().item(cached);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    Attributes attrs = Attributes.builder()
                            .put("yearMonth", yearMonth.toString())
                            .build();

                    return runTraced("findMonthlyOrders", "find_monthly_orders", attrs,
                            () -> orderSoldOutRepository.findMonthlyOrdersByYear(yearMonth)
                                    .chain(rawData -> {
                                        List<OrderMonthlyResponse> responses = OrderMonthlyResponse.fromList(rawData);
                                        ApiResponse<List<OrderMonthlyResponse>> apiResponse = ApiResponse.success(
                                                "Monthly order data retrieved successfully", responses);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(apiResponse),
                                                        CACHE_TTL_SECONDS)
                                                .map(v -> {
                                                    logger.info("Found {} monthly orders for yearMonth={}",
                                                            responses.size(), yearMonth);
                                                    return apiResponse;
                                                });
                                    })
                                    .onFailure().recoverWithItem(e -> {
                                        logger.error("Failed to fetch monthly orders for yearMonth={}: {}", yearMonth,
                                                e.getMessage(), e);
                                        return new ApiResponse<>("error",
                                                "Failed to retrieve monthly order data: " + e.getMessage(),
                                                Collections.emptyList());
                                    }));
                });
    }

    @Override
    @WithTransaction

    public Uni<ApiResponse<List<OrderYearlyResponse>>> findYearlyOrders(Integer yearMonth) {
        if (yearMonth == null) {
            logger.error("YearMonth is null");
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "YearMonth must not be null", Collections.emptyList()));
        }

        String cacheKey = "orders:soldout:yearly:" + yearMonth;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<OrderYearlyResponse>> cached = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<OrderYearlyResponse>>>() {
                                });
                        return Uni.createFrom().item(cached);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    Attributes attrs = Attributes.builder()
                            .put("yearMonth", yearMonth.toString())
                            .build();

                    return runTraced("findYearlyOrders", "find_yearly_orders", attrs,
                            () -> orderSoldOutRepository.findYearlyOrders(yearMonth)
                                    .chain(rawData -> {
                                        List<OrderYearlyResponse> responses = OrderYearlyResponse.fromList(rawData);
                                        ApiResponse<List<OrderYearlyResponse>> apiResponse = ApiResponse.success(
                                                "Yearly order data retrieved successfully", responses);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(apiResponse),
                                                        CACHE_TTL_SECONDS)
                                                .map(v -> {
                                                    logger.info("Found {} yearly orders for yearMonth={}",
                                                            responses.size(), yearMonth);
                                                    return apiResponse;
                                                });
                                    })
                                    .onFailure().recoverWithItem(e -> {
                                        logger.error("Failed to fetch yearly orders for yearMonth={}: {}", yearMonth,
                                                e.getMessage(), e);
                                        return new ApiResponse<>("error",
                                                "Failed to retrieve yearly order data: " + e.getMessage(),
                                                Collections.emptyList());
                                    }));
                });
    }

    private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
            Supplier<Uni<T>> supplier) {
        return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
    }
}