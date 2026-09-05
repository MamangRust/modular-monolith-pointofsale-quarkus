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
import com.sanedge.order.domain.requests.MonthOrderMerchantRequest;
import com.sanedge.order.domain.requests.YearOrderMerchantRequest;
import com.sanedge.order.domain.response.OrderMonthlyResponse;
import com.sanedge.order.domain.response.OrderYearlyResponse;
import com.sanedge.order.repository.statsbymerchant.OrderSoldOutByMerchantRepository;
import com.sanedge.order.service.statsbymerchant.OrderSoldOutByMerchantService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;


@ApplicationScoped
public class OrderSoldOutByMerchantServiceImpl implements OrderSoldOutByMerchantService {
    private static final Logger logger = LoggerFactory.getLogger(OrderSoldOutByMerchantServiceImpl.class);

    private final OrderSoldOutByMerchantRepository orderSoldOutByMerchantRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final TracingMetrics tracingMetrics;

    private static final long CACHE_TTL_SECONDS = 3600; // 1 hour for stats

    @Inject
    public OrderSoldOutByMerchantServiceImpl(OrderSoldOutByMerchantRepository orderSoldOutByMerchantRepository,
            RedisService redisService,
            ObjectMapper objectMapper,
            TracingMetrics tracingMetrics) {
        this.orderSoldOutByMerchantRepository = orderSoldOutByMerchantRepository;
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

    public Uni<ApiResponse<List<OrderMonthlyResponse>>> findMonthlyOrdersByMerchant(MonthOrderMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null) {
            logger.error("Missing required fields | req: {}", req);
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "Merchant ID and Year are required", Collections.emptyList()));
        }

        String cacheKey = String.format("orders:merchant:soldout:monthly:%d:%d", req.getMerchantId(), req.getYear());

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
                            .put("merchant.id", req.getMerchantId().toString())
                            .put("yearMonth", req.getYear().toString())
                            .build();

                    return runTraced("findMonthlyOrdersByMerchant", "find_merchant_monthly_orders", attrs,
                            () -> orderSoldOutByMerchantRepository
                                    .findMonthlyOrdersByMerchant(req.getMerchantId(), req.getYear())
                                    .chain(rawData -> {
                                        List<OrderMonthlyResponse> responses = OrderMonthlyResponse.fromList(rawData);
                                        ApiResponse<List<OrderMonthlyResponse>> apiResponse = ApiResponse.success(
                                                "Monthly order stats by merchant retrieved successfully", responses);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(apiResponse),
                                                        CACHE_TTL_SECONDS)
                                                .map(v -> {
                                                    logger.info("Found {} monthly order records for merchant",
                                                            responses.size());
                                                    return apiResponse;
                                                });
                                    })
                                    .onFailure().recoverWithItem(e -> {
                                        logger.error(
                                                "Failed to fetch monthly orders for merchant | merchantId={}, year={}: {}",
                                                req.getMerchantId(), req.getYear(), e.getMessage(), e);
                                        return new ApiResponse<>("error",
                                                "Failed to retrieve monthly order data: " + e.getMessage(),
                                                Collections.emptyList());
                                    }));
                });
    }

    @Override
    @WithTransaction

    public Uni<ApiResponse<List<OrderYearlyResponse>>> findYearlyOrdersByMerchant(YearOrderMerchantRequest req) {
        if (req.getMerchantId() == null || req.getYear() == null) {
            logger.error("Missing required fields | req: {}", req);
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "Merchant ID and Year are required", Collections.emptyList()));
        }

        String cacheKey = String.format("orders:merchant:soldout:yearly:%d:%d", req.getMerchantId(), req.getYear());

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
                            .put("merchant.id", req.getMerchantId().toString())
                            .put("year", req.getYear().toString())
                            .build();

                    return runTraced("findYearlyOrdersByMerchant", "find_merchant_yearly_orders", attrs,
                            () -> orderSoldOutByMerchantRepository
                                    .findYearlyOrdersByMerchant(req.getMerchantId(), req.getYear())
                                    .chain(rawData -> {
                                        List<OrderYearlyResponse> responses = OrderYearlyResponse.fromList(rawData);
                                        ApiResponse<List<OrderYearlyResponse>> apiResponse = ApiResponse.success(
                                                "Yearly order stats by merchant retrieved successfully", responses);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(apiResponse),
                                                        CACHE_TTL_SECONDS)
                                                .map(v -> {
                                                    logger.info("Found {} yearly order records for merchant",
                                                            responses.size());
                                                    return apiResponse;
                                                });
                                    })
                                    .onFailure().recoverWithItem(e -> {
                                        logger.error(
                                                "Failed to fetch yearly orders for merchant | merchantId={}, year={}: {}",
                                                req.getMerchantId(), req.getYear(), e.getMessage(), e);
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