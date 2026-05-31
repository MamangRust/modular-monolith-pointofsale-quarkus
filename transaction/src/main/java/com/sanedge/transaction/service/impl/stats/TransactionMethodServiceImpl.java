package com.sanedge.transaction.service.impl.stats;

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
import com.sanedge.transaction.domain.requests.MonthMethodTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionMonthlyMethodResponse;
import com.sanedge.transaction.domain.response.TransactionYearlyMethodResponse;
import com.sanedge.transaction.repository.stats.TransactionMethodRepository;
import com.sanedge.transaction.service.stats.TransactionMethodService;

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
public class TransactionMethodServiceImpl implements TransactionMethodService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionMethodServiceImpl.class);

    TransactionMethodRepository transactionMethodRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long STATS_CACHE_TTL_SECONDS = 3600; // 1 hour

    @Inject
    public TransactionMethodServiceImpl(TransactionMethodRepository transactionMethodRepository,
                                        OpenTelemetry openTelemetry,
                                        RedisService redisService,
                                        ObjectMapper objectMapper) {
        this.transactionMethodRepository = transactionMethodRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("transaction-method-stats-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("transaction-method-stats-service");

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
    public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodSuccess(MonthMethodTransactionRequest req) {
        logger.info("📊 Fetching monthly SUCCESS transaction by method | year={}, month={}", req.getYear(), req.getMonth());

        if (req.getYear() == null || req.getMonth() == null) {
            logger.error("❌ Year or Month is null | req: {}", req);
            return Uni.createFrom().item(new ApiResponse<>("error", "Year and Month must not be null", Collections.emptyList()));
        }

        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(new ApiResponse<>("error", "Month must be between 1 and 12", Collections.emptyList()));
        }

        String cacheKey = String.format("transactions:stats:method:monthly:success:y%d:m%d", req.getYear(), req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<TransactionMonthlyMethodResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionMonthlyMethodResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findMonthlyMethodSuccess")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-stats-service")
                            .setAttribute("operation", "find_monthly_method_success")
                            .setAttribute("year", req.getYear())
                            .setAttribute("month", req.getMonth())
                            .startSpan();

                    LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                    LocalDate prev = current.minusMonths(1);

                    return transactionMethodRepository.findMonthlyMethodsSuccess(
                            req.getYear(), req.getMonth(), prev.getYear(), prev.getMonthValue())
                            .chain(rawData -> {
                                List<TransactionMonthlyMethodResponse> responseList = rawData.stream()
                                        .map(TransactionMonthlyMethodResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = ApiResponse.success(
                                        "Monthly success transaction by method retrieved successfully", responseList);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_monthly_method_success",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} monthly SUCCESS transaction records by method", responseList.size());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch monthly SUCCESS transaction by method: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_method_success",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to retrieve monthly success transaction data by method: " + e.getMessage(),
                                        Collections.emptyList());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_method_success"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodSuccess(Integer year) {
        logger.info("📈 Fetching yearly SUCCESS transaction by method | year={}", year);

        if (year == null) {
            logger.error("❌ Year is null");
            return Uni.createFrom().item(new ApiResponse<>("error", "Year must not be null", Collections.emptyList()));
        }

        String cacheKey = String.format("transactions:stats:method:yearly:success:y%d", year);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<TransactionYearlyMethodResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionYearlyMethodResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findYearlyMethodSuccess")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-stats-service")
                            .setAttribute("operation", "find_yearly_method_success")
                            .setAttribute("year", year)
                            .startSpan();

                    return transactionMethodRepository.findYearlyMethodsSuccess(year)
                            .chain(rawData -> {
                                List<TransactionYearlyMethodResponse> responseList = rawData.stream()
                                        .map(TransactionYearlyMethodResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<TransactionYearlyMethodResponse>> response = ApiResponse.success(
                                        "Yearly success transaction by method retrieved successfully", responseList);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_yearly_method_success",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} yearly SUCCESS transaction records by method", responseList.size());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch yearly SUCCESS transaction by method: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_method_success",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to retrieve yearly success transaction data by method: " + e.getMessage(),
                                        Collections.emptyList());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_method_success"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionMonthlyMethodResponse>>> findMonthlyMethodFailed(MonthMethodTransactionRequest req) {
        logger.info("📊 Fetching monthly FAILED transaction by method | year={}, month={}", req.getYear(), req.getMonth());

        if (req.getYear() == null || req.getMonth() == null) {
            logger.error("❌ Year or Month is null | req: {}", req);
            return Uni.createFrom().item(new ApiResponse<>("error", "Year and Month must not be null", Collections.emptyList()));
        }

        if (req.getMonth() < 1 || req.getMonth() > 12) {
            return Uni.createFrom().item(new ApiResponse<>("error", "Month must be between 1 and 12", Collections.emptyList()));
        }

        String cacheKey = String.format("transactions:stats:method:monthly:failed:y%d:m%d", req.getYear(), req.getMonth());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<TransactionMonthlyMethodResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionMonthlyMethodResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findMonthlyMethodFailed")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-stats-service")
                            .setAttribute("operation", "find_monthly_method_failed")
                            .setAttribute("year", req.getYear())
                            .setAttribute("month", req.getMonth())
                            .startSpan();

                    LocalDate current = LocalDate.of(req.getYear(), req.getMonth(), 1);
                    LocalDate prev = current.minusMonths(1);

                    return transactionMethodRepository.findMonthlyMethodsFailed(
                            req.getYear(), req.getMonth(), prev.getYear(), prev.getMonthValue())
                            .chain(rawData -> {
                                List<TransactionMonthlyMethodResponse> responseList = rawData.stream()
                                        .map(TransactionMonthlyMethodResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<TransactionMonthlyMethodResponse>> response = ApiResponse.success(
                                        "Monthly failed transaction by method retrieved successfully", responseList);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_monthly_method_failed",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} monthly FAILED transaction records by method", responseList.size());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch monthly FAILED transaction by method: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_method_failed",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to retrieve monthly failed transaction data by method: " + e.getMessage(),
                                        Collections.emptyList());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_monthly_method_failed"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<List<TransactionYearlyMethodResponse>>> findYearlyMethodFailed(Integer year) {
        logger.info("📈 Fetching yearly FAILED transaction by method | year={}", year);

        if (year == null) {
            logger.error("❌ Year is null");
            return Uni.createFrom().item(new ApiResponse<>("error", "Year must not be null", Collections.emptyList()));
        }

        String cacheKey = String.format("transactions:stats:method:yearly:failed:y%d", year);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponse<List<TransactionYearlyMethodResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponse<List<TransactionYearlyMethodResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findYearlyMethodFailed")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-stats-service")
                            .setAttribute("operation", "find_yearly_method_failed")
                            .setAttribute("year", year)
                            .startSpan();

                    return transactionMethodRepository.findYearlyMethodsFailed(year)
                            .chain(rawData -> {
                                List<TransactionYearlyMethodResponse> responseList = rawData.stream()
                                        .map(TransactionYearlyMethodResponse::from)
                                        .collect(Collectors.toList());

                                ApiResponse<List<TransactionYearlyMethodResponse>> response = ApiResponse.success(
                                        "Yearly failed transaction by method retrieved successfully", responseList);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), STATS_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_yearly_method_failed",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} yearly FAILED transaction records by method", responseList.size());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch yearly FAILED transaction by method: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_method_failed",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to retrieve yearly failed transaction data by method: " + e.getMessage(),
                                        Collections.emptyList());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_yearly_method_failed"));
                            });
                });
    }
}
