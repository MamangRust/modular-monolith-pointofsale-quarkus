package com.sanedge.order.service.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.order.domain.requests.CreateOrderRequest;
import com.sanedge.order.domain.requests.UpdateOrderRequest;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.domain.response.OrderResponseDeleteAt;
import com.sanedge.order.entity.Order;
import com.sanedge.order.repository.OrderCommandRepository;
import com.sanedge.order.repository.OrderQueryRepository;
import com.sanedge.order.service.OrderCommandService;

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
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ApplicationScoped
public class OrderCommandServiceImpl implements OrderCommandService {
    private static final Logger logger = LoggerFactory.getLogger(OrderCommandServiceImpl.class);

    OrderQueryRepository orderQueryRepository;
    OrderCommandRepository orderCommandRepository;
    Validator validator;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    @GrpcClient("merchant")
    pb.merchant.MutinyMerchantQueryServiceGrpc.MutinyMerchantQueryServiceStub merchantQueryService;

    @GrpcClient("cashier")
    pb.cashier.MutinyCashierServiceGrpc.MutinyCashierServiceStub cashierQueryService;

    @GrpcClient("product")
    pb.product.MutinyProductServiceGrpc.MutinyProductServiceStub productQueryService;

    @GrpcClient("product")
    pb.product.MutinyProductCommandServiceGrpc.MutinyProductCommandServiceStub productCommandService;

