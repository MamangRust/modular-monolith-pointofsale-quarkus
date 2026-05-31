package com.sanedge.merchant.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.config.RedisService;
import com.sanedge.merchant.domain.requests.CreateMerchantDocumentRequest;
import com.sanedge.merchant.domain.requests.UpdateMerchantDocumentRequest;
import com.sanedge.merchant.domain.requests.UpdateMerchantDocumentStatus;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.merchant.domain.response.MerchantDocumentResponse;
import com.sanedge.merchant.domain.response.MerchantDocumentResponseDeleteAt;
import com.sanedge.merchant.entity.MerchantDocument;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.merchant.repository.MerchantQueryRepository;
import com.sanedge.merchant.repository.MerchantDocumentCommandRepository;
import com.sanedge.merchant.repository.MerchantDocumentQueryRepository;
import com.sanedge.merchant.service.MerchantDocumentCommandService;

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
public class MerchantDocumentCommandServiceImpl implements MerchantDocumentCommandService {
    private static final Logger logger = LoggerFactory.getLogger(MerchantDocumentCommandServiceImpl.class);

    private final MerchantQueryRepository merchantQueryRepository;
    private final MerchantDocumentQueryRepository merchantDocumentQueryRepository;
    private final MerchantDocumentCommandRepository merchantDocumentCommandRepository;
    private final RedisService redisService;

    private final Tracer tracer;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestDurationSeconds;

