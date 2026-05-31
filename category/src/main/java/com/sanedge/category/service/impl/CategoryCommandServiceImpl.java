package com.sanedge.category.service.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.config.RedisService;
import com.sanedge.category.domain.requests.CreateCategoryRequest;
import com.sanedge.category.domain.requests.UpdateCategoryRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.category.domain.response.CategoryResponse;
import com.sanedge.category.domain.response.CategoryResponseDeleteAt;
import com.sanedge.category.entity.Category;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.category.repository.CategoryCommandRepository;
import com.sanedge.category.repository.CategoryQueryRepository;
import com.sanedge.category.service.CategoryCommandService;

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
public class CategoryCommandServiceImpl implements CategoryCommandService {
    private static final Logger logger = LoggerFactory.getLogger(CategoryCommandServiceImpl.class);

    CategoryQueryRepository categoryQueryRepository;
    CategoryCommandRepository categoryCommandRepository;
    Validator validator;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public CategoryCommandServiceImpl(CategoryQueryRepository categoryQueryRepository,
                                      CategoryCommandRepository categoryCommandRepository,
                                      Validator validator,
                                      OpenTelemetry openTelemetry,
                                      RedisService redisService) {
        this.categoryQueryRepository = categoryQueryRepository;
        this.categoryCommandRepository = categoryCommandRepository;
        this.validator = validator;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("category-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("category-command-service");

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
    public Uni<ApiResponse<CategoryResponse>> createCategory(CreateCategoryRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createCategory")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-service")
                .setAttribute("operation", "create_category")
                .setAttribute("category.name", req.getName())
                .startSpan();

        logger.info("🆕 Creating category name={}", req.getName());

        try {
            validateRequest(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", e.getMessage(), null));
        }

        return categoryQueryRepository.findByName(req.getName())
                .chain(existingCategory -> {
                    if (existingCategory != null) {
                        logger.warn("❌ Category creation failed. Category name '{}' already exists", req.getName());
                        throw new IllegalArgumentException("Category with name '" + req.getName() + "' already exists");
                    }

                    Category category = new Category();
                    category.setName(req.getName());
                    category.setDescription(req.getDescription());
                    category.setSlugCategory(req.getSlugCategory());
                    category.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    category.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                    return categoryCommandRepository.persist(category)
                            .map(savedCategory -> {
                                span.setAttribute("category.id", savedCategory.getCategoryId());
                                span.setStatus(StatusCode.OK);

                                CategoryResponse response = CategoryResponse.from(savedCategory);
                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "create_category",
                                        AttributeKey.stringKey("status"), "success"));

                                logger.info("✅ Category created successfully with id={}", response.getId());
                                return ApiResponse.success("✅ Category created successfully!", response);
                            });
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to create category", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_category",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_category"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<CategoryResponse>> updateCategory(UpdateCategoryRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateCategory")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-service")
                .setAttribute("operation", "update_category")
                .setAttribute("category.id", req.getCategoryId() != null ? req.getCategoryId().toString() : "null")
                .startSpan();

        if (req.getCategoryId() == null) {
            span.setStatus(StatusCode.ERROR, "category_id is required");
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", "category_id is required", null));
        }

        logger.info("🔄 Updating category id={}", req.getCategoryId());

        try {
            validateRequest(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", e.getMessage(), null));
        }

        return categoryCommandRepository.findById(req.getCategoryId().longValue())
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException("Category not found"))
                .chain(category -> {
                    category.setName(req.getName());
                    category.setDescription(req.getDescription());
                    category.setSlugCategory(req.getSlugCategory());
                    category.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                    return categoryCommandRepository.persist(category)
                            .chain(savedCategory -> {
                                String cacheKey = "category:" + req.getCategoryId();
                                return redisService.deleteReactive(cacheKey)
                                        .map(deleted -> {
                                            span.setStatus(StatusCode.OK);
                                            CategoryResponse response = CategoryResponse.from(savedCategory);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "update_category",
                                                    AttributeKey.stringKey("status"), "success"));

                                            logger.info("✅ Category updated successfully with id={}", response.getId());
                                            return ApiResponse.success("✅ Category updated successfully!", response);
                                        });
                            });
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to update category id={}", req.getCategoryId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_category",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_category"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<CategoryResponseDeleteAt>> trashedCategory(Integer categoryId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashedCategory")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-service")
                .setAttribute("operation", "trash_category")
                .setAttribute("category.id", categoryId.toString())
                .startSpan();

        logger.info("🗑️ Trashing category id={}", categoryId);

        return categoryCommandRepository.trashed(categoryId.longValue())
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException("Category not found"))
                .chain(category -> {
                    String cacheKey = "category:" + categoryId;
                    return redisService.deleteReactive(cacheKey)
                            .map(deleted -> {
                                span.setStatus(StatusCode.OK);
                                CategoryResponseDeleteAt response = CategoryResponseDeleteAt.from(category);
                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_category",
                                        AttributeKey.stringKey("status"), "success"));

                                logger.info("🗑️ Category trashed successfully with id={}", categoryId);
                                return ApiResponse.success("🗑️ Category trashed successfully!", response);
                            });
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to trash category id={}", categoryId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_category",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to trash category: " + e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_category"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<CategoryResponseDeleteAt>> restoreCategory(Integer categoryId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreCategory")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-service")
                .setAttribute("operation", "restore_category")
                .setAttribute("category.id", categoryId.toString())
                .startSpan();

