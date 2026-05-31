package com.sanedge.cashier.service.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.config.RedisService;
import com.sanedge.cashier.domain.requests.CreateCashierRequest;
import com.sanedge.cashier.domain.requests.UpdateCashierRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.cashier.domain.response.CashierResponse;
import com.sanedge.cashier.domain.response.CashierResponseDeleteAt;
import com.sanedge.cashier.entity.Cashier;
import com.sanedge.common.exception.ResourceAlreadyExistsException;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.cashier.repository.CashierCommandRepository;
import com.sanedge.cashier.repository.CashierQueryRepository;
import com.sanedge.cashier.service.CashierCommandService;

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
import jakarta.validation.Valid;

@ApplicationScoped
public class CashierCommandServiceImpl implements CashierCommandService {
    private static final Logger logger = LoggerFactory.getLogger(CashierCommandServiceImpl.class);

    CashierCommandRepository cashierCommandRepository;
    CashierQueryRepository cashierQueryRepository;
    OpenTelemetry openTelemetry;
    RedisService redisService;

    @GrpcClient("merchant")
    pb.merchant.MutinyMerchantQueryServiceGrpc.MutinyMerchantQueryServiceStub merchantQueryService;

    @GrpcClient("user")
    pb.user.MutinyUserQueryServiceGrpc.MutinyUserQueryServiceStub userQueryService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public CashierCommandServiceImpl(CashierCommandRepository cashierCommandRepository,
                                     CashierQueryRepository cashierQueryRepository,
                                     OpenTelemetry openTelemetry,
                                     RedisService redisService) {
        this.cashierCommandRepository = cashierCommandRepository;
        this.cashierQueryRepository = cashierQueryRepository;
        this.openTelemetry = openTelemetry;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("cashier-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("cashier-command-service");

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
    public Uni<ApiResponse<CashierResponse>> createCashier(CreateCashierRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createCashier")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-service")
                .setAttribute("operation", "create_cashier")
                .setAttribute("cashier.name", req.getName())
                .startSpan();

        logger.info("🆕 Creating cashier for merchantId={}, userId={}, name={}", req.getMerchantId(), req.getUserId(), req.getName());

        return merchantQueryService.findByIdMerchant(pb.merchant.Merchant.FindByIdMerchantRequest.newBuilder()
                .setMerchantId(req.getMerchantId())
                .build())
                .chain(matchedResponse -> {
                    if ("success".equals(matchedResponse.getStatus()) && matchedResponse.hasData()) {
                        return Uni.createFrom().item(matchedResponse.getData());
                    } else {
                        return Uni.createFrom().failure(new ResourceNotFoundException("Merchant not found"));
                    }
                })
                .chain(merchant -> userQueryService.findById(pb.user.User.FindByIdUserRequest.newBuilder()
                        .setId(req.getUserId().intValue())
                        .build()))
                .chain(matchedUserResponse -> {
                    if ("success".equals(matchedUserResponse.getStatus()) && matchedUserResponse.hasData()) {
                        return Uni.createFrom().item(matchedUserResponse.getData());
                    } else {
                        return Uni.createFrom().failure(new ResourceNotFoundException("User not found"));
                    }
                })
                .chain(user -> cashierQueryRepository.findByNameAndMerchantId(req.getName(), req.getMerchantId().longValue()))
                .chain(existingCashier -> {
                    if (existingCashier != null) {
                        throw new ResourceAlreadyExistsException(
                                "Cashier with name '" + req.getName() + "' already exists for this merchant");
                    }

                    Cashier cashier = new Cashier();
                    cashier.setMerchantId(req.getMerchantId().longValue());
                    cashier.setUserId(req.getUserId().longValue());
                    cashier.setName(req.getName());
                    cashier.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
                    cashier.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                    return cashierCommandRepository.persist(cashier)
                            .map(v -> {
                                span.setAttribute("cashier.id", cashier.getCashierId());
                                span.setStatus(StatusCode.OK);

                                CashierResponse cashierResponse = CashierResponse.from(cashier);
                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "create_cashier",
                                        AttributeKey.stringKey("status"), "success"));

                                logger.info("✅ Cashier created successfully with id={}", cashier.getCashierId());
                                return ApiResponse.success("✅ Cashier created successfully!", cashierResponse);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to create cashier", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_cashier",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "create_cashier"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<CashierResponse>> updateCashier(@Valid UpdateCashierRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateCashier")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-service")
                .setAttribute("operation", "update_cashier")
                .setAttribute("cashier.id", req.getCashierId().toString())
                .startSpan();

        logger.info("🔄 Updating cashier id={}", req.getCashierId());

        return cashierCommandRepository.findById(req.getCashierId().longValue())
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException("Cashier not found"))
                .chain(cashier -> {
                    cashier.setName(req.getName());
                    cashier.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));

                    return cashierCommandRepository.persist(cashier)
                            .chain(v -> {
                                String cacheKey = "cashier:" + req.getCashierId();
                                return redisService.deleteReactive(cacheKey)
                                        .map(deleted -> {
                                            span.setStatus(StatusCode.OK);
                                            CashierResponse cashierResponse = CashierResponse.from(cashier);
                                            requestsTotal.add(1, Attributes.of(
                                                    AttributeKey.stringKey("operation"), "update_cashier",
                                                    AttributeKey.stringKey("status"), "success"));

                                            logger.info("✅ Cashier updated successfully id={}", cashier.getCashierId());
                                            return ApiResponse.success("✅ Cashier updated successfully!", cashierResponse);
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to update cashier id={}", req.getCashierId(), e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_cashier",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "update_cashier"));
                });
    }

    @WithTransaction
    public Uni<ApiResponse<CashierResponseDeleteAt>> trashed(Long cashierId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashCashier")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-service")
                .setAttribute("operation", "trash_cashier")
                .setAttribute("cashier.id", cashierId.toString())
                .startSpan();

        logger.info("🗑️ Trashing cashier id={}", cashierId);

        return cashierCommandRepository.trashed(cashierId)
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException("Cashier not found"))
                .chain(cashier -> {
                    String cacheKey = "cashier:" + cashierId;
                    return redisService.deleteReactive(cacheKey)
                            .map(deleted -> {
                                span.setStatus(StatusCode.OK);
                                cashierResponseDeleteAt = CashierResponseDeleteAt.from(cashier);
                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "trash_cashier",
                                        AttributeKey.stringKey("status"), "success"));

                                logger.info("🗑️ Cashier trashed successfully id={}", cashierId);
                                return ApiResponse.success("🗑️ Cashier trashed successfully!", cashierResponseDeleteAt);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to trash cashier id={}", cashierId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_cashier",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "trash_cashier"));
                });
    }

    @Override
    public Uni<ApiResponse<CashierResponseDeleteAt>> trashedCashier(Long cashierId) {
        return trashed(cashierId);
    }

    private CashierResponseDeleteAt cashierResponseDeleteAt;

    @WithTransaction
    public Uni<ApiResponse<CashierResponseDeleteAt>> restore(Long cashierId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreCashier")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-service")
                .setAttribute("operation", "restore_cashier")
                .setAttribute("cashier.id", cashierId.toString())
                .startSpan();

        logger.info("♻️ Restoring cashier id={}", cashierId);

        return cashierCommandRepository.restore(cashierId)
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException("Cashier not found or not trashed"))
                .chain(cashier -> {
                    String cacheKey = "cashier:" + cashierId;
                    return redisService.deleteReactive(cacheKey)
                            .map(deleted -> {
                                span.setStatus(StatusCode.OK);
                                CashierResponseDeleteAt cashierResponse = CashierResponseDeleteAt.from(cashier);
                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "restore_cashier",
                                        AttributeKey.stringKey("status"), "success"));

                                logger.info("♻️ Cashier restored successfully id={}", cashierId);
                                return ApiResponse.success("♻️ Cashier restored successfully!", cashierResponse);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore cashier id={}", cashierId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_cashier",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_cashier"));
                });
    }

    @Override
    public Uni<ApiResponse<CashierResponseDeleteAt>> restoreCashier(Long cashierId) {
        return restore(cashierId);
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteCashierPermanent(Long cashierId) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteCashierPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-service")
                .setAttribute("operation", "delete_cashier_permanent")
                .setAttribute("cashier.id", cashierId.toString())
                .startSpan();

        logger.info("🧨 Permanently deleting cashier id={}", cashierId);

        return cashierCommandRepository.deletePermanent(cashierId)
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException("Cashier not found or not trashed"))
                .chain(cashier -> {
                    String cacheKey = "cashier:" + cashierId;
                    return redisService.deleteReactive(cacheKey)
                            .map(deleted -> {
                                span.setStatus(StatusCode.OK);
                                requestsTotal.add(1, Attributes.of(
                                        AttributeKey.stringKey("operation"), "delete_cashier_permanent",
                                        AttributeKey.stringKey("status"), "success"));

                                logger.info("🧨 Cashier permanently deleted id={}", cashierId);
                                return ApiResponse.success("🧨 Cashier permanently deleted!", true);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to permanently delete cashier id={}", cashierId, e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_cashier_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
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
    public Uni<ApiResponse<Boolean>> restoreAllCashier() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllCashier")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-service")
                .setAttribute("operation", "restore_all_cashier")
                .startSpan();

        logger.info("🔄 Restoring ALL trashed cashiers");

        return cashierCommandRepository.restoreAllDeleted()
                .map(success -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_cashier",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("🔄 All cashiers restored successfully!");
                    return ApiResponse.success("🔄 All cashiers restored successfully!", true);
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore all cashiers", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_cashier",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "restore_all_cashier"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deleteAllCashierPermanent() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllCashierPermanent")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("service.name", "cashier-service")
                .setAttribute("operation", "delete_all_cashier_permanent")
                .startSpan();

        logger.info("💣 Permanently deleting ALL trashed cashiers");

        return cashierCommandRepository.deleteAllDeleted()
                .map(success -> {
                    span.setStatus(StatusCode.OK);
                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_cashier_permanent",
                            AttributeKey.stringKey("status"), "success"));

                    logger.info("💣 All cashiers permanently deleted!");
                    return ApiResponse.success("💣 All cashiers permanently deleted!", true);
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to delete all cashiers", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());

                    requestsTotal.add(1, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_cashier_permanent",
                            AttributeKey.stringKey("status"), "failed",
                            AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                            AttributeKey.stringKey("operation"), "delete_all_cashier_permanent"));
                });
    }
}
