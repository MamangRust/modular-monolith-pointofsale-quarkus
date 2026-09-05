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
import com.sanedge.transaction.domain.requests.MonthMethodTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;
import com.sanedge.transaction.repository.stats.TransactionMethodRepository;
import com.sanedge.transaction.service.stats.TransactionMethodService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionMethodServiceImpl implements TransactionMethodService {
        private static final Logger logger = LoggerFactory.getLogger(TransactionMethodServiceImpl.class);

        private final TransactionMethodRepository transactionMethodRepository;
        private final RedisService redisService;
        private final ObjectMapper objectMapper;
        private final TracingMetrics tracingMetrics;

        private static final long STATS_CACHE_TTL_SECONDS = 3600; // 1 hour

        @Inject
        public TransactionMethodServiceImpl(TransactionMethodRepository transactionMethodRepository,
                        RedisService redisService,
                        ObjectMapper objectMapper,
                        TracingMetrics tracingMetrics) {
                this.transactionMethodRepository = transactionMethodRepository;
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
        public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodSuccess(
                        MonthMethodTransactionRequest req) {
                if (req.getYear() == null || req.getMonth() == null) {
                        logger.error("Year or Month is null | req: {}", req);
                        return Uni.createFrom().item(new ApiResponse<>("error", "Year and Month must not be null",
                                        Collections.emptyList()));
                }

                if (req.getMonth() < 1 || req.getMonth() > 12) {
                        return Uni.createFrom().item(new ApiResponse<>("error", "Month must be between 1 and 12",
                                        Collections.emptyList()));
                }

                logger.info("Fetching monthly SUCCESS transaction by method | year={}, month={}", req.getYear(),
                                req.getMonth());
                String cacheKey = String.format("transactions:stats:method:monthly:success:y%d:m%d", req.getYear(),
                                req.getMonth());
                Attributes attrs = Attributes.builder()
                                .put("year", req.getYear())
                                .put("month", req.getMonth())
                                .build();

                LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                LocalDate prev = current.minusMonths(1);

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionMonthlyMethodResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        return runTraced("findMonthlyMethodSuccess", "find_monthly_method_success",
                                                        attrs,
                                                        () -> {
                                                                FindTransactionMonthRange rangeReq = new FindTransactionMonthRange();
                                                                rangeReq.setStartYear(req.getYear());
                                                                rangeReq.setStartMonth(req.getMonth());
                                                                rangeReq.setEndYear(prev.getYear());
                                                                rangeReq.setEndMonth(prev.getMonthValue());
                                                                return transactionMethodRepository.findMonthlyMethodsSuccess(rangeReq);
                                                        })
                                                                        .chain(rawData -> {
                                                                                List<TransactionMonthlyMethodResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionMonthlyMethodResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Monthly success transaction by method retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} monthly SUCCESS transaction records by method",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch monthly SUCCESS transaction by method: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve monthly success transaction data by method: "
                                                                                                                + e.getMessage(),
                                                                                                Collections.emptyList());
                                                                        });
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodSuccess(Integer year) {
                if (year == null) {
                        logger.error("Year is null");
                        return Uni.createFrom().item(
                                        new ApiResponse<>("error", "Year must not be null", Collections.emptyList()));
                }

                logger.info("Fetching yearly SUCCESS transaction by method | year={}", year);
                String cacheKey = String.format("transactions:stats:method:yearly:success:y%d", year);
                Attributes attrs = Attributes.builder()
                                .put("year", year)
                                .build();

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<TransactionYearlyMethodResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionYearlyMethodResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        return runTraced("findYearlyMethodSuccess", "find_yearly_method_success", attrs,
                                                        () -> transactionMethodRepository.findYearlyMethodsSuccess(year)
                                                                        .chain(rawData -> {
                                                                                List<TransactionYearlyMethodResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionYearlyMethodResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionYearlyMethodResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Yearly success transaction by method retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} yearly SUCCESS transaction records by method",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch yearly SUCCESS transaction by method: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve yearly success transaction data by method: "
                                                                                                                + e.getMessage(),
                                                                                                Collections.emptyList());
                                                                        }));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodFailed(
                        MonthMethodTransactionRequest req) {
                if (req.getYear() == null || req.getMonth() == null) {
                        logger.error("Year or Month is null | req: {}", req);
                        return Uni.createFrom().item(new ApiResponse<>("error", "Year and Month must not be null",
                                        Collections.emptyList()));
                }

                if (req.getMonth() < 1 || req.getMonth() > 12) {
                        return Uni.createFrom().item(new ApiResponse<>("error", "Month must be between 1 and 12",
                                        Collections.emptyList()));
                }

                logger.info("Fetching monthly FAILED transaction by method | year={}, month={}", req.getYear(),
                                req.getMonth());
                String cacheKey = String.format("transactions:stats:method:monthly:failed:y%d:m%d", req.getYear(),
                                req.getMonth());
                Attributes attrs = Attributes.builder()
                                .put("year", req.getYear())
                                .put("month", req.getMonth())
                                .build();

                LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                LocalDate prev = current.minusMonths(1);

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionMonthlyMethodResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        return runTraced("findMonthlyMethodFailed", "find_monthly_method_failed", attrs,
                                                        () -> {
                                                                FindTransactionMonthRange rangeReq = new FindTransactionMonthRange();
                                                                rangeReq.setStartYear(req.getYear());
                                                                rangeReq.setStartMonth(req.getMonth());
                                                                rangeReq.setEndYear(prev.getYear());
                                                                rangeReq.setEndMonth(prev.getMonthValue());
                                                                return transactionMethodRepository.findMonthlyMethodsFailed(rangeReq);
                                                        })
                                                                        .chain(rawData -> {
                                                                                List<TransactionMonthlyMethodResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionMonthlyMethodResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Monthly failed transaction by method retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} monthly FAILED transaction records by method",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch monthly FAILED transaction by method: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve monthly failed transaction data by method: "
                                                                                                                + e.getMessage(),
                                                                                                Collections.emptyList());
                                                                        });
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodFailed(Integer year) {
                if (year == null) {
                        logger.error("Year is null");
                        return Uni.createFrom().item(
                                        new ApiResponse<>("error", "Year must not be null", Collections.emptyList()));
                }

                logger.info("Fetching yearly FAILED transaction by method | year={}", year);
                String cacheKey = String.format("transactions:stats:method:yearly:failed:y%d", year);
                Attributes attrs = Attributes.builder()
                                .put("year", year)
                                .build();

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponse<List<TransactionYearlyMethodResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionYearlyMethodResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        return runTraced("findYearlyMethodFailed", "find_yearly_method_failed", attrs,
                                                        () -> transactionMethodRepository.findYearlyMethodsFailed(year)
                                                                        .chain(rawData -> {
                                                                                List<TransactionYearlyMethodResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionYearlyMethodResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionYearlyMethodResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Yearly failed transaction by method retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} yearly FAILED transaction records by method",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch yearly FAILED transaction by method: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve yearly failed transaction data by method: "
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