        logger.info("♻️ Restoring category id={}", categoryId);

        return categoryCommandRepository.restore(categoryId.longValue())
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException("Category not found or not trashed"))
                .chain(category -> {
                    String cacheKey = "category:" + categoryId;
                    return redisService.deleteReactive(cacheKey)
                            .map(deleted -> {
                                span.setStatus(StatusCode.OK);
                                CategoryResponseDeleteAt response = CategoryResponseDeleteAt.from(category);
                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_category",
                                        AttributeKey.stringKey("status"), "success"));

                                logger.info("♻️ Category restored successfully with id={}", categoryId);
                                return ApiResponse.success("♻️ Category restored successfully!", response);
                            });
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to restore category id={}", categoryId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_category",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to restore category: " + e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_category"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteCategoryPermanent(Integer categoryId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteCategoryPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-service")
                .setAttribute("operation", "delete_category_permanent")
                .setAttribute("category.id", categoryId.toString())
                .startSpan();

        logger.info("🧨 Permanently deleting category id={}", categoryId);

        return categoryCommandRepository.deletePermanent(categoryId.longValue())
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException("Category not found or not trashed"))
                .chain(category -> {
                    String cacheKey = "category:" + categoryId;
                    return redisService.deleteReactive(cacheKey)
                            .map(deleted -> {
                                span.setStatus(StatusCode.OK);
                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_category_permanent",
                                        AttributeKey.stringKey("status"), "success"));

                                logger.info("🧨 Category permanently deleted with id={}", categoryId);
                                return ApiResponse.success("🧨 Category permanently deleted!", true);
                            });
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to permanently delete category id={}", categoryId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_category_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to permanently delete category: " + e.getMessage(), false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_permanent"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAllCategories() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllCategories")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-service")
                .setAttribute("operation", "restore_all_categories")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed categories");

        return categoryCommandRepository.restoreAllDeleted()
                .map(success -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_categories",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("🔄 All categories restored successfully!");
                    return ApiResponse.success("🔄 All categories restored successfully!", true);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to restore all categories", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_categories",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to restore all categories: " + e.getMessage(), false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_categories"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAllCategoriesPermanent() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllCategoriesPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "category-service")
                .setAttribute("operation", "delete_all_categories_permanent")
                .startSpan();

        logger.info("💣 Permanently deleting ALL trashed categories");

        return categoryCommandRepository.deleteAllDeleted()
                .map(success -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_categories_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("💣 All categories permanently deleted!");
                    return ApiResponse.success("💣 All categories permanently deleted!", true);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to delete all categories", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_categories_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to delete all categories: " + e.getMessage(), false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_categories_permanent"));
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
