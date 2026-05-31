package com.sanedge.merchant.service.impl;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.config.RedisService;
import com.sanedge.merchant.domain.requests.CreateMerchantRequest;
import com.sanedge.merchant.domain.requests.UpdateMerchantRequest;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.merchant.domain.response.MerchantResponse;
import com.sanedge.merchant.domain.response.MerchantResponseDeleteAt;
import com.sanedge.merchant.entity.Merchant;
import com.sanedge.common.enums.Status;
import com.sanedge.common.exception.ResourceAlreadyExistsException;
import com.sanedge.common.exception.ResourceNotFoundException;
import io.quarkus.grpc.GrpcClient;
import com.sanedge.merchant.repository.MerchantCommandRepository;
import com.sanedge.merchant.repository.MerchantQueryRepository;
import com.sanedge.merchant.service.MerchantCommandService;
import com.sanedge.common.utils.ApiKeyGenerator;

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
public class MerchantCommandServiceImpl implements MerchantCommandService {
        private static final Logger logger = LoggerFactory.getLogger(MerchantCommandServiceImpl.class);

        @GrpcClient("user")
        pb.user.MutinyUserQueryServiceGrpc.MutinyUserQueryServiceStub userQueryService;

        private final MerchantQueryRepository merchantQueryRepository;
        private final MerchantCommandRepository merchantCommandRepository;
        private final RedisService redisService;

        private final Tracer tracer;
        private final LongCounter requestsTotal;
        private final DoubleHistogram requestDurationSeconds;

        @Inject
        public MerchantCommandServiceImpl(
                        MerchantQueryRepository merchantQueryRepository,
                        MerchantCommandRepository merchantCommandRepository,
                        OpenTelemetry openTelemetry,
                        RedisService redisService) {
                this.merchantQueryRepository = merchantQueryRepository;
                this.merchantCommandRepository = merchantCommandRepository;
                this.redisService = redisService;
                this.tracer = openTelemetry.getTracer("merchant-command-service", "1.0.0");
                Meter meter = openTelemetry.getMeter("merchant-command-service");

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
        public Uni<ApiResponse<MerchantResponse>> createMerchant(CreateMerchantRequest req) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("createMerchant")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "merchant-command-service")
                                .setAttribute("operation", "create_merchant")
                                .setAttribute("merchant.name", req.getName())
                                .startSpan();

                logger.info("🏪 Creating merchant | Name: {}, UserId: {}", req.getName(), req.getUserId());

