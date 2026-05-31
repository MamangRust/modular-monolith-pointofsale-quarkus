package com.sanedge.transaction.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.domain.response.PaginationMeta;
import com.sanedge.transaction.domain.requests.FindAllTransactionByMerchantRequest;
import com.sanedge.transaction.domain.requests.FindAllTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionResponse;
import com.sanedge.transaction.domain.response.TransactionResponseDeleteAt;
import com.sanedge.transaction.entity.Transaction;
import com.sanedge.transaction.repository.TransactionQueryRepository;
import com.sanedge.transaction.service.TransactionQueryService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionQueryServiceImpl implements TransactionQueryService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionQueryServiceImpl.class);

    TransactionQueryRepository transactionQueryRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long LIST_CACHE_TTL_SECONDS = 300;

    @Inject
    public TransactionQueryServiceImpl(TransactionQueryRepository transactionQueryRepository,
                                       OpenTelemetry openTelemetry,
                                       RedisService redisService,
                                       ObjectMapper objectMapper) {
        this.transactionQueryRepository = transactionQueryRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("transaction-query-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("transaction-query-service");

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
    public Uni<ApiResponsePagination<List<TransactionResponse>>> findAllTransactions(FindAllTransactionRequest req) {
        int page = req.getPage() > 0 ? req.getPage() : 1;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = req.getSearch() != null ? req.getSearch() : "";

        String cacheKey = String.format("transactions:all:page:%d:size:%d:search:%s", page, pageSize, keyword);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<TransactionResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponsePagination<List<TransactionResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findAllTransactions")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-service")
                            .setAttribute("operation", "find_all_transactions")
                            .setAttribute("page", page)
                            .setAttribute("pageSize", pageSize)
                            .setAttribute("keyword", keyword)
                            .startSpan();

                    return transactionQueryRepository.findTransactions(keyword, page, pageSize)
                            .chain(pagedResult -> {
                                span.setAttribute("transaction.count", pagedResult.getTotalRecords());

                                ApiResponsePagination<List<TransactionResponse>> response = buildPaginatedResponse(
                                        pagedResult, page, pageSize, "Transactions retrieved successfully", TransactionResponse::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_all_transactions",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} transactions", pagedResult.getTotalRecords());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch transactions: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_all_transactions",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to fetch transactions: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_all_transactions"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<TransactionResponseDeleteAt>>> findByActive(FindAllTransactionRequest req) {
        int page = req.getPage() > 0 ? req.getPage() : 1;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = req.getSearch() != null ? req.getSearch() : "";

        String cacheKey = String.format("transactions:active:page:%d:size:%d:search:%s", page, pageSize, keyword);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<TransactionResponseDeleteAt>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponsePagination<List<TransactionResponseDeleteAt>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findActiveTransactions")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-service")
                            .setAttribute("operation", "find_active_transactions")
                            .setAttribute("page", page)
                            .setAttribute("pageSize", pageSize)
                            .setAttribute("keyword", keyword)
                            .startSpan();

                    return transactionQueryRepository.findActiveTransactions(keyword, page, pageSize)
                            .chain(pagedResult -> {
                                span.setAttribute("transaction.count", pagedResult.getTotalRecords());

                                ApiResponsePagination<List<TransactionResponseDeleteAt>> response = buildPaginatedResponse(
                                        pagedResult, page, pageSize, "Active transactions retrieved successfully", TransactionResponseDeleteAt::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_active_transactions",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} active transactions", pagedResult.getTotalRecords());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch active transactions: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_active_transactions",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to fetch active transactions: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_active_transactions"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<TransactionResponseDeleteAt>>> findByTrashed(FindAllTransactionRequest req) {
        int page = req.getPage() > 0 ? req.getPage() : 1;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = req.getSearch() != null ? req.getSearch() : "";

        String cacheKey = String.format("transactions:trashed:page:%d:size:%d:search:%s", page, pageSize, keyword);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<TransactionResponseDeleteAt>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponsePagination<List<TransactionResponseDeleteAt>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findTrashedTransactions")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-service")
                            .setAttribute("operation", "find_trashed_transactions")
                            .setAttribute("page", page)
                            .setAttribute("pageSize", pageSize)
                            .setAttribute("keyword", keyword)
                            .startSpan();

                    return transactionQueryRepository.findTrashedTransactions(keyword, page, pageSize)
                            .chain(pagedResult -> {
                                span.setAttribute("transaction.count", pagedResult.getTotalRecords());

                                ApiResponsePagination<List<TransactionResponseDeleteAt>> response = buildPaginatedResponse(
                                        pagedResult, page, pageSize, "Trashed transactions retrieved successfully", TransactionResponseDeleteAt::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_trashed_transactions",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} trashed transactions", pagedResult.getTotalRecords());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch trashed transactions: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_trashed_transactions",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to fetch trashed transactions: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_trashed_transactions"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<TransactionResponse>>> findByMerchant(FindAllTransactionByMerchantRequest req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() : 1;
        int pageSize = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = req.getSearch() != null ? req.getSearch() : "";
        Long merchantId = req.getMerchantId() != null ? req.getMerchantId().longValue() : 0L;

        String cacheKey = String.format("transactions:merchant:%d:page:%d:size:%d:search:%s", merchantId, page, pageSize, keyword);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<TransactionResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponsePagination<List<TransactionResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findTransactionsByMerchant")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-service")
                            .setAttribute("operation", "find_transactions_by_merchant")
                            .setAttribute("merchantId", merchantId)
                            .setAttribute("page", page)
                            .setAttribute("pageSize", pageSize)
                            .setAttribute("keyword", keyword)
                            .startSpan();

                    return transactionQueryRepository.findTransactionsByMerchant(req)
                            .chain(pagedResult -> {
                                span.setAttribute("transaction.count", pagedResult.getTotalRecords());

                                ApiResponsePagination<List<TransactionResponse>> response = buildPaginatedResponse(
                                        pagedResult, page, pageSize, "Transactions by merchant retrieved successfully", TransactionResponse::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_transactions_by_merchant",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} transactions for merchant_id={}", pagedResult.getTotalRecords(), merchantId);
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch transactions by merchant_id={}: {}", merchantId, e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_transactions_by_merchant",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to fetch transactions by merchant: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_transactions_by_merchant"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<TransactionResponse>> findById(Integer id) {
        String cacheKey = "transaction:id:" + id;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        TransactionResponse cached = fromJson(cachedJson, new TypeReference<TransactionResponse>() {});
                        return Uni.createFrom().item(ApiResponse.success("Transaction retrieved successfully", cached));
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findTransactionById")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-service")
                            .setAttribute("operation", "find_transaction_by_id")
                            .setAttribute("transactionId", id)
                            .startSpan();

                    return transactionQueryRepository.findByTransactionId(id.longValue())
                            .chain(transaction -> {
                                if (transaction == null) {
                                    logger.warn("❌ Transaction not found with id={}", id);
                                    span.recordException(new IllegalArgumentException("Transaction not found"));
                                    span.setStatus(StatusCode.ERROR, "Transaction not found");

                                    requestsTotal.add(1, Attributes.of(
                                            AttributeKey.stringKey("operation"), "find_transaction_by_id",
                                            AttributeKey.stringKey("status"), "failed",
                                            AttributeKey.stringKey("error_type"), "not_found"));

                                    return Uni.createFrom().item(new ApiResponse<>("error", "Transaction not found", (TransactionResponse) null));
                                }

                                TransactionResponse transactionResponse = TransactionResponse.from(transaction);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(transactionResponse), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_transaction_by_id",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Successfully found transaction with id: {}", id);
                                            return ApiResponse.success("Transaction retrieved successfully", transactionResponse);
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch transaction by id={}: {}", id, e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_transaction_by_id",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to fetch transaction: " + e.getMessage(), (TransactionResponse) null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_transaction_by_id"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<TransactionResponse>> findByOrderId(Integer id) {
        String cacheKey = "transaction:order:" + id;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        TransactionResponse cached = fromJson(cachedJson, new TypeReference<TransactionResponse>() {});
                        return Uni.createFrom().item(ApiResponse.success("Transaction retrieved successfully", cached));
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findTransactionByOrderId")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "transaction-service")
                            .setAttribute("operation", "find_transaction_by_order")
                            .setAttribute("orderId", id)
                            .startSpan();

                    return transactionQueryRepository.findByOrderId(id.longValue())
                            .chain(transaction -> {
                                if (transaction == null) {
                                    logger.warn("❌ Transaction not found with order_id={}", id);
                                    span.recordException(new IllegalArgumentException("Transaction not found"));
                                    span.setStatus(StatusCode.ERROR, "Transaction not found");

                                    requestsTotal.add(1, Attributes.of(
                                            AttributeKey.stringKey("operation"), "find_transaction_by_order",
                                            AttributeKey.stringKey("status"), "failed",
                                            AttributeKey.stringKey("error_type"), "not_found"));

                                    return Uni.createFrom().item(new ApiResponse<>("error", "Transaction not found for order", (TransactionResponse) null));
                                }

                                TransactionResponse transactionResponse = TransactionResponse.from(transaction);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(transactionResponse), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_transaction_by_order",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Successfully found transaction with order_id: {}", id);
                                            return ApiResponse.success("Transaction retrieved successfully", transactionResponse);
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch transaction by order_id={}: {}", id, e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_transaction_by_order",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to fetch transaction by order: " + e.getMessage(), (TransactionResponse) null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_transaction_by_order"));
                            });
                });
    }

    private <T, R> ApiResponsePagination<List<R>> buildPaginatedResponse(PagedResult<T> pagedResult,
                                                                          int page,
                                                                          int pageSize,
                                                                          String successMessage,
                                                                          Function<T, R> mapper) {
        List<R> data = pagedResult.getData().stream()
                .map(mapper)
                .collect(Collectors.toList());

        int totalRecords = pagedResult.getTotalRecords();
        int size = pageSize > 0 ? pageSize : 1;
        int totalPages = (int) Math.ceil((double) totalRecords / size);

        PaginationMeta pagination = new PaginationMeta(page, size, totalPages, totalRecords);

        return new ApiResponsePagination<>("success", successMessage, data, pagination);
    }
}
