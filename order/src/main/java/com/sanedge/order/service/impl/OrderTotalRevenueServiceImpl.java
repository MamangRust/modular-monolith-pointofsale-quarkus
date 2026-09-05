package com.sanedge.order.service.impl;

import java.time.LocalDate;
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
import com.sanedge.order.domain.requests.MonthTotalRevenue;
import com.sanedge.order.domain.requests.FindOrderMonthRange;
import com.sanedge.order.domain.response.OrderMonthlyTotalRevenueResponse;
import com.sanedge.order.domain.response.OrderYearlyTotalRevenueResponse;
import com.sanedge.order.repository.stats.OrderTotalRevenueRepository;
import com.sanedge.order.service.stats.OrderTotalRevenueService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;


@ApplicationScoped
public class OrderTotalRevenueServiceImpl implements OrderTotalRevenueService {
    private static final Logger logger = LoggerFactory.getLogger(OrderTotalRevenueServiceImpl.class);

    private final OrderTotalRevenueRepository orderTotalRevenueRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final TracingMetrics tracingMetrics;

    private static final long CACHE_TTL_SECONDS = 3600; // 1 hour for stats

    @Inject
    public OrderTotalRevenueServiceImpl(OrderTotalRevenueRepository orderTotalRevenueRepository,
            RedisService redisService,
            ObjectMapper objectMapper,
            TracingMetrics tracingMetrics) {
        this.orderTotalRevenueRepository = orderTotalRevenueRepository;
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

    public Uni<ApiResponse<List<OrderMonthlyTotalRevenueResponse>>> findMonthlyStats(MonthTotalRevenue req) {
        if (req.getYear() == null || req.getMonth() == null) {
            logger.error("Year or Month is null | req: {}", req);
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "Year and Month must not be null", Collections.emptyList()));
        }

        if (req.getMonth() < 1 || req.getMonth() > 12) {
            logger.error("Invalid Month={}", req.getMonth());
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "Month must be between 1 and 12", Collections.emptyList()));
        }

        String cacheKey = String.format("orders:revenue:monthly:%d:%d", req.getYear(), req.getMonth());

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
                            .put("year", req.getYear().toString())
                            .put("month", req.getMonth().toString())
                            .build();

                    LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                    LocalDate next = current.plusMonths(1);

                                        return runTraced("findMonthlyRevenueStats", "find_monthly_revenue", attrs,
                            () -> {
                                FindOrderMonthRange rangeReq = new FindOrderMonthRange();
                                rangeReq.setStartYear(req.getYear());
                                rangeReq.setStartMonth(req.getMonth());
                                rangeReq.setEndYear(next.getYear());
                                rangeReq.setEndMonth(next.getMonthValue());
                                return orderTotalRevenueRepository.findMonthlyTotalRevenue(rangeReq)
                                    .chain(rawData -> {
                                        List<OrderMonthlyTotalRevenueResponse> responses = OrderMonthlyTotalRevenueResponse
                                                .fromList(rawData);
                                        ApiResponse<List<OrderMonthlyTotalRevenueResponse>> apiResponse = ApiResponse
                                                .success(
                                                        "Monthly order stats retrieved successfully", responses);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(apiResponse),
                                                        CACHE_TTL_SECONDS)
                                                .map(v -> {
                                                    logger.info("Found {} monthly order stats", responses.size());
                                                    return apiResponse;
                                                });
                                    })
                                    .onFailure().recoverWithItem(e -> {
                                        logger.error("Failed to fetch monthly order stats | Year: {}, Month: {}: {}",
                                                req.getYear(), req.getMonth(), e.getMessage(), e);
                                        return new ApiResponse<>("error",
                                                "Failed to fetch monthly order stats: " + e.getMessage(),
                                                Collections.emptyList());
                                    });
                            });
                });
    }

    @Override
    @WithTransaction

    public Uni<ApiResponse<List<OrderYearlyTotalRevenueResponse>>> findYearlyStats(Integer year) {
        if (year == null) {
            logger.error("Year is null");
            return Uni.createFrom().item(new ApiResponse<>("error", "Year must not be null", Collections.emptyList()));
        }

        String cacheKey = "orders:revenue:yearly:" + year;

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
                            .put("year", year.toString())
                            .build();

                    return runTraced("findYearlyRevenueStats", "find_yearly_revenue", attrs,
                            () -> orderTotalRevenueRepository.findYearlyTotalRevenue(year)
                                    .chain(rawData -> {
                                        List<OrderYearlyTotalRevenueResponse> responses = OrderYearlyTotalRevenueResponse
                                                .fromList(rawData);
                                        ApiResponse<List<OrderYearlyTotalRevenueResponse>> apiResponse = ApiResponse
                                                .success(
                                                        "Yearly order stats retrieved successfully", responses);

                                        return redisService
                                                .setWithExpirationReactive(cacheKey, toJson(apiResponse),
                                                        CACHE_TTL_SECONDS)
                                                .map(v -> {
                                                    logger.info("Found {} yearly order stats", responses.size());
                                                    return apiResponse;
                                                });
                                    })
                                    .onFailure().recoverWithItem(e -> {
                                        logger.error("Failed to fetch yearly order stats | Year: {}: {}", year,
                                                e.getMessage(), e);
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