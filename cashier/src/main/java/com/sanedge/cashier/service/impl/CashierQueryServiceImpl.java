package com.sanedge.cashier.service.impl;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.cashier.domain.requests.FindAllCashierMerchant;
import com.sanedge.cashier.domain.requests.FindAllCashiers;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.domain.response.PaginationMeta;
import com.sanedge.cashier.domain.response.CashierResponse;
import com.sanedge.cashier.domain.response.CashierResponseDeleteAt;
import com.sanedge.cashier.entity.Cashier;
import com.sanedge.cashier.repository.CashierQueryRepository;
import com.sanedge.cashier.service.CashierQueryService;

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
public class CashierQueryServiceImpl implements CashierQueryService {
        private static final Logger logger = LoggerFactory.getLogger(CashierQueryServiceImpl.class);

        CashierQueryRepository cashierQueryRepository;
        OpenTelemetry openTelemetry;
        RedisService redisService;
        ObjectMapper objectMapper;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        private static final long LIST_CACHE_TTL_SECONDS = 300;

        @Inject
        public CashierQueryServiceImpl(CashierQueryRepository cashierQueryRepository, OpenTelemetry openTelemetry,
                        RedisService redisService, ObjectMapper objectMapper) {
                this.cashierQueryRepository = cashierQueryRepository;
                this.openTelemetry = openTelemetry;
                this.redisService = redisService;
                this.objectMapper = objectMapper;
                this.tracer = openTelemetry.getTracer("cashier-query-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("cashier-query-service");

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
        public Uni<ApiResponsePagination<List<CashierResponse>>> findAll(FindAllCashiers req) {
                String cacheKey = String.format("cashiers:all:%d:%d:%s", req.getPage(), req.getPageSize(),
                                req.getSearch());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                 logger.info("Cache HIT for key: {}", cacheKey);
                                                 ApiResponsePagination<List<CashierResponse>> response = fromJson(cachedJson,
                                                                 new TypeReference<ApiResponsePagination<List<CashierResponse>>>() {
                                                                 });
                                                 return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findAllCashiers")
                                                         .setSpanKind(SpanKind.SERVER)
                                                         .setAttribute("service.name", "cashier-service")
                                                         .setAttribute("operation", "find_all_cashiers")
                                                         .startSpan();

                                         return cashierQueryRepository.findAllCashiers(req)
                                                         .chain(pagedResult -> {
                                                                 span.setAttribute("cashier.count", pagedResult.getTotalRecords());
                                                                 span.setAttribute("cashier.page", req.getPage());
                                                                 span.setAttribute("cashier.size", req.getPageSize());

                                                                 ApiResponsePagination<List<CashierResponse>> response = buildPaginatedResponse(
                                                                                 pagedResult, req.getPage(), req.getPageSize(),
                                                                                 "Cashiers retrieved successfully",
                                                                                 CashierResponse::from);

                                                                 return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                                                                 .map(v -> {
                                                                                         logger.info("Cached response for key: {}", cacheKey);
                                                                                         logger.info("Successfully retrieved {} cashiers", pagedResult.getTotalRecords());
                                                                                         span.setStatus(StatusCode.OK);

                                                                                         requestsTotal.add(1, Attributes.of(
                                                                                                         AttributeKey.stringKey("operation"), "find_all_cashiers",
                                                                                                         AttributeKey.stringKey("status"), "success"));
                                                                                         return response;
                                                                                 });
                                                         })
                                                         .onFailure().invoke(e -> {
                                                                 logger.error("Error finding all cashiers", e);
                                                                 span.recordException(e);
                                                                 span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                 requestsTotal.add(1, Attributes.of(
                                                                                 AttributeKey.stringKey("operation"), "find_all_cashiers",
                                                                                 AttributeKey.stringKey("status"), "failed",
                                                                                 AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                                                         })
                                                         .eventually(() -> {
                                                                 span.end();
                                                                 double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                                                 requestDurationSeconds.record(duration, Attributes.of(
                                                                                 AttributeKey.stringKey("operation"), "find_all_cashiers"));
                                                                 logger.debug("Find all cashiers operation completed in {} seconds", duration);
                                                         });
                                 });
        }

        @Override
        public Uni<ApiResponse<CashierResponse>> findById(Long cashierId) {
                String cacheKey = "cashier:" + cashierId;

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                CashierResponse cachedCashier = fromJson(cachedJson, CashierResponse.class);
                                                return Uni.createFrom().item(ApiResponse.success("Cashier retrieved successfully", cachedCashier));
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findCashierById")
                                                         .setSpanKind(SpanKind.SERVER)
                                                         .setAttribute("service.name", "cashier-service")
                                                         .setAttribute("operation", "find_cashier_by_id")
                                                         .setAttribute("cashier.id", cashierId.toString())
                                                         .startSpan();

                                         return cashierQueryRepository.findByCashierId(cashierId)
                                                         .chain(cashier -> {
                                                                 if (cashier == null) {
                                                                         logger.warn("Cashier not found with id: {}", cashierId);
                                                                         span.setStatus(StatusCode.ERROR, "Cashier not found");
                                                                         span.setAttribute("cashier.found", false);

                                                                         requestsTotal.add(1, Attributes.of(
                                                                                         AttributeKey.stringKey("operation"), "find_cashier_by_id",
                                                                                         AttributeKey.stringKey("status"), "failed",
                                                                                         AttributeKey.stringKey("error_type"), "not_found"));

                                                                         throw new NotFoundException("Cashier not found with id: " + cashierId);
                                                                 }

                                                                 span.setAttribute("cashier.found", true);
                                                                 span.setAttribute("cashier.name", cashier.getName());

                                                                 CashierResponse cashierResponse = CashierResponse.from(cashier);

                                                                 return redisService.setReactive(cacheKey, toJson(cashierResponse))
                                                                                 .map(v -> {
                                                                                         logger.info("Cached cashier for key: {}", cacheKey);
                                                                                         logger.info("Successfully found cashier with id: {} and name: {}", cashierId, cashier.getName());
                                                                                         span.setStatus(StatusCode.OK);

                                                                                         requestsTotal.add(1, Attributes.of(
                                                                                                         AttributeKey.stringKey("operation"), "find_cashier_by_id",
                                                                                                         AttributeKey.stringKey("status"), "success"));

                                                                                         return ApiResponse.success("Cashier retrieved successfully", cashierResponse);
                                                                                 });
                                                         })
                                                         .onFailure().invoke(e -> {
                                                                 logger.error("Error finding cashier by id: {}", cashierId, e);
                                                                 span.recordException(e);
                                                                 span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                 requestsTotal.add(1, Attributes.of(
                                                                                 AttributeKey.stringKey("operation"), "find_cashier_by_id",
                                                                                 AttributeKey.stringKey("status"), "failed",
                                                                                 AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                                                         })
                                                         .eventually(() -> {
                                                                 span.end();
                                                                 double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                                                 requestDurationSeconds.record(duration, Attributes.of(
                                                                                 AttributeKey.stringKey("operation"), "find_cashier_by_id"));
                                                                 logger.debug("Find cashier by id operation completed in {} seconds", duration);
                                                         });
                                 });
        }

        @Override
        public Uni<ApiResponsePagination<List<CashierResponseDeleteAt>>> findByActive(FindAllCashiers req) {
                String cacheKey = String.format("cashiers:active:%d:%d:%s", req.getPage(), req.getPageSize(),
                                req.getSearch());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<CashierResponseDeleteAt>> response = fromJson(cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<CashierResponseDeleteAt>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findActiveCashiers")
                                                         .setSpanKind(SpanKind.SERVER)
                                                         .setAttribute("service.name", "cashier-service")
                                                         .setAttribute("operation", "find_active_cashiers")
                                                         .startSpan();

                                         return cashierQueryRepository.findActiveCashiers(req)
                                                         .chain(pagedResult -> {
                                                                 span.setAttribute("cashier.count", pagedResult.getTotalRecords());
                                                                 span.setAttribute("cashier.page", req.getPage());
                                                                 span.setAttribute("cashier.size", req.getPageSize());

                                                                 ApiResponsePagination<List<CashierResponseDeleteAt>> response = buildPaginatedResponse(
                                                                                 pagedResult, req.getPage(), req.getPageSize(),
                                                                                 "Active cashiers retrieved successfully",
                                                                                 CashierResponseDeleteAt::from);

                                                                 return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                                                                 .map(v -> {
                                                                                         logger.info("Cached response for key: {}", cacheKey);
                                                                                         logger.info("Successfully retrieved {} active cashiers", pagedResult.getTotalRecords());
                                                                                         span.setStatus(StatusCode.OK);

                                                                                         requestsTotal.add(1, Attributes.of(
                                                                                                         AttributeKey.stringKey("operation"), "find_active_cashiers",
                                                                                                         AttributeKey.stringKey("status"), "success"));
                                                                                         return response;
                                                                                 });
                                                         })
                                                         .onFailure().invoke(e -> {
                                                                 logger.error("Error finding active cashiers", e);
                                                                 span.recordException(e);
                                                                 span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                 requestsTotal.add(1, Attributes.of(
                                                                                 AttributeKey.stringKey("operation"), "find_active_cashiers",
                                                                                 AttributeKey.stringKey("status"), "failed",
                                                                                 AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                                                         })
                                                         .eventually(() -> {
                                                                 span.end();
                                                                 double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                                                 requestDurationSeconds.record(duration, Attributes.of(
                                                                                 AttributeKey.stringKey("operation"), "find_active_cashiers"));
                                                                 logger.debug("Find active cashiers operation completed in {} seconds", duration);
                                                         });
                                 });
        }

        @Override
        public Uni<ApiResponsePagination<List<CashierResponseDeleteAt>>> findByTrashed(FindAllCashiers req) {
                String cacheKey = String.format("cashiers:trashed:%d:%d:%s", req.getPage(), req.getPageSize(),
                                req.getSearch());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<CashierResponseDeleteAt>> response = fromJson(cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<CashierResponseDeleteAt>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findTrashedCashiers")
                                                         .setSpanKind(SpanKind.SERVER)
                                                         .setAttribute("service.name", "cashier-service")
                                                         .setAttribute("operation", "find_trashed_cashiers")
                                                         .startSpan();

                                         return cashierQueryRepository.findTrashedCashiers(req)
                                                         .chain(pagedResult -> {
                                                                 span.setAttribute("cashier.count", pagedResult.getTotalRecords());
                                                                 span.setAttribute("cashier.page", req.getPage());
                                                                 span.setAttribute("cashier.size", req.getPageSize());

                                                                 ApiResponsePagination<List<CashierResponseDeleteAt>> response = buildPaginatedResponse(
                                                                                 pagedResult, req.getPage(), req.getPageSize(),
                                                                                 "Trashed cashiers retrieved successfully",
                                                                                 CashierResponseDeleteAt::from);

                                                                 return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                                                                 .map(v -> {
                                                                                         logger.info("Cached response for key: {}", cacheKey);
                                                                                         logger.info("Successfully retrieved {} trashed cashiers", pagedResult.getTotalRecords());
                                                                                         span.setStatus(StatusCode.OK);

                                                                                         requestsTotal.add(1, Attributes.of(
                                                                                                         AttributeKey.stringKey("operation"), "find_trashed_cashiers",
                                                                                                         AttributeKey.stringKey("status"), "success"));
                                                                                         return response;
                                                                                 });
                                                         })
                                                         .onFailure().invoke(e -> {
                                                                 logger.error("Error finding trashed cashiers", e);
                                                                 span.recordException(e);
                                                                 span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                 requestsTotal.add(1, Attributes.of(
                                                                                 AttributeKey.stringKey("operation"), "find_trashed_cashiers",
                                                                                 AttributeKey.stringKey("status"), "failed",
                                                                                 AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                                                         })
                                                         .eventually(() -> {
                                                                 span.end();
                                                                 double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                                                 requestDurationSeconds.record(duration, Attributes.of(
                                                                                 AttributeKey.stringKey("operation"), "find_trashed_cashiers"));
                                                                 logger.debug("Find trashed cashiers operation completed in {} seconds", duration);
                                                         });
                                 });
        }

        @Override
        public Uni<ApiResponsePagination<List<CashierResponse>>> findByMerchant(FindAllCashierMerchant req) {
                String cacheKey = String.format("cashiers:merchant:%d:%d:%d:%s", req.getMerchantId(), req.getPage(), req.getPageSize(),
                                req.getSearch());

                return redisService.getReactive(cacheKey)
                                .chain(cachedJson -> {
                                        if (cachedJson != null) {
                                                logger.info("Cache HIT for key: {}", cacheKey);
                                                ApiResponsePagination<List<CashierResponse>> response = fromJson(cachedJson,
                                                                new TypeReference<ApiResponsePagination<List<CashierResponse>>>() {
                                                                });
                                                return Uni.createFrom().item(response);
                                        }

                                        logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                                        long startTime = System.currentTimeMillis();
                                        Span span = tracer.spanBuilder("findByMerchantCashiers")
                                                         .setSpanKind(SpanKind.SERVER)
                                                         .setAttribute("service.name", "cashier-service")
                                                         .setAttribute("operation", "find_by_merchant_cashiers")
                                                         .startSpan();

                                         return cashierQueryRepository.findByMerchants(req)
                                                         .chain(pagedResult -> {
                                                                 span.setAttribute("cashier.count", pagedResult.getTotalRecords());
                                                                 span.setAttribute("cashier.page", req.getPage());
                                                                 span.setAttribute("cashier.size", req.getPageSize());

                                                                 ApiResponsePagination<List<CashierResponse>> response = buildPaginatedResponse(
                                                                                 pagedResult, req.getPage(), req.getPageSize(),
                                                                                 "Cashiers retrieved successfully by merchant",
                                                                                 CashierResponse::from);

                                                                 return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                                                                 .map(v -> {
                                                                                         logger.info("Cached response for key: {}", cacheKey);
                                                                                         logger.info("Successfully retrieved {} merchant cashiers", pagedResult.getTotalRecords());
                                                                                         span.setStatus(StatusCode.OK);

                                                                                         requestsTotal.add(1, Attributes.of(
                                                                                                         AttributeKey.stringKey("operation"), "find_by_merchant_cashiers",
                                                                                                         AttributeKey.stringKey("status"), "success"));
                                                                                         return response;
                                                                                 });
                                                         })
                                                         .onFailure().invoke(e -> {
                                                                 logger.error("Error finding merchant cashiers", e);
                                                                 span.recordException(e);
                                                                 span.setStatus(StatusCode.ERROR, e.getMessage());

                                                                 requestsTotal.add(1, Attributes.of(
                                                                                 AttributeKey.stringKey("operation"), "find_by_merchant_cashiers",
                                                                                 AttributeKey.stringKey("status"), "failed",
                                                                                 AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                                                         })
                                                         .eventually(() -> {
                                                                 span.end();
                                                                 double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                                                 requestDurationSeconds.record(duration, Attributes.of(
                                                                                 AttributeKey.stringKey("operation"), "find_by_merchant_cashiers"));
                                                                 logger.debug("Find merchant cashiers operation completed in {} seconds", duration);
                                                         });
                                 });
        }

        private <T, R> ApiResponsePagination<List<R>> buildPaginatedResponse(
                        PagedResult<T> pagedResult,
                        Integer page,
                        Integer pageSize,
                        String successMessage,
                        Function<T, R> mapper) {

                List<R> data = pagedResult.getData().stream()
                                .map(mapper)
                                .collect(Collectors.toList());

                int totalRecords = pagedResult.getTotalRecords();
                int size = pageSize != null && pageSize > 0 ? pageSize : 1;
                int totalPages = (int) Math.ceil((double) totalRecords / size);

                PaginationMeta pagination = new PaginationMeta(page != null ? page : 1, size, totalPages, totalRecords);

                return new ApiResponsePagination<>("success", successMessage, data, pagination);
        }
}