                return userQueryService.findById(pb.user.User.FindByIdUserRequest.newBuilder().setId(req.getUserId().intValue()).build())
                                .chain(response -> {
                                        if (response == null || !response.hasData()) {
                                                logger.error("❌ User not found with id {}", req.getUserId());
                                                span.setStatus(StatusCode.ERROR, "User not found");
                                                throw new ResourceNotFoundException("User not found");
                                        }
                                        return merchantQueryRepository.existsByName(req.getName());
                                })
                                .chain(nameExists -> {
                                        if (nameExists) {
                                                logger.error("❌ Merchant name already taken | Name: {}", req.getName());
                                                span.setStatus(StatusCode.ERROR, "Merchant name already taken");
                                                throw new ResourceAlreadyExistsException("Merchant name already taken");
                                        }

                                        String apiKey = ApiKeyGenerator.generateApiKey();
                                        UUID merchantNo = UUID.randomUUID();

                                        Merchant merchant = new Merchant();
                                        merchant.setName(req.getName());
                                        merchant.setMerchantNo(merchantNo);
                                        merchant.setUserId(req.getUserId().intValue());
                                        merchant.setApiKey(apiKey);
                                        merchant.setStatus(Status.PENDING);

                                        return merchantCommandRepository.persist(merchant)
                                                        .chain(savedMerchant -> {
                                                                logger.info("✅ Merchant created successfully | Id: {}, ApiKey: {}", merchant.getMerchantId(), apiKey);
                                                                span.setStatus(StatusCode.OK);

                                                                requestsTotal.add(1, Attributes.of(
                                                                                AttributeKey.stringKey("operation"), "create_merchant",
                                                                                AttributeKey.stringKey("status"), "success"));

                                                                return Uni.createFrom().item(ApiResponse.success("Merchant created successfully", MerchantResponse.from(merchant)));
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("💥 Failed to create merchant | Name: {}, UserId: {}", req.getName(), req.getUserId(), e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "create_merchant",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "create_merchant"));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<MerchantResponse>> updateMerchant(UpdateMerchantRequest req) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("updateMerchant")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "merchant-command-service")
                                .setAttribute("operation", "update_merchant")
                                .setAttribute("merchant.id", req.getMerchantId().toString())
                                .startSpan();

                logger.info("🛠️ Updating merchant | Id: {}", req.getMerchantId());

                return merchantQueryRepository.findMerchantById(req.getMerchantId())
                                .chain(merchant -> {
                                        if (merchant == null) {
                                                logger.error("❌ Merchant not found with id {}", req.getMerchantId());
                                                span.setStatus(StatusCode.ERROR, "Merchant not found");
                                                throw new ResourceNotFoundException("Merchant not found");
                                        }

                                        Uni<Void> userCheckUni = Uni.createFrom().nullItem();
                                        if (req.getUserId() != null) {
                                                userCheckUni = userQueryService.findById(pb.user.User.FindByIdUserRequest.newBuilder().setId(req.getUserId().intValue()).build())
                                                                .chain(response -> {
                                                                        if (response == null || !response.hasData()) {
                                                                                logger.error("❌ User not found with id {}", req.getUserId());
                                                                                span.setStatus(StatusCode.ERROR, "User not found");
                                                                                throw new ResourceNotFoundException("User not found");
                                                                        }
                                                                        merchant.setUserId(req.getUserId().intValue());
                                                                        return Uni.createFrom().nullItem();
                                                                });
                                        }

                                        return userCheckUni.chain(v -> {
                                                merchant.setName(req.getName());
                                                merchant.setStatus(Status.valueOf(req.getStatus().toUpperCase()));

                                                return merchantCommandRepository.persist(merchant)
                                                                .chain(savedMerchant -> {
                                                                        String cacheIdKey = "merchant:id:" + req.getMerchantId();
                                                                        String cacheApiKey = "merchant:apikey:" + merchant.getApiKey();
                                                                        String cacheUserKey = "merchant:user:" + merchant.getUserId();

                                                                        return Uni.combine().all().unis(
                                                                                redisService.deleteReactive(cacheIdKey),
                                                                                redisService.deleteReactive(cacheApiKey),
                                                                                redisService.deleteReactive(cacheUserKey)
                                                                        ).asTuple().map(v2 -> {
                                                                                logger.info("✅ Merchant updated successfully | Id: {}", req.getMerchantId());
                                                                                span.setStatus(StatusCode.OK);

                                                                                requestsTotal.add(1, Attributes.of(
                                                                                                AttributeKey.stringKey("operation"), "update_merchant",
                                                                                                AttributeKey.stringKey("status"), "success"));

                                                                                return ApiResponse.success("Merchant updated successfully", MerchantResponse.from(merchant));
                                                                        });
                                                                });
                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("💥 Failed to update merchant | Id: {}", req.getMerchantId(), e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "update_merchant",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "update_merchant"));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<MerchantResponseDeleteAt>> trashMerchant(Long id) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("trashMerchant")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "merchant-command-service")
                                .setAttribute("operation", "trash_merchant")
                                .setAttribute("merchant.id", id.toString())
                                .startSpan();

                logger.info("🗑️ Trashing merchant id={}", id);

                return merchantCommandRepository.trashed(id)
                                .chain(merchant -> {
                                        if (merchant == null) {
                                                logger.error("❌ Merchant not found with id {}", id);
                                                span.setStatus(StatusCode.ERROR, "Merchant not found");
                                                throw new ResourceNotFoundException("Merchant not found");
                                        }

                                        String cacheIdKey = "merchant:id:" + id;
                                        String cacheApiKey = "merchant:apikey:" + merchant.getApiKey();
                                        String cacheUserKey = "merchant:user:" + merchant.getUserId();

                                        return Uni.combine().all().unis(
                                                redisService.deleteReactive(cacheIdKey),
                                                redisService.deleteReactive(cacheApiKey),
                                                redisService.deleteReactive(cacheUserKey)
                                        ).asTuple().map(v2 -> {
                                                logger.info("✅ Merchant trashed successfully | Id: {}", id);
                                                span.setStatus(StatusCode.OK);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "trash_merchant",
                                                                AttributeKey.stringKey("status"), "success"));

                                                return ApiResponse.success("Merchant trashed successfully", MerchantResponseDeleteAt.from(merchant));
                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("💥 Failed to trash merchant id={}", id, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "trash_merchant",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "trash_merchant"));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<MerchantResponseDeleteAt>> restoreMerchant(Long id) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("restoreMerchant")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "merchant-command-service")
                                .setAttribute("operation", "restore_merchant")
                                .setAttribute("merchant.id", id.toString())
                                .startSpan();

                logger.info("♻️ Restoring merchant id={}", id);

                return merchantCommandRepository.restore(id)
                                .chain(merchant -> {
                                        if (merchant == null) {
                                                logger.error("❌ Merchant not found with id {}", id);
                                                span.setStatus(StatusCode.ERROR, "Merchant not found");
                                                throw new ResourceNotFoundException("Merchant not found");
                                        }

                                        String cacheIdKey = "merchant:id:" + id;
                                        String cacheApiKey = "merchant:apikey:" + merchant.getApiKey();
                                        String cacheUserKey = "merchant:user:" + merchant.getUserId();

                                        return Uni.combine().all().unis(
                                                redisService.deleteReactive(cacheIdKey),
                                                redisService.deleteReactive(cacheApiKey),
                                                redisService.deleteReactive(cacheUserKey)
                                        ).asTuple().map(v2 -> {
                                                logger.info("✅ Merchant restored successfully | Id: {}", id);
                                                span.setStatus(StatusCode.OK);

                                                requestsTotal.add(1, Attributes.of(
                                                                AttributeKey.stringKey("operation"), "restore_merchant",
                                                                AttributeKey.stringKey("status"), "success"));

                                                return ApiResponse.success("Merchant restored successfully", MerchantResponseDeleteAt.from(merchant));
                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("💥 Failed to restore merchant id={}", id, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_merchant",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_merchant"));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Boolean>> deleteMerchant(Long id) {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("deleteMerchant")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "merchant-command-service")
                                .setAttribute("operation", "delete_merchant")
                                .setAttribute("merchant.id", id.toString())
                                .startSpan();

                logger.info("🧨 Permanently deleting merchant id={}", id);

                return merchantQueryRepository.findMerchantById(id)
                                .chain(merchant -> {
                                        if (merchant == null) {
                                                logger.error("❌ Merchant not found with id {}", id);
                                                span.setStatus(StatusCode.ERROR, "Merchant not found");
                                                throw new ResourceNotFoundException("Merchant not found");
                                        }

                                        String cacheIdKey = "merchant:id:" + id;
                                        String cacheApiKey = "merchant:apikey:" + merchant.getApiKey();
                                        String cacheUserKey = "merchant:user:" + merchant.getUserId();

                                        return merchantCommandRepository.deletePermanent(id)
                                                        .chain(deleted -> {
                                                                return Uni.combine().all().unis(
                                                                        redisService.deleteReactive(cacheIdKey),
                                                                        redisService.deleteReactive(cacheApiKey),
                                                                        redisService.deleteReactive(cacheUserKey)
                                                                ).asTuple().map(v2 -> {
                                                                        logger.info("✅ Merchant permanently deleted | Id: {}", id);
                                                                        span.setStatus(StatusCode.OK);

                                                                        requestsTotal.add(1, Attributes.of(
                                                                                        AttributeKey.stringKey("operation"), "delete_merchant",
                                                                                        AttributeKey.stringKey("status"), "success"));

                                                                        return ApiResponse.success("Merchant permanently deleted", true);
                                                                });
                                                        });
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("💥 Failed to permanently delete merchant id={}", id, e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "delete_merchant",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "delete_merchant"));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Boolean>> restoreAll() {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("restoreAllMerchants")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "merchant-command-service")
                                .setAttribute("operation", "restore_all_merchants")
                                .startSpan();

                logger.info("🔄 Restoring ALL trashed merchants");

                return merchantCommandRepository.restoreAllDeleted()
                                .map(restored -> {
                                        logger.info("✅ Restored all trashed merchants");
                                        span.setStatus(StatusCode.OK);

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_all_merchants",
                                                        AttributeKey.stringKey("status"), "success"));

                                        return ApiResponse.success("Restored all trashed merchants", restored);
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("💥 Failed to restore all merchants", e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_all_merchants",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "restore_all_merchants"));
                                });
        }

        @Override
        @WithTransaction
        public Uni<ApiResponse<Boolean>> deleteAll() {
                long startTime = System.currentTimeMillis();
                Span span = tracer.spanBuilder("deleteAllMerchants")
                                .setSpanKind(SpanKind.SERVER)
                                .setAttribute("service.name", "merchant-command-service")
                                .setAttribute("operation", "delete_all_merchants")
                                .startSpan();

                logger.info("💣 Permanently deleting ALL trashed merchants");

                return merchantCommandRepository.deleteAllDeleted()
                                .map(deleted -> {
                                        logger.info("✅ Deleted all trashed merchants");
                                        span.setStatus(StatusCode.OK);

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "delete_all_merchants",
                                                        AttributeKey.stringKey("status"), "success"));

                                        return ApiResponse.success("Deleted all trashed merchants", deleted);
                                })
                                .onFailure().invoke(e -> {
                                        logger.error("💥 Failed to delete all merchants", e);
                                        span.recordException(e);
                                        span.setStatus(StatusCode.ERROR, e.getMessage());

                                        requestsTotal.add(1, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "delete_all_merchants",
                                                        AttributeKey.stringKey("status"), "failed",
                                                        AttributeKey.stringKey("error_type"), e.getClass().getSimpleName()));
                                })
                                .eventually(() -> {
                                        span.end();
                                        double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                                        requestDurationSeconds.record(duration, Attributes.of(
                                                        AttributeKey.stringKey("operation"), "delete_all_merchants"));
                                });
        }
}
