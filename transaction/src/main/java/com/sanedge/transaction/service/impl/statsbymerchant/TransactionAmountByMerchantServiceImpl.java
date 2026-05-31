package com.sanedge.transaction.service.impl.statsbymerchant;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.transaction.domain.requests.MonthAmountTransactionMerchant;
import com.sanedge.transaction.domain.requests.YearAmountTransactionMerchant;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionMonthlyAmountSuccessResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountFailedResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyAmountSuccessResponse;
import com.sanedge.transaction.repository.statsbymerchant.TransactionAmountByMerchantRepository;
import com.sanedge.transaction.service.statsbymerchant.TransactionAmountByMerchantService;

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
public class TransactionAmountByMerchantServiceImpl implements TransactionAmountByMerchantService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionAmountByMerchantServiceImpl.class);

    TransactionAmountByMerchantRepository transactionAmountByMerchantRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long STATS_CACHE_TTL_SECONDS = 3600; // 1 hour

    @Inject
    public TransactionAmountByMerchantServiceImpl(TransactionAmountByMerchantRepository transactionAmountByMerchantRepository,
                                                 OpenTelemetry openTelemetry,
                                                 RedisService redisService,
                                                 ObjectMapper objectMapper) {
        this.transactionAmountByMerchantRepository = transactionAmountByMerchantRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("transaction-amount-by-merchant-stats-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("transaction-amount-by-merchant-stats-service");

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

    private <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON with TypeReference", e);
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    @Override
    public Uni<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>> findMonthlyAmountSuccessByMerchant(MonthAmountTransactionMerchant req) {
        logger.info("📊 Fetching monthly SUCCESS transaction amount by merchant | merchantId={}, year={}, month={}",
                req.getMerchantId(), req.getYear(), req.getMonth());

        if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
            logger.error("❌ Missing required fields | req: {}", req);
            return Uni.createFrom().item(new ApiResponse<>("error", "Merchant ID, Year, and Month must not be null", Collections.emptyList()));
        }

        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(new ApiResponse<>("error", "Month must be between 1 and 12", Collections.emptyList()));
        }

        String cacheKey = String.format("transactions:merchant:stats:amount:monthly:success:m%d:y%d:m%d",
                req.getMerchantId(), req.getYear(), req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionMonthlyAmountSuccessResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findMonthlyAmountSuccessByMerchant")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-stats-service")
                            .setAttribute("operation", "find_monthly_amount_success_by_merchant")
                            .setAttribute("merchantId", req.getMerchantId())
                            .setAttribute("year", req.getYear())
                            .setAttribute("month", req.getMonth())
                            .startSpan();

                    LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                    LocalDate prev = current.minusMonths(1);

                    return transactionAmountByMerchantRepository.findMonthlySuccessByMerchant(
                            req.getMerchantId().longValue(), req.getYear(), req.getMonth(), prev.getYear(), prev.getMonthValue())
                            .chain(rawData -> {
                                List<TransactionMonthlyAmountSuccessResponse> responseList = rawData.stream()
                                        .map(TransactionMonthlyAmountSuccessResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<TransactionMonthlyAmountSuccessResponse>> response = ApiResponse.success(
                                        "Monthly success transaction amount by merchant retrieved successfully", responseList);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_monthly_amount_success_by_merchant",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} monthly SUCCESS transaction records for merchant", responseList.size());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch monthly SUCCESS transaction amount by merchant: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_amount_success_by_merchant",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to retrieve monthly success transaction data by merchant: " + e.getMessage(),
                                        Collections.emptyList());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_amount_success_by_merchant"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>> findYearlyAmountSuccessByMerchant(YearAmountTransactionMerchant req) {
        logger.info("📈 Fetching yearly SUCCESS transaction amount by merchant | merchantId={}, year={}",
                req.getMerchantId(), req.getYear());

        if (req.getMerchantId() == null || req.getYear() == null) {
            logger.error("❌ Missing required fields | req: {}", req);
            return Uni.createFrom().item(new ApiResponse<>("error", "Merchant ID and Year must not be null", Collections.emptyList()));
        }

        String cacheKey = String.format("transactions:merchant:stats:amount:yearly:success:m%d:y%d",
                req.getMerchantId(), req.getYear());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionYearlyAmountSuccessResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findYearlyAmountSuccessByMerchant")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-stats-service")
                            .setAttribute("operation", "find_yearly_amount_success_by_merchant")
                            .setAttribute("merchantId", req.getMerchantId())
                            .setAttribute("year", req.getYear())
                            .startSpan();

                    return transactionAmountByMerchantRepository.findYearlySuccessByMerchant(
                            req.getMerchantId().longValue(), req.getYear())
                            .chain(rawData -> {
                                List<TransactionYearlyAmountSuccessResponse> responseList = rawData.stream()
                                        .map(TransactionYearlyAmountSuccessResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<TransactionYearlyAmountSuccessResponse>> response = ApiResponse.success(
                                        "Yearly success transaction amount by merchant retrieved successfully", responseList);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_yearly_amount_success_by_merchant",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} yearly SUCCESS transaction records for merchant", responseList.size());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch yearly SUCCESS transaction amount by merchant: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_amount_success_by_merchant",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to retrieve yearly success transaction data by merchant: " + e.getMessage(),
                                        Collections.emptyList());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_amount_success_by_merchant"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>> findMonthlyAmountFailedByMerchant(MonthAmountTransactionMerchant req) {
        logger.info("📊 Fetching monthly FAILED transaction amount by merchant | merchantId={}, year={}, month={}",
                req.getMerchantId(), req.getYear(), req.getMonth());

        if (req.getMerchantId() == null || req.getYear() == null || req.getMonth() == null) {
            logger.error("❌ Missing required fields | req: {}", req);
            return Uni.createFrom().item(new ApiResponse<>("error", "Merchant ID, Year, and Month must not be null", Collections.emptyList()));
        }

        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(new ApiResponse<>("error", "Month must be between 1 and 12", Collections.emptyList()));
        }

        String cacheKey = String.format("transactions:merchant:stats:amount:monthly:failed:m%d:y%d:m%d",
                req.getMerchantId(), req.getYear(), req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<TransactionMonthlyAmountFailedResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionMonthlyAmountFailedResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findMonthlyAmountFailedByMerchant")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-stats-service")
                            .setAttribute("operation", "find_monthly_amount_failed_by_merchant")
                            .setAttribute("merchantId", req.getMerchantId())
                            .setAttribute("year", req.getYear())
                            .setAttribute("month", req.getMonth())
                            .startSpan();

                    LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                    LocalDate prev = current.minusMonths(1);

                    return transactionAmountByMerchantRepository.findMonthlyFailedByMerchant(
                            req.getMerchantId().longValue(), req.getYear(), req.getMonth(), prev.getYear(), prev.getMonthValue())
                            .chain(rawData -> {
                                List<TransactionMonthlyAmountFailedResponse> responseList = rawData.stream()
                                        .map(TransactionMonthlyAmountFailedResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<TransactionMonthlyAmountFailedResponse>> response = ApiResponse.success(
                                        "Monthly failed transaction amount by merchant retrieved successfully", responseList);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_monthly_amount_failed_by_merchant",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} monthly FAILED transaction records for merchant", responseList.size());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch monthly FAILED transaction amount by merchant: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_amount_failed_by_merchant",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to retrieve monthly failed transaction data by merchant: " + e.getMessage(),
                                        Collections.emptyList());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_amount_failed_by_merchant"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionYearlyAmountFailedResponse>>> findYearlyAmountFailedByMerchant(YearAmountTransactionMerchant req) {
        logger.info("📈 Fetching yearly FAILED transaction amount by merchant | merchantId={}, year={}",
                req.getMerchantId(), req.getYear());

        if (req.getMerchantId() == null || req.getYear() == null) {
            logger.error("❌ Missing required fields | req: {}", req);
            return Uni.createFrom().item(new ApiResponse<>("error", "Merchant ID and Year must not be null", Collections.emptyList()));
        }

        String cacheKey = String.format("transactions:merchant:stats:amount:yearly:failed:m%d:y%d",
                req.getMerchantId(), req.getYear());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<TransactionYearlyAmountFailedResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionYearlyAmountFailedResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findYearlyAmountFailedByMerchant")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-stats-service")
                            .setAttribute("operation", "find_yearly_amount_failed_by_merchant")
                            .setAttribute("merchantId", req.getMerchantId())
                            .setAttribute("year", req.getYear())
                            .startSpan();

                    return transactionAmountByMerchantRepository.findYearlyFailedByMerchant(
                            req.getMerchantId().longValue(), req.getYear())
                            .chain(rawData -> {
                                List<TransactionYearlyAmountFailedResponse> responseList = rawData.stream()
                                        .map(TransactionYearlyAmountFailedResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<TransactionYearlyAmountFailedResponse>> response = ApiResponse.success(
                                        "Yearly failed transaction amount by merchant retrieved successfully", responseList);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_yearly_amount_failed_by_merchant",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} yearly FAILED transaction records for merchant", responseList.size());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch yearly FAILED transaction amount by merchant: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_amount_failed_by_merchant",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to retrieve yearly failed transaction data by merchant: " + e.getMessage(),
                                        Collections.emptyList());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_amount_failed_by_merchant"));
                            });
                });
    }
}
