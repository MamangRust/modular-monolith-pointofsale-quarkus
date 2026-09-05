package com.sanedge.transaction.service.impl.stats;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.transaction.domain.requests.FindTransactionMonthRange;
import com.sanedge.transaction.domain.requests.MonthAmountTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountSuccessResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountSuccessResponse;
import com.sanedge.transaction.repository.stats.TransactionAmountStatusRepository;
import com.sanedge.transaction.service.stats.TransactionAmountService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionAmountServiceImpl implements TransactionAmountService {
        private static final Logger logger = LoggerFactory.getLogger(TransactionAmountServiceImpl.class);

        private final TransactionAmountStatusRepository transactionAmountStatusRepository;
        private final RedisService redisService;
        private final ObjectMapper objectMapper;
        private final TracingMetrics tracingMetrics;

        private static final long STATS_CACHE_TTL_SECONDS = 3600; // 1 hour

        @Inject
        public TransactionAmountServiceImpl(TransactionAmountStatusRepository transactionAmountStatusRepository,
                        RedisService redisService,
                        ObjectMapper objectMapper,
                        TracingMetrics tracingMetrics) {
                this.transactionAmountStatusRepository = transactionAmountStatusRepository;
                this.redisService = redisService;
                this.objectMapper = objectMapper;
                this.tracingMetrics = tracingMetrics;
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
                        logger.error("Error deserializing JSON with TypeReference", e);
                        throw new RuntimeException("Failed to deserialize JSON", e);
                }
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>> findMonthlyAmountSuccess(
                        MonthAmountTransactionRequest req) {
                if (req.getYear() == null || req.getMonth() == null) {
                        logger.error("Year or Month is null | req: {}", req);
                        return Uni.createFrom()
                                        .item(new ApiResponse<>("error", "Year and Month must not be null",
                                                        Collections.emptyList()));
                }

                if (req.getMonth() < 1 || req.getMonth() > 12) {
                        return Uni.createFrom()
                                        .item(new ApiResponse<>("error", "Month must be between 1 and 12",
                                                        Collections.emptyList()));
                }

                logger.info("Fetching monthly SUCCESS transaction amount | year={}, month={}", req.getYear(),
                                req.getMonth());
                String cacheKey = String.format("transactions:stats:amount:monthly:success:y%d:m%d", req.getYear(),
                                req.getMonth());
                Attributes attrs = Attributes.builder()
                                .put("year", req.getYear())
                                .put("month", req.getMonth())
                                .build();

                LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                LocalDate prev = current.minusMonths(1);

                FindTransactionMonthRange rangeReq = new FindTransactionMonthRange();
                rangeReq.setStartYear(req.getYear());
                rangeReq.setStartMonth(req.getMonth());
                rangeReq.setEndYear(prev.getYear());
                rangeReq.setEndMonth(prev.getMonthValue());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        return runTraced("findMonthlyAmountSuccess", "find_monthly_amount_success",
                                                        attrs,
                                                        () -> transactionAmountStatusRepository
                                                                        .findMonthlyTransactionSuccess(rangeReq)
                                                                        .chain(rawData -> {
                                                                                List<TransactionMonthlyAmountSuccessResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionMonthlyAmountSuccessResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Monthly success transaction amount retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info(
                                                                                                                        "Found {} monthly SUCCESS transaction records",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch monthly SUCCESS transaction amount: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve monthly success transaction data: "
                                                                                                                + e.getMessage(),
                                                                                                Collections.emptyList());
                                                                        }));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>> findYearlyAmountSuccess(Integer year) {
                if (year == null) {
                        logger.error("Year is null");
                        return Uni.createFrom()
                                        .item(new ApiResponse<>("error", "Year must not be null",
                                                        Collections.emptyList()));
                }

                logger.info("Fetching yearly SUCCESS transaction amount | year={}", year);
                String cacheKey = String.format("transactions:stats:amount:yearly:success:y%d", year);
                Attributes attrs = Attributes.builder()
                                .put("year", year)
                                .build();

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        return runTraced("findYearlyAmountSuccess", "find_yearly_amount_success", attrs,
                                                        () -> transactionAmountStatusRepository
                                                                        .findYearlyTransactionSuccess(year)
                                                                        .chain(rawData -> {
                                                                                List<TransactionYearlyAmountSuccessResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionYearlyAmountSuccessResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Yearly success transaction amount retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} yearly SUCCESS transaction records",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch yearly SUCCESS transaction amount: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve yearly success transaction data: "
                                                                                                                + e.getMessage(),
                                                                                                Collections.emptyList());
                                                                        }));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>> findMonthlyAmountFailed(
                        MonthAmountTransactionRequest req) {
                if (req.getYear() == null || req.getMonth() == null) {
                        logger.error("Year or Month is null | req: {}", req);
                        return Uni.createFrom()
                                        .item(new ApiResponse<>("error", "Year and Month must not be null",
                                                        Collections.emptyList()));
                }

                if (req.getMonth() < 1 || req.getMonth() > 12) {
                        return Uni.createFrom()
                                        .item(new ApiResponse<>("error", "Month must be between 1 and 12",
                                                        Collections.emptyList()));
                }

                logger.info("Fetching monthly FAILED transaction amount | year={}, month={}", req.getYear(),
                                req.getMonth());
                String cacheKey = String.format("transactions:stats:amount:monthly:failed:y%d:m%d", req.getYear(),
                                req.getMonth());
                Attributes attrs = Attributes.builder()
                                .put("year", req.getYear())
                                .put("month", req.getMonth())
                                .build();

                LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                LocalDate prev = current.minusMonths(1);

                FindTransactionMonthRange rangeReq = new FindTransactionMonthRange();
                rangeReq.setStartYear(req.getYear());
                rangeReq.setStartMonth(req.getMonth());
                rangeReq.setEndYear(prev.getYear());
                rangeReq.setEndMonth(prev.getMonthValue());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<TransactionMonthlyAmountFailedResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        return runTraced("findMonthlyAmountFailed", "find_monthly_amount_failed", attrs,
                                                        () -> transactionAmountStatusRepository
                                                                        .findMonthlyTransactionFailed(rangeReq)
                                                                        .chain(rawData -> {
                                                                                List<TransactionMonthlyAmountFailedResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionMonthlyAmountFailedResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionMonthlyAmountFailedResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Monthly failed transaction amount retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} monthly FAILED transaction records",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch monthly FAILED transaction amount: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve monthly failed transaction data: "
                                                                                                                + e.getMessage(),
                                                                                                Collections.emptyList());
                                                                        }));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionYearlyAmountFailedResponse>>> findYearlyAmountFailed(Integer year) {
                if (year == null) {
                        logger.error("Year is null");
                        return Uni.createFrom()
                                        .item(new ApiResponse<>("error", "Year must not be null",
                                                        Collections.emptyList()));
                }

                logger.info("Fetching yearly FAILED transaction amount | year={}", year);
                String cacheKey = String.format("transactions:stats:amount:yearly:failed:y%d", year);
                Attributes attrs = Attributes.builder()
                                .put("year", year)
                                .build();

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<TransactionYearlyAmountFailedResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionYearlyAmountFailedResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        return runTraced("findYearlyAmountFailed", "find_yearly_amount_failed", attrs,
                                                        () -> transactionAmountStatusRepository
                                                                        .findYearlyTransactionFailed(year)
                                                                        .chain(rawData -> {
                                                                                List<TransactionYearlyAmountFailedResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionYearlyAmountFailedResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionYearlyAmountFailedResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Yearly failed transaction amount retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} yearly FAILED transaction records",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch yearly FAILED transaction amount: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve yearly failed transaction data: "
                                                                                                                + e.getMessage(),
                                                                                                Collections.emptyList());
                                                                        }));
                                });
        }

        private <T> Uni<T> runTraced(String operationName, String method, Attributes attributes,
                        Supplier<Uni<T>> supplier) {
                return tracingMetrics.traceAndMeasure(operationName, method, attributes, supplier);
        }
}