package com.sanedge.product.service.impl;

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
import com.sanedge.product.domain.requests.FindAllProductByCategoryRequest;
import com.sanedge.product.domain.requests.FindAllProductByMerchantRequest;
import com.sanedge.product.domain.requests.FindAllProductRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.domain.response.PaginationMeta;
import com.sanedge.product.domain.response.ProductResponse;
import com.sanedge.product.domain.response.ProductResponseDeleteAt;
import com.sanedge.product.repository.ProductQueryRepository;
import com.sanedge.product.service.ProductQueryService;

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
public class ProductQueryImplService implements ProductQueryService {
    private static final Logger logger = LoggerFactory.getLogger(ProductQueryImplService.class);

    ProductQueryRepository productQueryRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final long LIST_CACHE_TTL_SECONDS = 300; // 5 minutes

    @Inject
    public ProductQueryImplService(ProductQueryRepository productQueryRepository,
                                   OpenTelemetry openTelemetry,
                                   RedisService redisService,
                                   ObjectMapper objectMapper) {
        this.productQueryRepository = productQueryRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("product-query-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("product-query-service");

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
    public Uni<ApiResponsePagination<List<ProductResponse>>> findAll(FindAllProductRequest req) {
        String cacheKey = String.format("products:all:%d:%d:%s", req.getPage(), req.getPageSize(),
                req.getSearch() != null ? req.getSearch() : "");

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<ProductResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponsePagination<List<ProductResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findAllProducts")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "product-service")
                            .setAttribute("operation", "find_all_products")
                            .startSpan();

                    String searchKeyword = req.getSearch() != null ? req.getSearch() : "";
                    int page = req.getPage() > 0 ? req.getPage() : 1;
                    int size = req.getPageSize() > 0 ? req.getPageSize() : 10;

                    return productQueryRepository.findAllProducts(searchKeyword, page, size)
                            .chain(pagedResult -> {
                                ApiResponsePagination<List<ProductResponse>> response = buildPaginatedResponse(
                                        pagedResult, page, size, "Products retrieved successfully", ProductResponse::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_all_products",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} products", pagedResult.getData().size());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch products: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_all_products",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to retrieve products: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_all_products"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<ProductResponseDeleteAt>>> findActiveProducts(FindAllProductRequest req) {
        String cacheKey = String.format("products:active:%d:%d:%s", req.getPage(), req.getPageSize(),
                req.getSearch() != null ? req.getSearch() : "");

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<ProductResponseDeleteAt>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponsePagination<List<ProductResponseDeleteAt>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findActiveProducts")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "product-service")
                            .setAttribute("operation", "find_active_products")
                            .startSpan();

                    String searchKeyword = req.getSearch() != null ? req.getSearch() : "";
                    int page = req.getPage() > 0 ? req.getPage() : 1;
                    int size = req.getPageSize() > 0 ? req.getPageSize() : 10;

                    return productQueryRepository.findActiveProducts(searchKeyword, page, size)
                            .chain(pagedResult -> {
                                ApiResponsePagination<List<ProductResponseDeleteAt>> response = buildPaginatedResponse(
                                        pagedResult, page, size, "Active products retrieved successfully", ProductResponseDeleteAt::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_active_products",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} active products", pagedResult.getData().size());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch active products: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_active_products",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to retrieve active products: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_active_products"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<ProductResponseDeleteAt>>> findTrashedProducts(FindAllProductRequest req) {
        String cacheKey = String.format("products:trashed:%d:%d:%s", req.getPage(), req.getPageSize(),
                req.getSearch() != null ? req.getSearch() : "");

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<ProductResponseDeleteAt>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponsePagination<List<ProductResponseDeleteAt>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findTrashedProducts")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "product-service")
                            .setAttribute("operation", "find_trashed_products")
                            .startSpan();

                    String searchKeyword = req.getSearch() != null ? req.getSearch() : "";
                    int page = req.getPage() > 0 ? req.getPage() : 1;
                    int size = req.getPageSize() > 0 ? req.getPageSize() : 10;

                    return productQueryRepository.findTrashedProducts(searchKeyword, page, size)
                            .chain(pagedResult -> {
                                ApiResponsePagination<List<ProductResponseDeleteAt>> response = buildPaginatedResponse(
                                        pagedResult, page, size, "Trashed products retrieved successfully", ProductResponseDeleteAt::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_trashed_products",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} trashed products", pagedResult.getData().size());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch trashed products: {}", e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_trashed_products",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to retrieve trashed products: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_trashed_products"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<ProductResponse>>> findByMerchant(FindAllProductByMerchantRequest req) {
        String cacheKey = String.format("products:merchant:m%d:c%d:p%d:s%d:q%s:min%d:max%d",
                req.getMerchantId() != null ? req.getMerchantId() : 0,
                req.getCategoryId() != null ? req.getCategoryId() : 0,
                req.getPage(), req.getPageSize(),
                req.getSearch() != null ? req.getSearch() : "",
                req.getMinPrice() != null ? req.getMinPrice() : 0,
                req.getMaxPrice() != null ? req.getMaxPrice() : 0);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<ProductResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponsePagination<List<ProductResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findProductsByMerchant")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "product-service")
                            .setAttribute("operation", "find_products_by_merchant")
                            .setAttribute("merchantId", req.getMerchantId())
                            .startSpan();

                    int page = req.getPage() > 0 ? req.getPage() : 1;
                    int size = req.getPageSize() > 0 ? req.getPageSize() : 10;

                    return productQueryRepository.findProductsByMerchant(req)
                            .chain(pagedResult -> {
                                ApiResponsePagination<List<ProductResponse>> response = buildPaginatedResponse(
                                        pagedResult, page, size, "Products by merchant retrieved successfully", ProductResponse::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_products_by_merchant",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} products for merchant {}", pagedResult.getData().size(), req.getMerchantId());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch products by merchant {}: {}", req.getMerchantId(), e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_products_by_merchant",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to retrieve products by merchant: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_products_by_merchant"));
                            });
                });
    }

    @Override
    public Uni<ApiResponsePagination<List<ProductResponse>>> findByCategoryName(FindAllProductByCategoryRequest req) {
        String cacheKey = String.format("products:category:n%s:p%d:s%d:q%s:min%d:max%d",
                req.getCategoryName() != null ? req.getCategoryName() : "",
                req.getPage(), req.getPageSize(),
                req.getSearch() != null ? req.getSearch() : "",
                req.getMinPrice() != null ? req.getMinPrice() : 0,
                req.getMaxPrice() != null ? req.getMaxPrice() : 0);

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ApiResponsePagination<List<ProductResponse>> response = fromJson(cachedJson,
                                new TypeReference<ApiResponsePagination<List<ProductResponse>>>() {});
                        return Uni.createFrom().item(response);
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findProductsByCategoryName")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "product-service")
                            .setAttribute("operation", "find_products_by_category_name")
                            .setAttribute("categoryName", req.getCategoryName())
                            .startSpan();

                    String searchKeyword = req.getSearch() != null ? req.getSearch() : "";
                    int page = req.getPage() > 0 ? req.getPage() : 1;
                    int size = req.getPageSize() > 0 ? req.getPageSize() : 10;

                    return productQueryRepository.findProductsByCategory(
                            req.getCategoryName(), searchKeyword, req.getMinPrice(), req.getMaxPrice(), page, size)
                            .chain(pagedResult -> {
                                ApiResponsePagination<List<ProductResponse>> response = buildPaginatedResponse(
                                        pagedResult, page, size, "Products by category retrieved successfully", ProductResponse::from);

                                return redisService.setWithExpirationReactive(cacheKey, toJson(response), LIST_CACHE_TTL_SECONDS)
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_products_by_category_name",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found {} products for category {}", pagedResult.getData().size(), req.getCategoryName());
                                            return response;
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch products by category name {}: {}", req.getCategoryName(), e.getMessage(), e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_products_by_category_name",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponsePagination<>("error", "Failed to retrieve products by category: " + e.getMessage(),
                                        Collections.emptyList(), null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_products_by_category_name"));
                            });
                });
    }

    @Override
    public Uni<ApiResponse<ProductResponse>> findById(Long productId) {
        logger.info("🔍 Fetching product by ID: {}", productId);

        if (productId == null) {
            return Uni.createFrom().item(new ApiResponse<>("error", "Product ID must not be null", (ProductResponse) null));
        }

        String cacheKey = "products:id:" + productId;

        return redisService.getReactive(cacheKey)
                .chain(cachedJson -> {
                    if (cachedJson != null) {
                        logger.info("Cache HIT for key: {}", cacheKey);
                        ProductResponse cachedResponse = fromJson(cachedJson, new TypeReference<ProductResponse>() {});
                        return Uni.createFrom().item(ApiResponse.success("Product retrieved successfully", cachedResponse));
                    }

                    logger.info("Cache MISS for key: {}. Fetching from DB.", cacheKey);
                    long startTime = System.currentTimeMillis();
                    Span span = tracer.spanBuilder("findProductById")
                            .setSpanKind(SpanKind.SERVER)
                            .setAttribute("service.name", "product-service")
                            .setAttribute("operation", "find_product_by_id")
                            .setAttribute("productId", productId)
                            .startSpan();

                    return productQueryRepository.findProductById(productId)
                            .chain(product -> {
                                if (product == null) {
                                    logger.warn("❌ Product not found with ID: {}", productId);
                                    span.setStatus(StatusCode.ERROR, "Product not found");
                                    requestsTotal.add(1, Attributes.of(
                                            AttributeKey.stringKey("operation"), "find_product_by_id",
                                            AttributeKey.stringKey("status"), "failed",
                                            AttributeKey.stringKey("error_type"), "not_found"));
                                    return Uni.createFrom().item(new ApiResponse<>("error", "Product not found", (ProductResponse) null));
                                }

                                ProductResponse productResponse = ProductResponse.from(product);

                                return redisService.setReactive(cacheKey, toJson(productResponse))
                                        .map(v -> {
                                            span.setStatus(StatusCode.OK);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "find_product_by_id",
                                                    AttributeKey.stringKey("status"), "success"));
                                            logger.info("✅ Found product with ID: {}", productId);
                                            return ApiResponse.success("Product retrieved successfully", productResponse);
                                        });
                            })
                            .onFailure().recoverWithItem(e -> {
                                logger.error("💥 Failed to fetch product by ID: {}", productId, e);
                                span.recordException(e);
                                span.setStatus(StatusCode.ERROR, e.getMessage());

                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_product_by_id",
                                        AttributeKey.stringKey("status"), "failed",
                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                                return new ApiResponse<>("error", "Failed to retrieve product: " + e.getMessage(), (ProductResponse) null);
                            })
                            .eventually(() -> {
                                span.end();
                                double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                requestDurationSeconds.record(duration, Attributes.of(
                                        AttributeKey.stringKey("operation"), "find_product_by_id"));
                            });
                });
    }

    private <T, R> ApiResponsePagination<List<R>> buildPaginatedResponse(PagedResult<T> pagedResult,
                                                                         int page,
                                                                         int size,
                                                                         String successMessage,
                                                                         Function<T, R> mapper) {
        List<R> data = pagedResult.getData().stream()
                .map(mapper)
                .collect(Collectors.toList());

        int totalRecords = pagedResult.getTotalRecords();
        int totalPages = (int) Math.ceil((double) totalRecords / (size > 0 ? size : 1));

        PaginationMeta pagination = new PaginationMeta(page, size, totalPages, totalRecords);

        return new ApiResponsePagination<>("success", successMessage, data, pagination);
    }
}
