package com.sanedge.merchant.service.impl;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.merchant.domain.requests.FindAllMerchantDocuments;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.domain.response.PaginationMeta;
import com.sanedge.merchant.domain.response.MerchantDocumentResponse;
import com.sanedge.merchant.domain.response.MerchantDocumentResponseDeleteAt;
import com.sanedge.merchant.repository.MerchantDocumentQueryRepository;
import com.sanedge.merchant.service.MerchantDocumentQueryService;

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
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class MerchantDocumentQueryServiceImpl implements MerchantDocumentQueryService {
    private static final Logger logger = LoggerFactory.getLogger(MerchantDocumentQueryServiceImpl.class);

    private final MerchantDocumentQueryRepository merchantDocumentQueryRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long LIST_CACHE_TTL_SECONDS = 300;

    @Inject
    public MerchantDocumentQueryServiceImpl(MerchantDocumentQueryRepository merchantDocumentQueryRepository,
                    OpenTelemetry openTelemetry,
                    RedisService redisService,
                    ObjectMapper objectMapper) {
        this.merchantDocumentQueryRepository = merchantDocumentQueryRepository;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("merchant-document-query-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("merchant-document-query-service");

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

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON to object", e);
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON to object with TypeReference", e);
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    @Override
    public Uni<ApiResponsePagination<List<MerchantDocumentResponse>>> findAll(FindAllMerchantDocuments req) {
        String cacheKey = String.format("merchant_docs:all:%d:%d:%s", req.getPage(), req.getPageSize(),
                        req.getSearch());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<MerchantDocumentResponse>> response = fromJson(cachedJson,
                                        new TypeReference<ApiResponsePagination<List<MerchantDocumentResponse>>>() {
                                        });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findAllMerchantDocuments")
                                    .setSpanKind(SpanKind.SERVER)
                                    .setAttribute("service.name", "merchant-document-query-service")
                                    .setAttribute("operation", "find_all")
                                    .startSpan();

                    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                    int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                    String search = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : null;

                    return merchantDocumentQueryRepository.findDocuments(search, page, size)
                            .chain(pagedResult -> {
                                span.setAttribute("doc.count", pagedResult.getTotalRecords());
                                span.setAttribute("doc.page", req.getPage());
                                span.setAttribute("doc.size", req.getPageSize());

                                ApiResponsePagination<List<MerchantDocumentResponse>> response = buildPaginatedResponse(
                                                pagedResult, req, "Merchant documents retrieved successfully",
                                                MerchantDocumentResponse::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            logger.info("Cached response for key: {}", cacheKey);
                                            span.setStatus(StatusCode.OK);

                                            requestsTotal.add(1, Attributes.of(
                                                            AttributeKey.stringKey("operation"), "find_all",
                                                            AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error finding all merchant documents", e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                                AttributeKey.stringKey("operation"), "find_all",
                                                AttributeKey.stringKey("status"), "failed",
                                                AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                                AttributeKey.stringKey("operation"), "find_all"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<MerchantDocumentResponseDeleteAt>>> findAllActive(FindAllMerchantDocuments req) {
        String cacheKey = String.format("merchant_docs:active:%d:%d:%s", req.getPage(), req.getPageSize(),
                        req.getSearch());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<MerchantDocumentResponseDeleteAt>> response = fromJson(cachedJson,
                                        new TypeReference<ApiResponsePagination<List<MerchantDocumentResponseDeleteAt>>>() {
                                        });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findActiveMerchantDocuments")
                                    .setSpanKind(SpanKind.SERVER)
                                    .setAttribute("service.name", "merchant-document-query-service")
                                    .setAttribute("operation", "find_active")
                                    .startSpan();

                    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                    int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                    String search = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : null;

                    return merchantDocumentQueryRepository.findActiveDocuments(search, page, size)
                            .chain(pagedResult -> {
                                span.setAttribute("doc.count", pagedResult.getTotalRecords());
                                ApiResponsePagination<List<MerchantDocumentResponseDeleteAt>> response = buildPaginatedResponse(
                                                pagedResult, req, "Active merchant documents retrieved successfully",
                                                MerchantDocumentResponseDeleteAt::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                            AttributeKey.stringKey("operation"), "find_active",
                                                            AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error finding active merchant documents", e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                                AttributeKey.stringKey("operation"), "find_active"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<MerchantDocumentResponseDeleteAt>>> findAllTrashed(FindAllMerchantDocuments req) {
        String cacheKey = String.format("merchant_docs:trashed:%d:%d:%s", req.getPage(), req.getPageSize(),
                        req.getSearch());

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<MerchantDocumentResponseDeleteAt>> response = fromJson(cachedJson,
                                        new TypeReference<ApiResponsePagination<List<MerchantDocumentResponseDeleteAt>>>() {
                                        });
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findTrashedMerchantDocuments")
                                    .setSpanKind(SpanKind.SERVER)
                                    .setAttribute("service.name", "merchant-document-query-service")
                                    .setAttribute("operation", "find_trashed")
                                    .startSpan();

                    int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
                    int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
                    String search = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : null;

                    return merchantDocumentQueryRepository.findTrashedDocuments(search, page, size)
                            .chain(pagedResult -> {
                                span.setAttribute("doc.count", pagedResult.getTotalRecords());
                                ApiResponsePagination<List<MerchantDocumentResponseDeleteAt>> response = buildPaginatedResponse(
                                                pagedResult, req, "Trashed merchant documents retrieved successfully",
                                                MerchantDocumentResponseDeleteAt::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                            AttributeKey.stringKey("operation"), "find_trashed",
                                                            AttributeKey.stringKey("status"), "success"));
                                            return response;
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error finding trashed merchant documents", e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                                AttributeKey.stringKey("operation"), "find_trashed"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<MerchantDocumentResponse>> findById(Long id) {
        String cacheKey = "merchant_doc:id:" + id;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        MerchantDocumentResponse cachedDoc = fromJson(cachedJson, MerchantDocumentResponse.class);
                        return Uni.createFrom().item(ApiResponse.success("Merchant document retrieved successfully", cachedDoc));
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findMerchantDocumentById")
                                    .setSpanKind(SpanKind.SERVER)
                                    .setAttribute("service.name", "merchant-document-query-service")
                                    .setAttribute("operation", "find_by_id")
                                    .setAttribute("doc.id", id.toString())
                                    .startSpan();

                    return merchantDocumentQueryRepository.findDocumentById(id)
                            .chain(doc -> {
                                if (doc == null) {
                                    span.setStatus(StatusCode.ERROR, "Merchant document not found");
                                    throw new NotFoundException("Merchant document not found with id: " + id);
                                }

                                MerchantDocumentResponse response = MerchantDocumentResponse.from(doc);

                                return redisService.setReactive(cacheKey, toJson(response))
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                            AttributeKey.stringKey("operation"), "find_by_id",
                                                            AttributeKey.stringKey("status"), "success"));
                                            return ApiResponse.success("Merchant document retrieved successfully", response);
                                        });
                            })
                            .onFailure().invoke(e -> {
                                logger.error("Error finding merchant document by id: {}", id, e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                                AttributeKey.stringKey("operation"), "find_by_id"));
                            });
                });
    }

    private <T, R> ApiResponsePagination<List<R>> buildPaginatedResponse(
                    PagedResult<T> pagedResult,
                    FindAllMerchantDocuments request,
                    String successMessage,
                    Function<T, R> mapper) {

        List<R> data = pagedResult.getData().stream()
                        .map(mapper)
                        .collect(Collectors.toList());

        int totalRecords = pagedResult.getTotalRecords();
        int size = request.getPageSize() > 0 ? request.getPageSize() : 1;
        int totalPages = (int) Math.ceil((double) totalRecords / size);

        PaginationMeta pagination = new PaginationMeta(request.getPage(), size, totalPages, totalRecords);

        return new ApiResponsePagination<>("success", successMessage, data, pagination);
    }
}
