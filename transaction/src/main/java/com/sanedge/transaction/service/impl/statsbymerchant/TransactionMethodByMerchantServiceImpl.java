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
import com.sanedge.transaction.domain.requests.MonthMethodTransactionMerchantRequest;
import com.sanedge.transaction.domain.requests.YearMethodTransactionMerchantRequest;
import com.sanedge.transaction.domain.requests.FindTransactionMonthMerchantRange;
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;
import com.sanedge.transaction.repository.statsbymerchant.TransactionMethodByMerchantRepository;
import com.sanedge.transaction.service.statsbymerchant.TransactionMethodByMerchantService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionMethodByMerchantServiceImpl implements TransactionMethodByMerchantService {
        private static final Logger logger = LoggerFactory.getLogger(TransactionMethodByMerchantServiceImpl.class);

        private final TransactionMethodByMerchantRepository transactionMethodByMerchantRepository;
        private final RedisService redisService;
        private final ObjectMapper objectMapper;
        private final TracingMetrics tracingMetrics;

        private static final long STATS_CACHE_TTL_SECONDS = 3600; // 1 hour

        @Inject
        public TransactionMethodByMerchantServiceImpl(
                        TransactionMethodByMerchantRepository transactionMethodByMerchantRepository,
                        RedisService redisService,
                        ObjectMapper objectMapper,
                        TracingMetrics tracingMetrics) {
                this.transactionMethodByMerchantRepository = transactionMethodByMerchantRepository;
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
        public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodByMerchantSuccess(
                        MonthMethodTransactionMerchantRequest req) {
                if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
                        logger.error("Missing required fields | req: {}", req);
                        return Uni.createFrom().item(new ApiResponse<>("error",
                                        "Merchant ID, Year, and Month must not be null", Collections.emptyList()));
                }

                if (req.getMonth() < 1 || req.getMonth() > 12) {
                        return Uni.createFrom().item(new ApiResponse<>("error", "Month must be between 1 and 12",
                                        Collections.emptyList()));
                }

                logger.info("Fetching monthly SUCCESS transaction by method & merchant | merchantId={}, year={}, month={}",
                                req.getMerchantId(), req.getYear(), req.getMonth());
                String cacheKey = String.format("transactions:merchant:stats:method:monthly:success:m%d:y%d:m%d",
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
                                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionMonthlyMethodResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        return runTraced("findMonthlyMethodByMerchantSuccess",
                                                         "find_monthly_method_by_merchant_success", attrs,
                                                         () -> {
                                                                 FindTransactionMonthMerchantRange rangeReq = new FindTransactionMonthMerchantRange();
                                                                 rangeReq.setMerchantId(req.getMerchantId().longValue());
                                                                 rangeReq.setStartYear(req.getYear());
                                                                 rangeReq.setStartMonth(req.getMonth());
                                                                 rangeReq.setEndYear(prev.getYear());
                                                                 rangeReq.setEndMonth(prev.getMonthValue());
                                                                 return transactionMethodByMerchantRepository.findMonthlyTransactionMethodsSuccessByMerchant(rangeReq);
                                                         })
                                                                        .chain(rawData -> {
                                                                                List<TransactionMonthlyMethodResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionMonthlyMethodResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Monthly success transaction by method & merchant retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} monthly SUCCESS transaction records by method & merchant",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch monthly SUCCESS transaction by method & merchant: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve monthly success transaction data by method & merchant: "
                                                                                                                + e.getMessage(),
                                                                                                Collections.emptyList());
                                                                        });
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodByMerchantFailed(
                        MonthMethodTransactionMerchantRequest req) {
                if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
                        logger.error("Missing required fields | req: {}", req);
                        return Uni.createFrom().item(new ApiResponse<>("error",
                                        "Merchant ID, Year, and Month must not be null", Collections.emptyList()));
                }

                if (req.getMonth() < 1 || req.getMonth() > 12) {
                        return Uni.createFrom().item(new ApiResponse<>("error", "Month must be between 1 and 12",
                                        Collections.emptyList()));
                }

                logger.info("Fetching monthly FAILED transaction by method & merchant | merchantId={}, year={}, month={}",
                                req.getMerchantId(), req.getYear(), req.getMonth());
                String cacheKey = String.format("transactions:merchant:stats:method:monthly:failed:m%d:y%d:m%d",
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
                                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = fromJson(
                                                                cachedJson,
                                                                new TypeReference<ApiResponse<List<TransactionMonthlyMethodResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        return runTraced("findMonthlyMethodByMerchantFailed",
                                                         "find_monthly_method_by_merchant_failed", attrs,
                                                         () -> {
                                                                 FindTransactionMonthMerchantRange rangeReq = new FindTransactionMonthMerchantRange();
                                                                 rangeReq.setMerchantId(req.getMerchantId().longValue());
                                                                 rangeReq.setStartYear(req.getYear());
                                                                 rangeReq.setStartMonth(req.getMonth());
                                                                 rangeReq.setEndYear(prev.getYear());
                                                                 rangeReq.setEndMonth(prev.getMonthValue());
                                                                 return transactionMethodByMerchantRepository.findMonthlyTransactionMethodsFailedByMerchant(rangeReq);
                                                         })
                                                                        .chain(rawData -> {
                                                                                List<TransactionMonthlyMethodResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionMonthlyMethodResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Monthly failed transaction by method & merchant retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} monthly FAILED transaction records by method & merchant",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch monthly FAILED transaction by method & merchant: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve monthly failed transaction data by method & merchant: "
                                                                                                                + e.getMessage(),
                                                                                                Collections.emptyList());
                                                                        });
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodByMerchantSuccess(
                        YearMethodTransactionMerchantRequest req) {
                if (req.getMerchantId() == null || req.getYear() == null) {
                        logger.error("Missing required fields | req: {}", req);
                        return Uni.createFrom().item(new ApiResponse<>("error", "Merchant ID and Year must not be null",
                                        Collections.emptyList()));
                }

                logger.info("Fetching yearly SUCCESS transaction by method & merchant | merchantId={}, year={}",
                                req.getMerchantId(), req.getYear());
                String cacheKey = String.format("transactions:merchant:stats:method:yearly:success:m%d:y%d",
                                req.getMerchantId(), req.getYear());
                Attributes attrs = Attributes.builder()
                                .put("merchantId", req.getMerchantId())
                                .put("year", req.getYear())
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
                                        return runTraced("findYearlyMethodByMerchantSuccess",
                                                        "find_yearly_method_by_merchant_success", attrs,
                                                        () -> transactionMethodByMerchantRepository
                                                                        .findYearlyTransactionMethodsSuccessByMerchant(
                                                                                        req.getMerchantId().longValue(),
                                                                                        req.getYear())
                                                                        .chain(rawData -> {
                                                                                List<TransactionYearlyMethodResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionYearlyMethodResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionYearlyMethodResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Yearly success transaction by method & merchant retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} yearly SUCCESS transaction records by method & merchant",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch yearly SUCCESS transaction by method & merchant: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve yearly success transaction data by method & merchant: "
                                                                                                                + e.getMessage(),
                                                                                                Collections.emptyList());
                                                                        }));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodByMerchantFailed(
                        YearMethodTransactionMerchantRequest req) {
                if (req.getMerchantId() == null || req.getYear() == null) {
                        logger.error("Missing required fields | req: {}", req);
                        return Uni.createFrom().item(new ApiResponse<>("error", "Merchant ID and Year must not be null",
                                        Collections.emptyList()));
                }

                logger.info("Fetching yearly FAILED transaction by method & merchant | merchantId={}, year={}",
                                req.getMerchantId(), req.getYear());
                String cacheKey = String.format("transactions:merchant:stats:method:yearly:failed:m%d:y%d",
                                req.getMerchantId(), req.getYear());
                Attributes attrs = Attributes.builder()
                                .put("merchantId", req.getMerchantId())
                                .put("year", req.getYear())
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
                                        return runTraced("findYearlyMethodByMerchantFailed",
                                                        "find_yearly_method_by_merchant_failed", attrs,
                                                        () -> transactionMethodByMerchantRepository
                                                                        .findYearlyTransactionMethodsFailedByMerchant(
                                                                                        req.getMerchantId().longValue(),
                                                                                        req.getYear())
                                                                        .chain(rawData -> {
                                                                                List<TransactionYearlyMethodResponse> responseList = rawData
                                                                                                .stream()
                                                                                                .map(TransactionYearlyMethodResponse::from)
                                                                                                .collect(Collectors
                                                                                                                .toList());

                                                                                ApiResponse<List<TransactionYearlyMethodResponse>> response = ApiResponse
                                                                                                .success(
                                                                                                                "Yearly failed transaction by method & merchant retrieved successfully",
                                                                                                                responseList);

                                                                                return redisService
                                                                                                .setWithExpirationReactive(
                                                                                                                cacheKey,
                                                                                                                toJson(response),
                                                                                                                STATS_CACHE_TTL_SECONDS)
                                                                                                .map(v -> {
                                                                                                        logger.info("Found {} yearly FAILED transaction records by method & merchant",
                                                                                                                        responseList.size());
                                                                                                        return response;
                                                                                                });
                                                                        })
                                                                        .onFailure().recoverWithItem(e -> {
                                                                                logger.error("Failed to fetch yearly FAILED transaction by method & merchant: {}",
                                                                                                e.getMessage(), e);
                                                                                return new ApiResponse<>("error",
                                                                                                "Failed to retrieve yearly failed transaction data by method & merchant: "
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