    @GrpcClient("order_item")
    pb.order_item.MutinyOrderItemCommandServiceGrpc.MutinyOrderItemCommandServiceStub orderItemCommandService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public OrderCommandServiceImpl(OrderQueryRepository orderQueryRepository,
            OrderCommandRepository orderCommandRepository,
            Validator validator,
            OpenTelemetry openTelemetry,
            RedisService redisService) {
        this.orderQueryRepository = orderQueryRepository;
        this.orderCommandRepository = orderCommandRepository;
        this.validator = validator;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("order-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("order-command-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
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

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderResponse>> create(CreateOrderRequest request) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createOrder")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "create_order")
                .setAttribute("merchant.id",
                        request.getMerchantId() != null ? request.getMerchantId().toString() : "null")
                .setAttribute("cashier.id", request.getCashierId() != null ? request.getCashierId().toString() : "null")
                .startSpan();

        logger.info("🆕 Creating new order for merchantId={} and cashierId={}", request.getMerchantId(),
                request.getCashierId());

        try {
            validateRequest(request);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", e.getMessage(), null));
        }

        return merchantQueryService.findByIdMerchant(pb.merchant.Merchant.FindByIdMerchantRequest.newBuilder()
                .setMerchantId(request.getMerchantId())
                .build())
                .chain(merchantResponse -> {
                    if (!"success".equals(merchantResponse.getStatus()) || !merchantResponse.hasData()) {
                        logger.error("❌ Merchant not found with id={}", request.getMerchantId());
                        throw new ResourceNotFoundException("Merchant not found");
                    }
                    return cashierQueryService.findById(pb.cashier.Cashier.FindByIdCashierRequest.newBuilder()
                            .setId(request.getCashierId())
                            .build());
                })
                .chain(cashierResponse -> {
                    if (!"success".equals(cashierResponse.getStatus()) || !cashierResponse.hasData()) {
                        logger.error("❌ Cashier not found with id={}", request.getCashierId());
                        throw new ResourceNotFoundException("Cashier not found");
                    }

                    Order order = new Order();
                    order.setMerchantId(request.getMerchantId().longValue());
                    order.setCashierId(request.getCashierId().longValue());
                    order.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    order.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    order.setTotalPrice(0L);

                    return orderCommandRepository.persist(order).map(v -> order);
                })
                .chain(order -> {
                    return Multi.createFrom().iterable(request.getItems())
                            .onItem().transformToUniAndConcatenate(itemReq -> {
                                return productQueryService.findById(pb.product.Product.FindByIdProductRequest.newBuilder()
                                        .setId(itemReq.getProductId())
                                        .build())
                                        .chain(productResponse -> {
                                            if (!"success".equals(productResponse.getStatus()) || !productResponse.hasData()) {
                                                logger.error("❌ Product not found with id={}", itemReq.getProductId());
                                                throw new ResourceNotFoundException(
                                                        "Product not found with id=" + itemReq.getProductId());
                                            }
                                            pb.product.Product.ProductResponse product = productResponse.getData();
                                            if (product.getCountInStock() < itemReq.getQuantity()) {
                                                logger.error("❌ Insufficient stock for product id={}",
                                                        itemReq.getProductId());
                                                throw new IllegalArgumentException(
                                                        "Insufficient stock for product id=" + itemReq.getProductId());
                                            }

                                            pb.order_item.OrderItemCommand.CreateOrderItemRequest orderItemReq = pb.order_item.OrderItemCommand.CreateOrderItemRequest.newBuilder()
                                                    .setOrderId(order.getOrderId().intValue())
                                                    .setProductId(itemReq.getProductId())
                                                    .setQuantity(itemReq.getQuantity())
                                                    .setPrice(itemReq.getPrice())
                                                    .build();

                                            pb.product.ProductCommand.UpdateProductRequest productReq = pb.product.ProductCommand.UpdateProductRequest.newBuilder()
                                                    .setProductId(product.getId())
                                                    .setMerchantId(product.getMerchantId())
                                                    .setCategoryId(product.getCategoryId())
                                                    .setName(product.getName())
                                                    .setDescription(product.getDescription())
                                                    .setPrice(product.getPrice())
                                                    .setCountInStock(product.getCountInStock() - itemReq.getQuantity())
                                                    .setBrand(product.getBrand())
                                                    .setWeight(product.getWeight())
                                                    .setImageProduct(product.getImageProduct())
                                                    .build();

                                            return Uni.combine().all().unis(
                                                    orderItemCommandService.createOrderItem(orderItemReq),
                                                    productCommandService.update(productReq)).asTuple()
                                                    .map(t -> itemReq.getQuantity().longValue() * itemReq.getPrice().longValue());
                                        });
                            })
                            .collect().in(java.util.ArrayList::new, List::add)
                            .map(list -> {
                                long sum = list.stream().mapToLong(val -> ((Number) val).longValue()).sum();
                                order.setTotalPrice(sum);
                                return order;
                            });
                })
                .chain(order -> {
                    return orderCommandRepository.persist(order).map(v -> order);
                })
                .map(savedOrder -> {
                    span.setAttribute("order.id", savedOrder.getOrderId());
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_order",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Order created successfully with id={}", savedOrder.getOrderId());
                    return ApiResponse.success("Order created successfully", OrderResponse.from(savedOrder));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to create order", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_order",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to create your order: " + e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_order"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderResponse>> update(UpdateOrderRequest request) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateOrder")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "update_order")
                .setAttribute("order.id", request.getOrderId() != null ? request.getOrderId().toString() : "null")
                .startSpan();

        logger.info("🔄 Updating order id={}", request.getOrderId());

        try {
            validateRequest(request);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.end();
            return Uni.createFrom().item(new ApiResponse<>("error", e.getMessage(), null));
        }

        String cacheKey = "order:" + request.getOrderId();

        return orderQueryRepository.findOrderById(request.getOrderId().longValue())
                .chain(order -> {
                    if (order == null) {
                        logger.error("❌ Order not found with id={}", request.getOrderId());
                        throw new ResourceNotFoundException("Order not found");
                    }

                    return cashierQueryService.findById(pb.cashier.Cashier.FindByIdCashierRequest.newBuilder()
                            .setId(request.getCashierId())
                            .build())
                            .chain(cashierResponse -> {
                                if (!"success".equals(cashierResponse.getStatus()) || !cashierResponse.hasData()) {
                                    logger.error("❌ Cashier not found with id={}", request.getCashierId());
                                    throw new ResourceNotFoundException("Cashier not found");
                                }
                                return Uni.createFrom().item(order);
                            });
                })
                .chain(order -> {
                    return Multi.createFrom().iterable(request.getItems())
                            .onItem().transformToUniAndConcatenate(itemReq -> {
                                return productQueryService.findById(pb.product.Product.FindByIdProductRequest.newBuilder()
                                        .setId(itemReq.getProductId())
                                        .build())
                                        .chain(productResponse -> {
                                            if (!"success".equals(productResponse.getStatus()) || !productResponse.hasData()) {
                                                logger.error("❌ Product not found with id={}", itemReq.getProductId());
                                                throw new ResourceNotFoundException(
                                                        "Product not found with id=" + itemReq.getProductId());
                                            }
                                            pb.product.Product.ProductResponse product = productResponse.getData();

                                            if (itemReq.getOrderItemId() != null && itemReq.getOrderItemId() > 0) {
                                                return orderItemCommandService.updateOrderItem(pb.order_item.OrderItemCommand.UpdateOrderItemRequest.newBuilder()
                                                        .setOrderItemId(itemReq.getOrderItemId())
                                                        .setOrderId(order.getOrderId().intValue())
                                                        .setProductId(itemReq.getProductId())
                                                        .setQuantity(itemReq.getQuantity())
                                                        .setPrice(itemReq.getPrice())
                                                        .build())
                                                        .map(resp -> {
                                                            if (!"success".equals(resp.getStatus())) {
                                                                throw new ResourceNotFoundException("Order item not found");
                                                            }
                                                            return itemReq.getQuantity().longValue() * itemReq.getPrice().longValue();
                                                        });
                                            } else {
                                                if (product.getCountInStock() < itemReq.getQuantity()) {
                                                    logger.error("❌ Insufficient stock for product id={}",
                                                            itemReq.getProductId());
                                                    throw new IllegalArgumentException(
                                                            "Insufficient stock for product id="
                                                                    + itemReq.getProductId());
                                                }

                                                pb.order_item.OrderItemCommand.CreateOrderItemRequest orderItemReq = pb.order_item.OrderItemCommand.CreateOrderItemRequest.newBuilder()
                                                        .setOrderId(order.getOrderId().intValue())
                                                        .setProductId(itemReq.getProductId())
                                                        .setQuantity(itemReq.getQuantity())
                                                        .setPrice(itemReq.getPrice())
                                                        .build();

                                                pb.product.ProductCommand.UpdateProductRequest productReq = pb.product.ProductCommand.UpdateProductRequest.newBuilder()
                                                        .setProductId(product.getId())
                                                        .setMerchantId(product.getMerchantId())
                                                        .setCategoryId(product.getCategoryId())
                                                        .setName(product.getName())
                                                        .setDescription(product.getDescription())
                                                        .setPrice(product.getPrice())
                                                        .setCountInStock(product.getCountInStock() - itemReq.getQuantity())
                                                        .setBrand(product.getBrand())
                                                        .setWeight(product.getWeight())
                                                        .setImageProduct(product.getImageProduct())
                                                        .build();

                                                return Uni.combine().all().unis(
                                                        orderItemCommandService.createOrderItem(orderItemReq),
                                                        productCommandService.update(productReq)).asTuple()
                                                        .map(t -> itemReq.getQuantity().longValue() * itemReq.getPrice().longValue());
                                            }
                                        });
                            })
                            .collect().in(java.util.ArrayList::new, List::add)
                            .map(list -> {
                                long sum = list.stream().mapToLong(val -> ((Number) val).longValue()).sum();
                                order.setTotalPrice(sum);
                                order.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                                return order;
                            });
                })
                .chain(order -> {
                    return orderCommandRepository.persist(order).map(v -> order);
                })
                .chain(savedOrder -> {
                    return redisService.deleteReactive(cacheKey)
                            .map(v -> savedOrder);
                })
                .map(savedOrder -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_order",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Order updated successfully with id={}", savedOrder.getOrderId());
                    return ApiResponse.success("Order updated successfully", OrderResponse.from(savedOrder));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to update order id={}", request.getOrderId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_order",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to update your order: " + e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_order"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderResponseDeleteAt>> trash(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashOrder")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "trash_order")
                .setAttribute("order.id", id.toString())
                .startSpan();

        logger.info("🗑️ Trashing order id={}", id);

        String cacheKey = "order:" + id;

        return orderCommandRepository.trashed(id.longValue())
                .chain(order -> {
                    if (order == null) {
                        logger.error("❌ Order not found for trashing id={}", id);
                        throw new ResourceNotFoundException("Order not found");
                    }
                    return redisService.deleteReactive(cacheKey).map(v -> order);
                })
                .map(order -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_order",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Order trashed successfully with id={}", id);
                    return ApiResponse.success("Order trashed successfully", OrderResponseDeleteAt.from(order));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to trash order id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_order",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to trash order: " + e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_order"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderResponseDeleteAt>> restore(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreOrder")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "restore_order")
                .setAttribute("order.id", id.toString())
                .startSpan();

        logger.info("♻️ Restoring order id={}", id);

        String cacheKey = "order:" + id;

        return orderCommandRepository.restore(id.longValue())
                .chain(order -> {
                    if (order == null) {
                        logger.error("❌ Order not found for restoration id={}", id);
                        throw new ResourceNotFoundException("Order not found or not in trash");
                    }
                    return redisService.deleteReactive(cacheKey).map(v -> order);
                })
                .map(order -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_order",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Order restored successfully with id={}", id);
                    return ApiResponse.success("Order restored successfully", OrderResponseDeleteAt.from(order));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to restore order id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_order",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to restore order: " + e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_order"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> delete(Integer id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteOrderPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "delete_order")
                .setAttribute("order.id", id.toString())
                .startSpan();

        logger.info("🧨 Permanently deleting order id={}", id);

        String cacheKey = "order:" + id;

        return orderCommandRepository.deletePermanent(id.longValue())
                .chain(order -> {
                    if (order == null) {
                        logger.error("❌ Order not found for permanent deletion id={}", id);
                        throw new ResourceNotFoundException("Order not found or not in trash");
                    }
                    return redisService.deleteReactive(cacheKey).map(v -> true);
                })
                .map(deleted -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_order",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Order permanently deleted with id={}", id);
                    return ApiResponse.success("Order permanently deleted successfully", true);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to permanently delete order id={}", id, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_order",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to permanently delete order: " + e.getMessage(), false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_order"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllOrders")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "restore_all")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed orders");

        return orderCommandRepository.restoreAllDeleted()
                .map(restored -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ All orders restored successfully");
                    return ApiResponse.success("All orders restored successfully", true);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to restore all orders", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to restore all orders: " + e.getMessage(), false);
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
        Span span = tracer.spanBuilder("deleteAllOrders")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "delete_all")
                .startSpan();