    @Inject
    public MerchantDocumentCommandServiceImpl(
                    MerchantQueryRepository merchantQueryRepository,
                    MerchantDocumentQueryRepository merchantDocumentQueryRepository,
                    MerchantDocumentCommandRepository merchantDocumentCommandRepository,
                    OpenTelemetry openTelemetry,
                    RedisService redisService) {
        this.merchantQueryRepository = merchantQueryRepository;
        this.merchantDocumentQueryRepository = merchantDocumentQueryRepository;
        this.merchantDocumentCommandRepository = merchantDocumentCommandRepository;
        this.redisService = redisService;
        this.tracer = openTelemetry.getTracer("merchant-document-command-service", "1.0.0");
        Meter meter = openTelemetry.getMeter("merchant-document-command-service");

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
    public Uni<ApiResponse<MerchantDocumentResponse>> create(CreateMerchantDocumentRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("createMerchantDocument")
                        .setSpanKind(SpanKind.SERVER)
                        .setAttribute("service.name", "merchant-document-command-service")
                        .setAttribute("operation", "create")
                        .setAttribute("merchant.id", req.getMerchantId())
                        .startSpan();

        logger.info("📄 Creating merchant document | MerchantId: {}, Type: {}", req.getMerchantId(), req.getDocumentType());

        return merchantQueryRepository.findMerchantById(req.getMerchantId())
                .chain(merchant -> {
                    if (merchant == null) {
                        logger.error("ResourceNotFound: Merchant not found with id {}", req.getMerchantId());
                        span.setStatus(StatusCode.ERROR, "Merchant not found");
                        throw new ResourceNotFoundException("Merchant not found");
                    }

                    MerchantDocument doc = new MerchantDocument();
                    doc.setMerchantId(req.getMerchantId().intValue());
                    doc.setDocumentType(req.getDocumentType());
                    doc.setDocumentUrl(req.getDocumentUrl());
                    doc.setStatus("PENDING");

                    return merchantDocumentCommandRepository.persist(doc)
                            .chain(savedDoc -> {
                                logger.info("✅ Merchant document created successfully | Id: {}", savedDoc.getDocumentId());
                                span.setStatus(StatusCode.OK);

                                requestsTotal.add(1, Attributes.of(
                                                AttributeKey.stringKey("operation"), "create",
                                                AttributeKey.stringKey("status"), "success"));

                                return Uni.createFrom().item(ApiResponse.success("Merchant document created successfully", MerchantDocumentResponse.from(savedDoc)));
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to create merchant document", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                                    AttributeKey.stringKey("operation"), "create"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantDocumentResponse>> update(UpdateMerchantDocumentRequest req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateMerchantDocument")
                        .setSpanKind(SpanKind.SERVER)
                        .setAttribute("service.name", "merchant-document-command-service")
                        .setAttribute("operation", "update")
                        .setAttribute("doc.id", req.getDocumentId())
                        .startSpan();

        logger.info("🛠️ Updating merchant document | Id: {}", req.getDocumentId());

        return merchantDocumentQueryRepository.findDocumentById(req.getDocumentId())
                .chain(doc -> {
                    if (doc == null) {
                        logger.error("❌ Merchant document not found with id {}", req.getDocumentId());
                        span.setStatus(StatusCode.ERROR, "Merchant document not found");
                        throw new ResourceNotFoundException("Merchant document not found");
                    }

                    return merchantQueryRepository.findMerchantById(req.getMerchantId())
                            .chain(merchant -> {
                                if (merchant == null) {
                                    logger.error("❌ Merchant not found with id {}", req.getMerchantId());
                                    span.setStatus(StatusCode.ERROR, "Merchant not found");
                                    throw new ResourceNotFoundException("Merchant not found");
                                }

                                doc.setMerchantId(req.getMerchantId().intValue());
                                doc.setDocumentType(req.getDocumentType());
                                doc.setDocumentUrl(req.getDocumentUrl());
                                doc.setNote(req.getNote());
                                doc.setStatus(req.getStatus());

                                return merchantDocumentCommandRepository.persist(doc)
                                        .chain(savedDoc -> {
                                            String cacheKey = "merchant_doc:id:" + req.getDocumentId();

                                            return redisService.deleteReactive(cacheKey)
                                                    .map(v -> {
                                                        logger.info("✅ Merchant document updated successfully | Id: {}", req.getDocumentId());
                                                        span.setStatus(StatusCode.OK);
                                                        return ApiResponse.success("Merchant document updated successfully", MerchantDocumentResponse.from(savedDoc));
                                                    });
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to update merchant document", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                                    AttributeKey.stringKey("operation"), "update"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantDocumentResponse>> updateStatus(UpdateMerchantDocumentStatus req) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("updateMerchantDocumentStatus")
                        .setSpanKind(SpanKind.SERVER)
                        .setAttribute("service.name", "merchant-document-command-service")
                        .setAttribute("operation", "update_status")
                        .setAttribute("doc.id", req.getDocumentId())
                        .startSpan();

        logger.info("🛠️ Updating merchant document status | Id: {}", req.getDocumentId());

        return merchantDocumentQueryRepository.findDocumentById(req.getDocumentId())
                .chain(doc -> {
                    if (doc == null) {
                        logger.error("❌ Merchant document not found with id {}", req.getDocumentId());
                        span.setStatus(StatusCode.ERROR, "Merchant document not found");
                        throw new ResourceNotFoundException("Merchant document not found");
                    }

                    return merchantQueryRepository.findMerchantById(req.getMerchantId())
                            .chain(merchant -> {
                                if (merchant == null) {
                                    logger.error("❌ Merchant not found with id {}", req.getMerchantId());
                                    span.setStatus(StatusCode.ERROR, "Merchant not found");
                                    throw new ResourceNotFoundException("Merchant not found");
                                }

                                doc.setStatus(req.getStatus());
                                doc.setNote(req.getNote());

                                return merchantDocumentCommandRepository.persist(doc)
                                        .chain(savedDoc -> {
                                            String cacheKey = "merchant_doc:id:" + req.getDocumentId();

                                            return redisService.deleteReactive(cacheKey)
                                                    .map(v -> {
                                                        logger.info("✅ Merchant document status updated successfully | Id: {}", req.getDocumentId());
                                                        span.setStatus(StatusCode.OK);
                                                        return ApiResponse.success("Merchant document status updated successfully", MerchantDocumentResponse.from(savedDoc));
                                                    });
                                        });
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to update merchant document status", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                                    AttributeKey.stringKey("operation"), "update_status"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantDocumentResponseDeleteAt>> trash(Long id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("trashMerchantDocument")
                        .setSpanKind(SpanKind.SERVER)
                        .setAttribute("service.name", "merchant-document-command-service")
                        .setAttribute("operation", "trash")
                        .setAttribute("doc.id", id.toString())
                        .startSpan();

        logger.info("🗑️ Trashing merchant document | Id: {}", id);

        return merchantDocumentCommandRepository.trashed(id)
                .chain(doc -> {
                    if (doc == null) {
                        logger.error("❌ Merchant document not found with id {}", id);
                        span.setStatus(StatusCode.ERROR, "Merchant document not found");
                        throw new ResourceNotFoundException("Merchant document not found");
                    }

                    String cacheKey = "merchant_doc:id:" + id;

                    return redisService.deleteReactive(cacheKey)
                            .map(v -> {
                                logger.info("✅ Merchant document trashed successfully | Id: {}", id);
                                span.setStatus(StatusCode.OK);
                                return ApiResponse.success("Merchant document trashed successfully", MerchantDocumentResponseDeleteAt.from(doc));
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to trash merchant document", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                                    AttributeKey.stringKey("operation"), "trash"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<MerchantDocumentResponseDeleteAt>> restore(Long id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreMerchantDocument")
                        .setSpanKind(SpanKind.SERVER)
                        .setAttribute("service.name", "merchant-document-command-service")
                        .setAttribute("operation", "restore")
                        .setAttribute("doc.id", id.toString())
                        .startSpan();

        logger.info("♻️ Restoring merchant document | Id: {}", id);

        return merchantDocumentCommandRepository.restore(id)
                .chain(doc -> {
                    if (doc == null) {
                        logger.error("❌ Merchant document not found with id {}", id);
                        span.setStatus(StatusCode.ERROR, "Merchant document not found");
                        throw new ResourceNotFoundException("Merchant document not found");
                    }

                    String cacheKey = "merchant_doc:id:" + id;

                    return redisService.deleteReactive(cacheKey)
                            .map(v -> {
                                logger.info("✅ Merchant document restored successfully | Id: {}", id);
                                span.setStatus(StatusCode.OK);
                                return ApiResponse.success("Merchant document restored successfully", MerchantDocumentResponseDeleteAt.from(doc));
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore merchant document", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                                    AttributeKey.stringKey("operation"), "restore"));
                });
    }

    @Override
    @WithTransaction
    public Uni<ApiResponse<Boolean>> deletePermanent(Long id) {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deletePermanentMerchantDocument")
                        .setSpanKind(SpanKind.SERVER)
                        .setAttribute("service.name", "merchant-document-command-service")
                        .setAttribute("operation", "delete_permanent")
                        .setAttribute("doc.id", id.toString())
                        .startSpan();

        logger.info("🗑️ Permanently deleting merchant document | Id: {}", id);

        return merchantDocumentCommandRepository.deletePermanent(id)
                .chain(success -> {
                    if (!success) {
                        logger.error("❌ Merchant document not found with id {}", id);
                        span.setStatus(StatusCode.ERROR, "Merchant document not found");
                        throw new ResourceNotFoundException("Merchant document not found");
                    }

                    String cacheKey = "merchant_doc:id:" + id;

                    return redisService.deleteReactive(cacheKey)
                            .map(v -> {
                                logger.info("✅ Merchant document permanently deleted | Id: {}", id);
                                span.setStatus(StatusCode.OK);
                                return ApiResponse.success("Merchant document permanently deleted", true);
                            });
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to permanently delete merchant document", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
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
    public Uni<ApiResponse<Boolean>> restoreAll() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("restoreAllMerchantDocuments")
                        .setSpanKind(SpanKind.SERVER)
                        .setAttribute("service.name", "merchant-document-command-service")
                        .setAttribute("operation", "restore_all")
                        .startSpan();

        logger.info("♻️ Restoring all merchant documents");

        return merchantDocumentCommandRepository.restoreAllDeleted()
                .map(success -> {
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("All merchant documents restored successfully", success);
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to restore all merchant documents", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
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
    public Uni<ApiResponse<Boolean>> deleteAllPermanent() {
        long startTime = System.currentTimeMillis();
        Span span = tracer.spanBuilder("deleteAllPermanentMerchantDocuments")
                        .setSpanKind(SpanKind.SERVER)
                        .setAttribute("service.name", "merchant-document-command-service")
                        .setAttribute("operation", "delete_all_permanent")
                        .startSpan();

        logger.info("🗑️ Permanently deleting all trashed merchant documents");

        return merchantDocumentCommandRepository.deleteAllDeleted()
                .map(success -> {
                    span.setStatus(StatusCode.OK);
                    return ApiResponse.success("All trashed merchant documents permanently deleted", success);
                })
                .onFailure().invoke(e -> {
                    logger.error("💥 Failed to permanently delete all merchant documents", e);
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR, e.getMessage());
                })
                .eventually(() -> {
                    span.end();
                    double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                    requestDurationSeconds.record(duration, Attributes.of(
                                    AttributeKey.stringKey("operation"), "delete_all_permanent"));
                });
    }
}
