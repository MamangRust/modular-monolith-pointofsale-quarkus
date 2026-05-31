package com.sanedge.order_item.service.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.order_item.entity.OrderItem;
import com.sanedge.order_item.repository.OrderItemRepository;
import com.sanedge.order_item.domain.requests.CreateOrderItemRequest;
import com.sanedge.order_item.domain.requests.UpdateOrderItemRequest;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;
import com.sanedge.order_item.service.OrderItemCommandService;

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
import io.quarkus.grpc.GrpcClient;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ApplicationScoped
public class OrderItemCommandServiceImpl implements OrderItemCommandService {
    private static final Logger logger = LoggerFactory.getLogger(OrderItemCommandServiceImpl.class);

    OrderItemRepository orderItemRepository;
    Validator validator;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    @GrpcClient("order")
    pb.order.MutinyOrderQueryServiceGrpc.MutinyOrderQueryServiceStub orderQueryService;

    @GrpcClient("order")
    pb.order.MutinyOrderCommandServiceGrpc.MutinyOrderCommandServiceStub orderCommandService;

    @GrpcClient("product")
    pb.product.MutinyProductServiceGrpc.MutinyProductServiceStub productQueryService;

    @GrpcClient("product")
    pb.product.MutinyProductCommandServiceGrpc.MutinyProductCommandServiceStub productCommandService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public OrderItemCommandServiceImpl(OrderItemRepository orderItemRepository,
                                       Validator validator,
                                       OpenTelemetry openTelemetry,
                                       RedisService redisService) {
        this.orderItemRepository = orderItemRepository;
        this.validator = validator;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("order-item-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("order-item-command-service");

        this.requestsTotal = meter.counterBuilder("order_item_requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("order_item_request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
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

    private Uni<Void> updateOrderTotalPrice(Long orderId) {
        return orderItemRepository.findOrderItemByOrder(orderId)
                .chain(items -> {
                    long total = items.stream().mapToLong(item -> item.getPrice().longValue() * item.getQuantity()).sum();
                    return orderCommandService.updateOrderTotalPrice(pb.order.OrderCommand.UpdateOrderTotalPriceRequest.newBuilder()
                            .setOrderId(orderId.intValue())
                            .setTotalPrice((int) total)
                            .build())
                            .map(res -> {
                                if ("success".equals(res.getStatus())) {
                                    logger.info("Order total price updated successfully for orderId={}", orderId);
                                } else {
                                    logger.error("Failed to update order total price for orderId={}: {}", orderId, res.getMessage());
                                }
                                return null;
                            })
                            .replaceWithVoid();
                });
    }

    private Uni<Void> clearCache(Long orderId) {
        if (orderId == null) {
            return Uni.createFrom().voidItem();
        }
        String cacheKey = "order_item:by_order:" + orderId;
        return redisService.deleteReactive(cacheKey);
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderItemResponse>> create(CreateOrderItemRequest request) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createOrderItem")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-item-service")
                .setAttribute("operation", "create_order_item")
                .setAttribute("order.id", request.getOrderId() != null ? request.getOrderId().toString() : "null")
                .setAttribute("product.id", request.getProductId() != null ? request.getProductId().toString() : "null")
                .startSpan();

        logger.info("🆕 Creating new order item for orderId={} and productId={}", request.getOrderId(), request.getProductId());

        try {
            validateRequest(request);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", e.getMessage(), null));
        }

        return orderQueryService.findById(pb.order.Order.FindByIdOrderRequest.newBuilder()
                .setId(request.getOrderId().intValue())
                .build())
                .chain(orderRes -> {
                    if (orderRes == null || !"success".equals(orderRes.getStatus())) {
                        logger.error("❌ Order not found with id={}", request.getOrderId());
                        throw new ResourceNotFoundException("Order not found");
                    }
                    return productQueryService.findById(pb.product.Product.FindByIdProductRequest.newBuilder()
                            .setId(request.getProductId().intValue())
                            .build());
                })
                .chain(productRes -> {
                    if (productRes == null || !"success".equals(productRes.getStatus())) {
                        logger.error("❌ Product not found with id={}", request.getProductId());
                        throw new ResourceNotFoundException("Product not found with id=" + request.getProductId());
                    }
                    var product = productRes.getData();
                    if (product.getCountInStock() < request.getQuantity()) {
                        logger.error("❌ Insufficient stock for product id={}", request.getProductId());
                        throw new IllegalArgumentException("Insufficient stock for product id=" + request.getProductId());
                    }

                    OrderItem orderItem = new OrderItem();
                    orderItem.setOrderId(request.getOrderId().longValue());
                    orderItem.setProductId(request.getProductId().longValue());
                    orderItem.setQuantity(request.getQuantity());
                    orderItem.setPrice(request.getPrice());
                    orderItem.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    orderItem.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                    pb.product.ProductCommand.UpdateProductRequest updateReq = pb.product.ProductCommand.UpdateProductRequest.newBuilder()
                            .setProductId(product.getId())
                            .setMerchantId(product.getMerchantId())
                            .setCategoryId(product.getCategoryId())
                            .setName(product.getName())
                            .setDescription(product.getDescription())
                            .setPrice(product.getPrice())
                            .setCountInStock(product.getCountInStock() - request.getQuantity())
                            .setBrand(product.getBrand())
                            .setWeight(product.getWeight())
                            .setImageProduct(product.getImageProduct())
                            .build();

                    return Uni.combine().all().unis(
                            orderItemRepository.persist(orderItem),
                            productCommandService.update(updateReq)
                    ).asTuple().map(t -> t.getItem1());
                })
                .chain(savedItem -> {
                    return updateOrderTotalPrice(request.getOrderId().longValue())
                            .chain(() -> clearCache(request.getOrderId().longValue()))
                            .map(v -> savedItem);
                })
                .map(savedItem -> {
                    span.setAttribute("order_item.id", savedItem.getOrderItemId());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_order_item",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Order item created successfully with id={}", savedItem.getOrderItemId());
                    return ApiResponse.success("Order item created successfully", OrderItemResponse.from(savedItem));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to create order item", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_order_item",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to create your order item: " + e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_order_item"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderItemResponse>> update(UpdateOrderItemRequest request) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateOrderItem")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-item-service")
                .setAttribute("operation", "update_order_item")
                .setAttribute("order_item.id", request.getOrderItemId() != null ? request.getOrderItemId().toString() : "null")
                .startSpan();

        logger.info("🔄 Updating order item id={}", request.getOrderItemId());

        try {
            validateRequest(request);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", e.getMessage(), null));
        }

        return orderItemRepository.findById(request.getOrderItemId().longValue())
                .chain(existingItem -> {
                    if (existingItem == null) {
                        logger.error("❌ Order item not found with id={}", request.getOrderItemId());
                        throw new ResourceNotFoundException("Order item not found");
                    }

                    return productQueryService.findById(pb.product.Product.FindByIdProductRequest.newBuilder()
                            .setId(request.getProductId().intValue())
                            .build())
                            .chain(productRes -> {
                                if (productRes == null || !"success".equals(productRes.getStatus())) {
                                    logger.error("❌ Product not found with id={}", request.getProductId());
                                    throw new ResourceNotFoundException("Product not found with id=" + request.getProductId());
                                }
                                var product = productRes.getData();

                                int diff = request.getQuantity() - existingItem.getQuantity();
                                if (diff > 0 && product.getCountInStock() < diff) {
                                    logger.error("❌ Insufficient stock for product id={}", request.getProductId());
                                    throw new IllegalArgumentException("Insufficient stock for product id=" + request.getProductId());
                                }

                                existingItem.setOrderId(request.getOrderId().longValue());
                                existingItem.setProductId(request.getProductId().longValue());
                                existingItem.setQuantity(request.getQuantity());
                                existingItem.setPrice(request.getPrice());
                                existingItem.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                                pb.product.ProductCommand.UpdateProductRequest updateReq = pb.product.ProductCommand.UpdateProductRequest.newBuilder()
                                        .setProductId(product.getId())
                                        .setMerchantId(product.getMerchantId())
                                        .setCategoryId(product.getCategoryId())
                                        .setName(product.getName())
                                        .setDescription(product.getDescription())
                                        .setPrice(product.getPrice())
                                        .setCountInStock(product.getCountInStock() - diff)
                                        .setBrand(product.getBrand())
                                        .setWeight(product.getWeight())
                                        .setImageProduct(product.getImageProduct())
                                        .build();

                                return Uni.combine().all().unis(
                                        orderItemRepository.persist(existingItem),
                                        productCommandService.update(updateReq)
                                ).asTuple().map(t -> t.getItem1());
                            });
                })
                .chain(savedItem -> {
                    return updateOrderTotalPrice(request.getOrderId().longValue())
                            .chain(() -> clearCache(request.getOrderId().longValue()))
                            .map(v -> savedItem);
                })
                .map(savedItem -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_order_item",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Order item updated successfully with id={}", savedItem.getOrderItemId());
                    return ApiResponse.success("Order item updated successfully", OrderItemResponse.from(savedItem));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to update order item id={}", request.getOrderItemId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_order_item",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to update order item: " + e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_order_item"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderItemResponseDeleteAt>> trash(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashOrderItem")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-item-service")
                .setAttribute("operation", "trash_order_item")
                .setAttribute("order_item.id", id.toString())
                .startSpan();

        logger.info("🗑️ Trashing order item id={}", id);

        return orderItemRepository.trashed(id.longValue())
                .chain(item -> {
                    if (item == null) {
                        logger.error("❌ Order item not found for trashing id={}", id);
                        throw new ResourceNotFoundException("Order item not found");
                    }
                    return updateOrderTotalPrice(item.getOrderId())
                            .chain(() -> clearCache(item.getOrderId()))
                            .map(v -> item);
                })
                .map(item -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_order_item",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Order item trashed successfully with id={}", id);
                    return ApiResponse.success("Order item trashed successfully", OrderItemResponseDeleteAt.from(item));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to trash order item id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_order_item",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to trash order item: " + e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_order_item"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderItemResponseDeleteAt>> restore(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreOrderItem")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-item-service")
                .setAttribute("operation", "restore_order_item")
                .setAttribute("order_item.id", id.toString())
                .startSpan();

        logger.info("♻️ Restoring order item id={}", id);

        return orderItemRepository.restore(id.longValue())
                .chain(item -> {
                    if (item == null) {
                        logger.error("❌ Order item not found for restoration id={}", id);
                        throw new ResourceNotFoundException("Order item not found or not in trash");
                    }
                    return updateOrderTotalPrice(item.getOrderId())
                            .chain(() -> clearCache(item.getOrderId()))
                            .map(v -> item);
                })
                .map(item -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_order_item",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Order item restored successfully with id={}", id);
                    return ApiResponse.success("Order item restored successfully", OrderItemResponseDeleteAt.from(item));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to restore order item id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_order_item",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to restore order item: " + e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_order_item"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> delete(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteOrderItemPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-item-service")
                .setAttribute("operation", "delete_order_item")
                .setAttribute("order_item.id", id.toString())
                .startSpan();

        logger.info("🧨 Permanently deleting order item id={}", id);

        return orderItemRepository.deletePermanent(id.longValue())
                .chain(item -> {
                    if (item == null) {
                        logger.error("❌ Order item not found for permanent deletion id={}", id);
                        throw new ResourceNotFoundException("Order item not found or not in trash");
                    }
                    return updateOrderTotalPrice(item.getOrderId())
                            .chain(() -> clearCache(item.getOrderId()))
                            .map(v -> true);
                })
                .map(deleted -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_order_item",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Order item permanently deleted with id={}", id);
                    return ApiResponse.success("Order item permanently deleted successfully", true);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to permanently delete order item id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_order_item",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to permanently delete order item: " + e.getMessage(), false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_order_item"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllOrderItems")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-item-service")
                .setAttribute("operation", "restore_all")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed order items");

        return orderItemRepository.restoreAllDeleted()
                .map(restored -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ All order items restored successfully");
                    return ApiResponse.success("All order items restored successfully", true);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to restore all order items", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to restore all order items: " + e.getMessage(), false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllOrderItems")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-item-service")
                .setAttribute("operation", "delete_all")
                .startSpan();

        logger.info("Bom Permanently deleting ALL trashed order items");

        return orderItemRepository.deleteAllDeleted()
                .map(deleted -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ All order items permanently deleted successfully");
                    return ApiResponse.success("All order items permanently deleted successfully", true);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to delete all order items", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to delete all order items: " + e.getMessage(), false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all"));
                });
    }
}