        logger.info("💣 Permanently deleting ALL trashed orders");

        return orderCommandRepository.deleteAllDeleted()
                .map(deleted -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ All orders permanently deleted successfully");
                    return ApiResponse.success("All orders permanently deleted successfully", true);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to delete all orders", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to delete all orders: " + e.getMessage(), false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<OrderResponse>> updateOrderTotalPrice(Integer orderId, Integer totalPrice) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateOrderTotalPrice")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "order-service")
                .setAttribute("operation", "update_order_total_price")
                .setAttribute("order.id", orderId != null ? orderId.toString() : "null")
                .startSpan();

        logger.info("🔄 Updating order total price for orderId={} to totalPrice={}", orderId, totalPrice);

        return orderQueryRepository.findOrderById(orderId.longValue())
                .chain(order -> {
                    if (order == null) {
                        logger.error("❌ Order not found with id={}", orderId);
                        throw new ResourceNotFoundException("Order not found with id=" + orderId);
                    }
                    order.setTotalPrice(totalPrice.longValue());
                    return orderCommandRepository.persist(order);
                })
                .map(savedOrder -> {
                    span.setStatus(StatusCode.OK);

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_order_total_price",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("✅ Order total price updated successfully for id={}", savedOrder.getOrderId());
                    return ApiResponse.success("Order total price updated successfully", OrderResponse.from(savedOrder));
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to update order total price", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_order_total_price",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to update order total price: " + e.getMessage(), null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_order_total_price"));
                });
    }
}
