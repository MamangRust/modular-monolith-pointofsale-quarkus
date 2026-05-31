package com.sanedge.transaction.service.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.enums.PaymentStatus;
import io.quarkus.grpc.GrpcClient;
import com.sanedge.transaction.domain.requests.CreateTransactionRequest;
import com.sanedge.transaction.domain.requests.UpdateTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionResponse;
import com.sanedge.transaction.domain.response.TransactionResponseDeleteAt;
import com.sanedge.transaction.entity.Transaction;
import com.sanedge.transaction.repository.TransactionCommandRepository;
import com.sanedge.transaction.repository.TransactionQueryRepository;
import com.sanedge.transaction.service.TransactionCommandService;

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

@ApplicationScoped
public class TransactionCommandServiceImpl implements TransactionCommandService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionCommandServiceImpl.class);

    TransactionQueryRepository transactionQueryRepository;
    TransactionCommandRepository transactionCommandRepository;

    @Inject
    @GrpcClient("merchant")
    pb.merchant.MutinyMerchantQueryServiceGrpc.MutinyMerchantQueryServiceStub merchantQueryService;

    @Inject
    @GrpcClient("order")
    pb.order.MutinyOrderQueryServiceGrpc.MutinyOrderQueryServiceStub orderQueryService;

    @Inject
    @GrpcClient("order_item")
    pb.order_item.MutinyOrderItemServiceGrpc.MutinyOrderItemServiceStub orderItemQueryService;

    OpenTelemetry openTelemetry;
    RedisService redisService;
    ObjectMapper objectMapper;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public TransactionCommandServiceImpl(TransactionQueryRepository transactionQueryRepository,
            TransactionCommandRepository transactionCommandRepository,
            OpenTelemetry openTelemetry,
            RedisService redisService,
            ObjectMapper objectMapper) {
        this.transactionQueryRepository = transactionQueryRepository;
        this.transactionCommandRepository = transactionCommandRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.tracer = openTelemetry.getTracer("transaction-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("transaction-command-service");

        this.requestsTotal = meter.counterBuilder("requests_total")
                .setDescription("Total number of requests")
                .build();
        this.requestDurationSeconds = meter.histogramBuilder("request_duration_seconds")
                .setDescription("Request duration in seconds")
                .setUnit("s")
                .build();
    }

    private Uni<Void> clearCache(Long transactionId, Long orderId) {
        String idKey = "transaction:id:" + transactionId;
        String orderKey = "transaction:order:" + orderId;

        return Uni.combine().all().unis(
                redisService.deleteReactive(idKey),
                redisService.deleteReactive(orderKey)).discardItems();
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<TransactionResponse>> create(CreateTransactionRequest req) {
        logger.info("💳 Creating new transaction | orderId={}, merchantId={}", req.getOrderID(), req.getMerchantID());
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "create_transaction")
                .setAttribute("merchantId", req.getMerchantID())
                .setAttribute("orderId", req.getOrderID())
                .startSpan();

        if (req.getMerchantID() == null || req.getOrderID() == null || req.getAmount() == null
                || req.getPaymentMethod() == null) {
            span.setStatus(StatusCode.ERROR, "Missing required fields");
            span.end();
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "All fields are required", (TransactionResponse) null));
        }

        return merchantQueryService.findByIdMerchant(pb.merchant.Merchant.FindByIdMerchantRequest.newBuilder()
                        .setMerchantId(req.getMerchantID())
                        .build())
                .onItem().transformToUni(merchantResp -> {
                    if (merchantResp == null || !merchantResp.hasData() || !"success".equalsIgnoreCase(merchantResp.getStatus())) {
                        logger.error("❌ Merchant not found | merchantId={}", req.getMerchantID());
                        return Uni.createFrom().item(new ApiResponse<>("error", "Merchant not found", (TransactionResponse) null));
                    }

                    return orderQueryService.findById(pb.order.Order.FindByIdOrderRequest.newBuilder()
                                    .setId(req.getOrderID())
                                    .build())
                            .onItem().transformToUni(orderResp -> {
                                if (orderResp == null || !orderResp.hasData() || !"success".equalsIgnoreCase(orderResp.getStatus())) {
                                    logger.error("❌ Order not found | orderId={}", req.getOrderID());
                                    return Uni.createFrom().item(new ApiResponse<>("error", "Order not found", (TransactionResponse) null));
                                }

                                return orderItemQueryService.findOrderItemByOrder(pb.order_item.OrderItem.FindByIdOrderItemRequest.newBuilder()
                                                .setOrderItemId(req.getOrderID())
                                                .build())
                                        .onItem().transformToUni(orderItemsResp -> {
                                            if (orderItemsResp == null || orderItemsResp.getDataCount() == 0 || !"success".equalsIgnoreCase(orderItemsResp.getStatus())) {
                                                logger.error("❌ No order items found | orderId={}", req.getOrderID());
                                                return Uni.createFrom().item(new ApiResponse<>("error", "No order items found", (TransactionResponse) null));
                                            }

                                            int totalAmount = 0;
                                            for (var item : orderItemsResp.getDataList()) {
                                                if (item.getQuantity() <= 0) {
                                                    return Uni.createFrom().item(new ApiResponse<>("error", "Invalid order item quantity", (TransactionResponse) null));
                                                }
                                                totalAmount += item.getPrice() * item.getQuantity();
                                            }
                                            int ppn = totalAmount * 11 / 100;
                                            int totalAmountWithTax = totalAmount + ppn;

                                            String paymentStatus = req.getAmount() >= totalAmountWithTax ? "success"
                                                    : "failed";
                                            if ("failed".equals(paymentStatus)) {
                                                logger.error("❌ Insufficient payment amount | amount={}, required={}",
                                                        req.getAmount(), totalAmountWithTax);
                                                return Uni.createFrom().item(new ApiResponse<>("error",
                                                        "Insufficient payment amount", (TransactionResponse) null));
                                            }

                                            req.setAmount(totalAmountWithTax);
                                            req.setPaymentStatus(paymentStatus);

                                            Transaction transaction = new Transaction();
                                            transaction.setOrderId(req.getOrderID().longValue());
                                            transaction.setMerchantId(req.getMerchantID().longValue());
                                            transaction.setPaymentMethod(req.getPaymentMethod());
                                            transaction.setAmount(req.getAmount());
                                            transaction.setStatus(PaymentStatus.fromValue(req.getPaymentStatus()));
                                            transaction.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                                            transaction.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                                            return transactionCommandRepository.persist(transaction)
                                                    .chain(savedTx -> {
                                                        return clearCache(savedTx.getTransactionId(),
                                                                savedTx.getOrderId())
                                                                .map(v -> {
                                                                    span.setStatus(StatusCode.OK);
                                                                    requestsTotal.add(1, Attributes.of(
                                                                            AttributeKey.stringKey("operation"),
                                                                            "create_transaction",
                                                                            AttributeKey.stringKey("status"),
                                                                            "success"));
                                                                    logger.info(
                                                                            "✅ Transaction created successfully | transactionId={}",
                                                                            savedTx.getTransactionId());
                                                                    return ApiResponse.success(
                                                                            "Transaction created successfully",
                                                                            TransactionResponse.from(savedTx));
                                                                });
                                                    });
                                        });
                            });
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to create transaction: {}", e.getMessage(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_transaction",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to create transaction: " + e.getMessage(),
                            (TransactionResponse) null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration,
                            Attributes.of(AttributeKey.stringKey("operation"), "create_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<TransactionResponse>> update(UpdateTransactionRequest req) {
        logger.info("✏️ Updating transaction | transactionId={}", req.getTransactionID());
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "update_transaction")
                .setAttribute("transactionId", req.getTransactionID())
                .startSpan();

        if (req.getTransactionID() == null || req.getMerchantID() == null || req.getOrderID() == null
                || req.getAmount() == null || req.getPaymentMethod() == null) {
            span.setStatus(StatusCode.ERROR, "Missing required fields");
            span.end();
            return Uni.createFrom()
                    .item(new ApiResponse<>("error", "All fields are required", (TransactionResponse) null));
        }

        return transactionQueryRepository.findByTransactionId(req.getTransactionID().longValue())
                .chain(existingTx -> {
                    if (existingTx == null) {
                        logger.error("❌ Transaction not found | transactionId={}", req.getTransactionID());
                        return Uni.createFrom()
                                .item(new ApiResponse<>("error", "Transaction not found", (TransactionResponse) null));
                    }

                    if (PaymentStatus.SUCCESS.equals(existingTx.getStatus())
                            || PaymentStatus.REFUNDED.equals(existingTx.getStatus())) {
                        logger.error("❌ Transaction cannot be modified | transactionId={}", req.getTransactionID());
                        return Uni.createFrom().item(new ApiResponse<>("error", "Transaction cannot be modified",
                                (TransactionResponse) null));
                    }

                    return merchantQueryService.findByIdMerchant(pb.merchant.Merchant.FindByIdMerchantRequest.newBuilder()
                                    .setMerchantId(req.getMerchantID())
                                    .build())
                            .onItem().transformToUni(merchantResp -> {
                                if (merchantResp == null || !merchantResp.hasData() || !"success".equalsIgnoreCase(merchantResp.getStatus())) {
                                    logger.error("❌ Merchant not found | merchantId={}", req.getMerchantID());
                                    return Uni.createFrom().item(new ApiResponse<>("error", "Merchant not found", (TransactionResponse) null));
                                }

                                return orderQueryService.findById(pb.order.Order.FindByIdOrderRequest.newBuilder()
                                                .setId(req.getOrderID())
                                                .build())
                                        .onItem().transformToUni(orderResp -> {
                                            if (orderResp == null || !orderResp.hasData() || !"success".equalsIgnoreCase(orderResp.getStatus())) {
                                                logger.error("❌ Order not found | orderId={}", req.getOrderID());
                                                return Uni.createFrom().item(new ApiResponse<>("error", "Order not found", (TransactionResponse) null));
                                            }

                                            return orderItemQueryService.findOrderItemByOrder(pb.order_item.OrderItem.FindByIdOrderItemRequest.newBuilder()
                                                            .setOrderItemId(req.getOrderID())
                                                            .build())
                                                    .onItem().transformToUni(orderItemsResp -> {
                                                        if (orderItemsResp == null || orderItemsResp.getDataCount() == 0 || !"success".equalsIgnoreCase(orderItemsResp.getStatus())) {
                                                            logger.error("❌ No order items found | orderId={}", req.getOrderID());
                                                            return Uni.createFrom().item(new ApiResponse<>("error", "No order items found", (TransactionResponse) null));
                                                        }

                                                        int totalAmount = 0;
                                                        for (var item : orderItemsResp.getDataList()) {
                                                            if (item.getQuantity() <= 0) {
                                                                 return Uni.createFrom().item(new ApiResponse<>("error", "Invalid order item quantity", (TransactionResponse) null));
                                                            }
                                                            totalAmount += item.getPrice() * item.getQuantity();
                                                        }
                                                        int ppn = totalAmount * 11 / 100;
                                                        int totalAmountWithTax = totalAmount + ppn;

                                                        String paymentStatus = req.getAmount() >= totalAmountWithTax
                                                                ? "success"
                                                                : "failed";
                                                        if ("failed".equals(paymentStatus)) {
                                                            logger.error(
                                                                    "❌ Insufficient payment amount | amount={}, required={}",
                                                                    req.getAmount(), totalAmountWithTax);
                                                            return Uni.createFrom()
                                                                    .item(new ApiResponse<>("error",
                                                                            "Insufficient payment amount",
                                                                            (TransactionResponse) null));
                                                        }

                                                        req.setAmount(totalAmountWithTax);
                                                        req.setPaymentStatus(paymentStatus);

                                                        existingTx.setOrderId(req.getOrderID().longValue());
                                                        existingTx.setMerchantId(req.getMerchantID().longValue());
                                                        existingTx.setPaymentMethod(req.getPaymentMethod());
                                                        existingTx.setAmount(req.getAmount());
                                                        existingTx.setStatus(
                                                                PaymentStatus.fromValue(req.getPaymentStatus()));
                                                        existingTx.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                                                        return transactionCommandRepository.persist(existingTx)
                                                                .chain(savedTx -> {
                                                                    return clearCache(savedTx.getTransactionId(),
                                                                            savedTx.getOrderId())
                                                                            .map(v -> {
                                                                                span.setStatus(StatusCode.OK);
                                                                                requestsTotal.add(1, Attributes.of(
                                                                                        AttributeKey
                                                                                                .stringKey("operation"),
                                                                                        "update_transaction",
                                                                                        AttributeKey.stringKey(
                                                                                                "status"),
                                                                                        "success"));
                                                                                logger.info(
                                                                                        "✅ Transaction updated successfully | transactionId={}",
                                                                                        savedTx.getTransactionId());
                                                                                return ApiResponse.success(
                                                                                        "Transaction updated successfully",
                                                                                        TransactionResponse
                                                                                                .from(savedTx));
                                                                            });
                                                                });
                                                    });
                                        });
                            });
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to update transaction: {}", e.getMessage(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_transaction",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));

                    return new ApiResponse<>("error", "Failed to update transaction: " + e.getMessage(),
                            (TransactionResponse) null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration,
                            Attributes.of(AttributeKey.stringKey("operation"), "update_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<TransactionResponseDeleteAt>> trash(Integer id) {
        logger.info("🗑️ Trashing transaction id={}", id);
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "trash_transaction")
                .setAttribute("transactionId", id)
                .startSpan();

        return transactionCommandRepository.trashed(id.longValue())
                .chain(transaction -> {
                    if (transaction == null) {
                        return Uni.createFrom().item(new ApiResponse<>("error", "Transaction not found",
                                (TransactionResponseDeleteAt) null));
                    }
                    return clearCache(transaction.getTransactionId(), transaction.getOrderId())
                            .map(v -> {
                                span.setStatus(StatusCode.OK);
                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_transaction",
                                        AttributeKey.stringKey("status"), "success"));
                                return ApiResponse.success("Transaction trashed successfully",
                                        TransactionResponseDeleteAt.from(transaction));
                            });
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to trash transaction id={}: {}", id, e.getMessage(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    return new ApiResponse<>("error", "Failed to trash transaction: " + e.getMessage(),
                            (TransactionResponseDeleteAt) null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration,
                            Attributes.of(AttributeKey.stringKey("operation"), "trash_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<TransactionResponseDeleteAt>> restore(Integer id) {
        logger.info("♻️ Restoring transaction id={}", id);
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "restore_transaction")
                .setAttribute("transactionId", id)
                .startSpan();

        return transactionCommandRepository.restore(id.longValue())
                .chain(transaction -> {
                    if (transaction == null) {
                        return Uni.createFrom().item(new ApiResponse<>("error", "Transaction not found",
                                (TransactionResponseDeleteAt) null));
                    }
                    return clearCache(transaction.getTransactionId(), transaction.getOrderId())
                            .map(v -> {
                                span.setStatus(StatusCode.OK);
                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_transaction",
                                        AttributeKey.stringKey("status"), "success"));
                                return ApiResponse.success("Transaction restored successfully",
                                        TransactionResponseDeleteAt.from(transaction));
                            });
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to restore transaction id={}: {}", id, e.getMessage(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    return new ApiResponse<>("error", "Failed to restore transaction: " + e.getMessage(),
                            (TransactionResponseDeleteAt) null);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration,
                            Attributes.of(AttributeKey.stringKey("operation"), "restore_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> delete(Integer id) {
        logger.info("🧨 Permanently deleting transaction id={}", id);
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteTransaction")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "delete_transaction")
                .setAttribute("transactionId", id)
                .startSpan();

        return transactionCommandRepository.deletePermanent(id.longValue())
                .chain(transaction -> {
                    if (transaction == null) {
                        return Uni.createFrom()
                                .item(new ApiResponse<>("error", "Transaction not found or not trashed", false));
                    }
                    return clearCache(transaction.getTransactionId(), transaction.getOrderId())
                            .map(v -> {
                                span.setStatus(StatusCode.OK);
                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_transaction",
                                        AttributeKey.stringKey("status"), "success"));
                                return ApiResponse.success("Transaction permanently deleted", true);
                            });
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to delete transaction id={}: {}", id, e.getMessage(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    return new ApiResponse<>("error", "Failed to delete transaction: " + e.getMessage(), false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration,
                            Attributes.of(AttributeKey.stringKey("operation"), "delete_transaction"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> restoreAll() {
        logger.info("🔄 Restoring ALL trashed transactions");
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllTransactions")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "restore_all_transactions")
                .startSpan();

        return transactionCommandRepository.restoreAllDeleted()
                .map(success -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_transactions",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("All transactions restored successfully", success);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to restore all transactions: {}", e.getMessage(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    return new ApiResponse<>("error", "Failed to restore all transactions: " + e.getMessage(), false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration,
                            Attributes.of(AttributeKey.stringKey("operation"), "restore_all_transactions"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAll() {
        logger.info("💣 Permanently deleting ALL trashed transactions");
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllTransactions")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "transaction-service")
                .setAttribute("operation", "delete_all_transactions")
                .startSpan();

        return transactionCommandRepository.deleteAllDeleted()
                .map(success -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_transactions",
                            AttributeKey.stringKey("status"), "success"));
                    return ApiResponse.success("All trashed transactions permanently deleted", success);
                })
                .onFailure().recoverWithItem(e -> {
                    logger.error("💥 Failed to delete all transactions: {}", e.getMessage(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                    return new ApiResponse<>("error", "Failed to delete all transactions: " + e.getMessage(), false);
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration,
                            Attributes.of(AttributeKey.stringKey("operation"), "delete_all_transactions"));
                });
    }
}
