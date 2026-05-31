package com.sanedge.product.service.impl;

import java.io.File;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.config.RedisService;
import com.sanedge.product.domain.requests.CreateProductRequest;
import com.sanedge.product.domain.requests.UpdateProductRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.product.domain.response.ProductResponse;
import com.sanedge.product.domain.response.ProductResponseDeleteAt;
import com.sanedge.product.entity.Product;
import com.sanedge.common.exception.ResourceNotFoundException;
import io.quarkus.grpc.GrpcClient;
import com.sanedge.product.repository.ProductCommandRepository;
import com.sanedge.product.repository.ProductQueryRepository;
import com.sanedge.product.service.ProductCommandService;

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
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ApplicationScoped
public class ProductCommandServiceImpl implements ProductCommandService {
    private static final Logger logger = LoggerFactory.getLogger(ProductCommandServiceImpl.class);

    ProductCommandRepository productCommandRepository;
    ProductQueryRepository productQueryRepository;

    @Inject
    @GrpcClient("merchant")
    pb.merchant.MutinyMerchantQueryServiceGrpc.MutinyMerchantQueryServiceStub merchantQueryService;

    OpenTelemetry openTelemetry;
    Validator validator;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    private static final String PRODUCT_BASE_PATH = "static/product";

    @Inject
    public ProductCommandServiceImpl(ProductCommandRepository productCommandRepository,
            ProductQueryRepository productQueryRepository,
            Validator validator,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.productCommandRepository = productCommandRepository;
        this.productQueryRepository = productQueryRepository;
        this.validator = validator;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("product-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("product-command-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponse>> createProduct(CreateProductRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createProduct")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "create_product")
                .setAttribute("product.name", req.getName())
                .startSpan();

        logger.info("🆕 Creating product: {}", req.getName());

        try {
            validateRequest(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", e.getMessage(), (ProductResponse) null));
        }

        Product product = new Product();
        product.setMerchantId(req.getMerchantId().longValue());
        product.setCategoryId(req.getCategoryId().longValue());
        product.setName(req.getName());
        product.setDescription(req.getDescription());
        product.setPrice(req.getPrice());
        product.setCountInStock(req.getCountInStock());
        product.setBrand(req.getBrand());
        product.setWeight(req.getWeight());
        product.setSlugProduct(req.getSlugProduct());
        product.setImageProduct(req.getImageProduct());
        product.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        product.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

        return productCommandRepository.persist(product)
                .map(saved -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_product",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Product created successfully with name={}", saved.getName());
                    return ApiResponse.success("Product created successfully", ProductResponse.from(saved));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to create product: {}", req.getName(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_product",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to create product: " + e.getMessage(),
                            (ProductResponse) null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_product"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponse>> updateProduct(UpdateProductRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateProduct")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "update_product")
                .setAttribute("productId", req.getProductId())
                .startSpan();

        logger.info("✏️ Updating product ID: {}", req.getProductId());

        try {
            validateRequest(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", e.getMessage(), (ProductResponse) null));
        }

        return merchantQueryService.findByIdMerchant(pb.merchant.Merchant.FindByIdMerchantRequest.newBuilder()
                        .setMerchantId(req.getMerchantId())
                        .build())
                .onItem().transformToUni(apiResp -> {
                    if (apiResp == null || !apiResp.hasData() || !"success".equalsIgnoreCase(apiResp.getStatus())) {
                        return Uni.createFrom().failure(new ResourceNotFoundException("Merchant not found with id " + req.getMerchantId()));
                    }
                    return productQueryRepository.findProductById(req.getProductId().longValue());
                })
                .onItem().ifNull()
                .failWith(() -> new ResourceNotFoundException("Product not found with id " + req.getProductId()))
                .chain(product -> {
                    if (req.getImageProduct() != null) {
                        product.setImageProduct(req.getImageProduct());
                    }

                    product.setMerchantId(req.getMerchantId().longValue());
                    product.setCategoryId(req.getCategoryId().longValue());

                    product.setName(req.getName());
                    product.setDescription(req.getDescription());
                    product.setPrice(req.getPrice());
                    product.setCountInStock(req.getCountInStock());
                    product.setBrand(req.getBrand());
                    product.setWeight(req.getWeight());
                    product.setSlugProduct(req.getSlugProduct());
                    product.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                    return productCommandRepository.persist(product);
                })
                .chain(updated -> {
                    String cacheKey = "products:id:" + req.getProductId();
                    return redisService.deleteReactive(cacheKey)
                            .replaceWith(updated);
                })
                .map(updated -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_product",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Product updated successfully for ID={}", updated.getProductId());
                    return ApiResponse.success("Product updated successfully", ProductResponse.from(updated));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to update product ID: {}", req.getProductId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_product",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    String errorMsg = e instanceof ResourceNotFoundException ? "Resource not found: " + e.getMessage()
                            : "Failed to update product";
                    return new ApiResponse<>("error", errorMsg, (ProductResponse) null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_product"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponseDeleteAt>> trashedProduct(Integer productId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashedProduct")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "trashed_product")
                .setAttribute("productId", productId)
                .startSpan();

        logger.info("🗑️ Trashing product ID: {}", productId);

        if (productId == null) {
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "Product ID must not be null", (ProductResponseDeleteAt) null));
        }

