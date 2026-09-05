package com.sanedge.transaction.service.impl.statsbymerchant;

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
import com.sanedge.transaction.domain.requests.MonthAmountTransactionMerchant;
import com.sanedge.transaction.domain.requests.YearAmountTransactionMerchant;
import com.sanedge.transaction.domain.requests.FindTransactionMonthMerchantRange;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountSuccessResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountSuccessResponse;
import com.sanedge.transaction.repository.statsbymerchant.TransactionAmountByMerchantRepository;
import com.sanedge.transaction.service.statsbymerchant.TransactionAmountByMerchantService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionAmountByMerchantServiceImpl implements TransactionAmountByMerchantService {
        private static final Logger logger = LoggerFactory.getLogger(TransactionAmountByMerchantServiceImpl.class);

        private final TransactionAmountByMerchantRepository transactionAmountByMerchantRepository;
        private final RedisService redisService;
        private final ObjectMapper objectMapper;
        private final TracingMetrics tracingMetrics;

        private static final long STATS_CACHE_TTL_SECONDS = 3600; // 1 hour

        @Inject
        public TransactionAmountByMerchantServiceImpl(
                        TransactionAmountByMerchantRepository transactionAmountByMerchantRepository,
                        RedisService redisService,
                        ObjectMapper objectMapper,
                        TracingMetrics tracingMetrics) {
                this.transactionAmountByMerchantRepository = transactionAmountByMerchantRepository;
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
        public Uni<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>> findMonthlyAmountSuccessByMerchant(
                        MonthAmountTransactionMerchant req) {
                if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
                        logger.error("Missing required fields | req: {}", req);
                        return Uni.createFrom().item(new ApiResponse<>("error",
                                        "Merchant ID, Year, and Month must not be null", Collections.emptyList()));
                }

                if (req.getMonth() < 1 || req.getMonth() > 12) {
                        return Uni.createFrom().item(new ApiResponse<>("error", "Month must be between 1 and 12",
                                        Collections.emptyList()));
                }

                logger.info("Fetching monthly SUCCESS transaction amount by merchant | merchantId={}, year={}, month={}",
                                req.getMerchantId(), req.getYear(), req.getMonth());
                String cacheKey = String.format("transactions:merchant:stats:amount:monthly:success:m%d:y%d:m%d",
                                req.getMerchantId(), req.getYear(), req.getMonth());
                Attributes attrs = Attributes.builder()
                                .put("merchantId", req.getMerchantId())
                                .put("year", req.getYear())
                                .put("month", req.getMonth())
                                .build();

                LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                LocalDate prev = current.minusMonths(1);

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
                                        return runTraced("findMonthlyAmountSuccessByMerchant",
                                                        "find_monthly_amount_success_by_merchant", attrs,
                                                        () -> {
                                                                FindTransactionMonthMerchantRange rangeReq = new FindTransactionMonthMerchantRange();
                                                                rangeReq.setMerchantId(req.getMerchantId().longValue());
                                                                rangeReq.setStartYear(req.getYear());
                                                                rangeReq.setStartMonth(req.getMonth());
                                                                rangeReq.setEndYear(prev.getYear());
                                                                rangeReq.setEndMonth(prev.getMonthValue());
                                                                return transactionAmountByMerchantRepository.findMonthlySuccessByMerchant(rangeReq);
                                                        })
                                                        .chain(rawData -> {
                                                                List<TransactionMonthlyAmountSuccessResponse> responseList = rawData
                                                                                .stream()
                                                                                .map(TransactionMonthlyAmountSuccessResponse::from)
                                                                                .collect(Collectors
                                                                                                .toList());

                                                                ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Monthly success transaction amount by merchant retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(
                                                                                                cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Found {} monthly SUCCESS transaction records for merchant",
                                                                                                        responseList.size());
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().recoverWithItem(e -> {
                                                                logger.error("Failed to fetch monthly SUCCESS transaction amount by merchant: {}",
                                                                                e.getMessage(), e);
                                                                return new ApiResponse<>("error",
                                                                                "Failed to retrieve monthly success transaction data by merchant: "
                                                                                                + e.getMessage(),
                                                                                Collections.emptyList());
                                                        });
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>> findYearlyAmountSuccessByMerchant(
                        YearAmountTransactionMerchant req) {
                if (req.getMerchantId() == null || req.getYear() == null) {
                        logger.error("Missing required fields | req: {}", req);
                        return Uni.createFrom().item(new ApiResponse<>("error", "Merchant ID and Year must not be null",
                                        Collections.emptyList()));
                }

                logger.info("Fetching yearly SUCCESS transaction amount by merchant | merchantId={}, year={}",
                                req.getMerchantId(), req.getYear());
                String cacheKey = String.format("transactions:merchant:stats:amount:yearly:success:m%d:y%d",
                                req.getMerchantId(), req.getYear());
                Attributes attrs = Attributes.builder()
                                .put("merchantId", req.getMerchantId())
                                .put("year", req.getYear())
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
                                        return runTraced("findYearlyAmountSuccessByMerchant",
                                                        "find_yearly_amount_success_by_merchant", attrs,
                                                        () -> transactionAmountByMerchantRepository
                                                                        .findYearlySuccessByMerchant(
                                                                                        req.getMerchantId().longValue(),
                                                                                        req.getYear())
                                                                        .chain(rawData -> {
                                                                                List<TransactionYearlyAmountSuccessResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionYearlyAmountSuccessResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Yearly success transaction amount by merchant retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} yearly SUCCESS transaction records for merchant",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch yearly SUCCESS transaction amount by merchant: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve yearly success transaction data by merchant: "
                                                                                                                + e.getMessage(),
                                                                                                Collections.emptyList());
                                                                        }));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>> findMonthlyAmountFailedByMerchant(
                        MonthAmountTransactionMerchant req) {
                if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
                        logger.error("Missing required fields | req: {}", req);
                        return Uni.createFrom().item(new ApiResponse<>("error",
                                        "Merchant ID, Year, and Month must not be null", Collections.emptyList()));
                }

                if (req.getMonth() < 1 || req.getMonth() > 12) {
                        return Uni.createFrom().item(new ApiResponse<>("error", "Month must be between 1 and 12",
                                        Collections.emptyList()));
                }

                logger.info("Fetching monthly FAILED transaction amount by merchant | merchantId={}, year={}, month={}",
                                req.getMerchantId(), req.getYear(), req.getMonth());
                String cacheKey = String.format("transactions:merchant:stats:amount:monthly:failed:m%d:y%d:m%d",
                                req.getMerchantId(), req.getYear(), req.getMonth());
                Attributes attrs = Attributes.builder()
                                .put("merchantId", req.getMerchantId())
                                .put("year", req.getYear())
                                .put("month", req.getMonth())
                                .build();

                LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                LocalDate prev = current.minusMonths(1);

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
                                        return runTraced("findMonthlyAmountFailedByMerchant",
                                                        "find_monthly_amount_failed_by_merchant", attrs,
                                                        () -> {
                                                                FindTransactionMonthMerchantRange rangeReq = new FindTransactionMonthMerchantRange();
                                                                rangeReq.setMerchantId(req.getMerchantId().longValue());
                                                                rangeReq.setStartYear(req.getYear());
                                                                rangeReq.setStartMonth(req.getMonth());
                                                                rangeReq.setEndYear(prev.getYear());
                                                                rangeReq.setEndMonth(prev.getMonthValue());
                                                                return transactionAmountByMerchantRepository.findMonthlyFailedByMerchant(rangeReq);
                                                        })
                                                        .chain(rawData -> {
                                                                List<TransactionMonthlyAmountFailedResponse> responseList = rawData
                                                                                .stream()
                                                                                .map(TransactionMonthlyAmountFailedResponse::from)
                                                                                .collect(Collectors
                                                                                                .toList());

                                                                ApiResponse<List<TransactionMonthlyAmountFailedResponse>> response = ApiResponse
                                                                                .success(
                                                                                                "Monthly failed transaction amount by merchant retrieved successfully",
                                                                                                responseList);

                                                                return redisService
                                                                                .setWithExpirationReactive(
                                                                                                cacheKey,
                                                                                                toJson(response),
                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                .map(v -> {
                                                                                        logger.info("Found {} monthly FAILED transaction records for merchant",
                                                                                                        responseList.size());
                                                                                        return response;
                                                                                });
                                                        })
                                                        .onFailure().recoverWithItem(e -> {
                                                                logger.error("Failed to fetch monthly FAILED transaction amount by merchant: {}",
                                                                                e.getMessage(), e);
                                                                return new ApiResponse<>("error",
                                                                                "Failed to retrieve monthly failed transaction data by merchant: "
                                                                                                + e.getMessage(),
                                                                                Collections.emptyList());
                                                        });
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionYearlyAmountFailedResponse>>> findYearlyAmountFailedByMerchant(
                        YearAmountTransactionMerchant req) {
                if (req.getMerchantId() == null || req.getYear() == null) {
                        logger.error("Missing required fields | req: {}", req);
                        return Uni.createFrom().item(new ApiResponse<>("error", "Merchant ID and Year must not be null",
                                        Collections.emptyList()));
                }

                logger.info("Fetching yearly FAILED transaction amount by merchant | merchantId={}, year={}",
                                req.getMerchantId(), req.getYear());
                String cacheKey = String.format("transactions:merchant:stats:amount:yearly:failed:m%d:y%d",
                                req.getMerchantId(), req.getYear());
                Attributes attrs = Attributes.builder()
                                .put("merchantId", req.getMerchantId())
                                .put("year", req.getYear())
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
                                        return runTraced("findYearlyAmountFailedByMerchant",
                                                        "find_yearly_amount_failed_by_merchant", attrs,
                                                        () -> transactionAmountByMerchantRepository
                                                                        .findYearlyFailedByMerchant(
                                                                                        req.getMerchantId().longValue(),
                                                                                        req.getYear())
                                                                        .chain(rawData -> {
                                                                                List<TransactionYearlyAmountFailedResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionYearlyAmountFailedResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionYearlyAmountFailedResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Yearly failed transaction amount by merchant retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} yearly FAILED transaction records for merchant",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch yearly FAILED transaction amount by merchant: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve yearly failed transaction data by merchant: "
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