        return productCommandRepository.trashed(productId.longValue())
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException("Product not found"))
                .chain(trashed -> {
                    String cacheKey = "products:id:" + productId;
                    return redisService.deleteReactive(cacheKey)
                            .replaceWith(trashed);
                })
                .map(trashed -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trashed_product",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Product soft deleted successfully for ID={}", trashed.getProductId());
                    return ApiResponse.success("Product trashed successfully", ProductResponseDeleteAt.from(trashed));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to trash product ID: {}", productId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trashed_product",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to trash product", (ProductResponseDeleteAt) null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trashed_product"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<ProductResponseDeleteAt>> restoreProduct(Integer productId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreProduct")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "restore_product")
                .setAttribute("productId", productId)
                .startSpan();

        logger.info("♻️ Restoring product ID: {}", productId);

        if (productId == null) {
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "Product ID must not be null", (ProductResponseDeleteAt) null));
        }

        return productCommandRepository.restore(productId.longValue())
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException("Product not found or not deleted"))
                .chain(restored -> {
                    String cacheKey = "products:id:" + productId;
                    return redisService.deleteReactive(cacheKey)
                            .replaceWith(restored);
                })
                .map(restored -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_product",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Product restored successfully for ID={}", restored.getProductId());
                    return ApiResponse.success("Product restored successfully", ProductResponseDeleteAt.from(restored));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to restore product ID: {}", productId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_product",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to restore product", (ProductResponseDeleteAt) null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_product"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteProductPermanent(Integer productId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteProductPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "delete_product_permanent")
                .setAttribute("productId", productId)
                .startSpan();

        logger.warn("❌ Permanently deleting product ID: {}", productId);

        if (productId == null) {
            return Uni.createFrom().item(new ApiResponse<>("error", "Product ID must not be null", false));
        }

        return productCommandRepository.findById(productId.longValue())
                .chain(deleted -> {
                    if (Boolean.TRUE.equals(deleted)) {
                        String cacheKey = "products:id:" + productId;
                        return redisService.deleteReactive(cacheKey)
                                .replaceWith(true);
                    }
                    return Uni.createFrom().item(false);
                })
                .map(deleted -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_product_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Product permanently deleted for ID={}: {}", productId, deleted);
                    return ApiResponse.success("Product permanently deleted", deleted);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to permanently delete product ID: {}", productId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_product_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to permanently delete product", false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_product_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAllProducts() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllProducts")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "restore_all_products")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed products");

        return productCommandRepository.restoreAllDeleted()
                .map(restored -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_products",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ All trashed products restored: {}", restored);
                    return ApiResponse.success("All products restored successfully", restored);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to restore all products", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_products",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to restore all products", false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_products"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAllProductsPermanent() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllProductsPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "product-service")
                .setAttribute("operation", "delete_all_products_permanent")
                .startSpan();

        logger.warn("💣 Permanently deleting ALL trashed products");

        return productCommandRepository.deleteAllDeleted()
                .map(deleted -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_products_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ All trashed products permanently deleted: {}", deleted);
                    return ApiResponse.success("All products permanently deleted", deleted);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to delete all products", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_products_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to delete all products", false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_products_permanent"));
                });
    }

    private <T> void validateRequest(T req) {
        Set<ConstraintViolation<T>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ConstraintViolation<T> violation : violations) {
                sb.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("; ");
            }
            logger.error("Validation failed: {}", sb);
            throw new ConstraintViolationException("Validation failed: " + sb, violations);
        }
    }
}
