package com.sanedge.gateway.service.impl;

import com.sanedge.gateway.dto.MerchantDocumentDto;
import com.sanedge.gateway.domain.requests.FindAllMerchantDocumentsRequest;
import com.sanedge.gateway.service.MerchantDocumentService;
import com.sanedge.gateway.telemetry.TelemetryHelper;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class MerchantDocumentServiceImpl implements MerchantDocumentService {

    private static final Logger LOG = Logger.getLogger(MerchantDocumentServiceImpl.class);

    @Inject
    TelemetryHelper telemetryHelper;

    @GrpcClient("merchant")
    pb.merchant_document.MutinyMerchantDocumentQueryServiceGrpc.MutinyMerchantDocumentQueryServiceStub merchantDocumentQueryService;

    @GrpcClient("merchant")
    pb.merchant_document.MutinyMerchantDocumentCommandServiceGrpc.MutinyMerchantDocumentCommandServiceStub merchantDocumentCommandService;

    @Override
    public Uni<MerchantDocumentDto.ApiResponsePaginationMerchantDocument> listMerchantDocuments(FindAllMerchantDocumentsRequest request) {
        return telemetryHelper.traceAndMetric("merchant_document.listMerchantDocuments", () -> 
            merchantDocumentQueryService.findAll(pb.merchant_document.MerchantDocumentOuterClass.FindAllMerchantDocumentsRequest.newBuilder()
                    .setPage(request.getPage())
                    .setPageSize(request.getSize())
                    .setSearch(request.getSearch() == null ? "" : request.getSearch())
                    .build())
                    .map(MerchantDocumentDto.ApiResponsePaginationMerchantDocument::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to list merchant documents: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDocumentDto.ApiResponsePaginationMerchantDocumentAt> listActiveMerchantDocuments(FindAllMerchantDocumentsRequest request) {
        return telemetryHelper.traceAndMetric("merchant_document.listActiveMerchantDocuments", () -> 
            merchantDocumentQueryService.findAllActive(pb.merchant_document.MerchantDocumentOuterClass.FindAllMerchantDocumentsRequest.newBuilder()
                    .setPage(request.getPage())
                    .setPageSize(request.getSize())
                    .setSearch(request.getSearch() == null ? "" : request.getSearch())
                    .build())
                    .map(MerchantDocumentDto.ApiResponsePaginationMerchantDocumentAt::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to list active merchant documents: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDocumentDto.ApiResponsePaginationMerchantDocumentAt> listTrashedMerchantDocuments(FindAllMerchantDocumentsRequest request) {
        return telemetryHelper.traceAndMetric("merchant_document.listTrashedMerchantDocuments", () -> 
            merchantDocumentQueryService.findAllTrashed(pb.merchant_document.MerchantDocumentOuterClass.FindAllMerchantDocumentsRequest.newBuilder()
                    .setPage(request.getPage())
                    .setPageSize(request.getSize())
                    .setSearch(request.getSearch() == null ? "" : request.getSearch())
                    .build())
                    .map(MerchantDocumentDto.ApiResponsePaginationMerchantDocumentAt::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to list trashed merchant documents: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDocumentDto.ApiResponseMerchantDocument> getMerchantDocument(int id) {
        return telemetryHelper.traceAndMetric("merchant_document.getMerchantDocument", () -> 
            merchantDocumentQueryService.findById(pb.merchant_document.MerchantDocumentOuterClass.FindMerchantDocumentByIdRequest.newBuilder()
                    .setDocumentId(id)
                    .build())
                    .map(MerchantDocumentDto.ApiResponseMerchantDocument::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to get merchant document " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDocumentDto.ApiResponseMerchantDocument> createMerchantDocument(MerchantDocumentDto.CreateRequest body) {
        return telemetryHelper.traceAndMetric("merchant_document.createMerchantDocument", () -> 
            merchantDocumentCommandService.create(pb.merchant_document.MerchantDocumentCommand.CreateMerchantDocumentRequest.newBuilder()
                    .setMerchantId(body.merchantId())
                    .setDocumentType(body.documentType() == null ? "" : body.documentType())
                    .setDocumentUrl(body.documentUrl() == null ? "" : body.documentUrl())
                    .build())
                    .map(MerchantDocumentDto.ApiResponseMerchantDocument::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to create merchant document: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDocumentDto.ApiResponseMerchantDocument> updateMerchantDocument(int id, MerchantDocumentDto.UpdateRequest body) {
        return telemetryHelper.traceAndMetric("merchant_document.updateMerchantDocument", () -> 
            merchantDocumentCommandService.update(pb.merchant_document.MerchantDocumentCommand.UpdateMerchantDocumentRequest.newBuilder()
                    .setDocumentId(id)
                    .setMerchantId(body.merchantId())
                    .setDocumentType(body.documentType() == null ? "" : body.documentType())
                    .setDocumentUrl(body.documentUrl() == null ? "" : body.documentUrl())
                    .setNote(body.note() == null ? "" : body.note())
                    .setStatus(body.status() == null ? "" : body.status())
                    .build())
                    .map(MerchantDocumentDto.ApiResponseMerchantDocument::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to update merchant document " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDocumentDto.ApiResponseMerchantDocument> updateMerchantDocumentStatus(int id, MerchantDocumentDto.UpdateStatusRequest body) {
        return telemetryHelper.traceAndMetric("merchant_document.updateMerchantDocumentStatus", () -> 
            merchantDocumentCommandService.updateStatus(pb.merchant_document.MerchantDocumentCommand.UpdateMerchantDocumentStatusRequest.newBuilder()
                    .setDocumentId(id)
                    .setMerchantId(body.merchantId())
                    .setNote(body.note() == null ? "" : body.note())
                    .setStatus(body.status() == null ? "" : body.status())
                    .build())
                    .map(MerchantDocumentDto.ApiResponseMerchantDocument::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to update merchant document status " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDocumentDto.ApiResponseMerchantDocumentDeleteAt> deleteMerchantDocument(int id) {
        return telemetryHelper.traceAndMetric("merchant_document.deleteMerchantDocument", () -> 
            merchantDocumentCommandService.trashed(pb.merchant_document.MerchantDocumentOuterClass.FindMerchantDocumentByIdRequest.newBuilder()
                    .setDocumentId(id)
                    .build())
                    .map(MerchantDocumentDto.ApiResponseMerchantDocumentDeleteAt::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to soft-delete merchant document " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDocumentDto.ApiResponseMerchantDocumentDeleteAt> restoreMerchantDocument(int id) {
        return telemetryHelper.traceAndMetric("merchant_document.restoreMerchantDocument", () -> 
            merchantDocumentCommandService.restore(pb.merchant_document.MerchantDocumentOuterClass.FindMerchantDocumentByIdRequest.newBuilder()
                    .setDocumentId(id)
                    .build())
                    .map(MerchantDocumentDto.ApiResponseMerchantDocumentDeleteAt::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to restore merchant document " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDocumentDto.SimpleResponse> deleteMerchantDocumentPermanent(int id) {
        return telemetryHelper.traceAndMetric("merchant_document.deleteMerchantDocumentPermanent", () -> 
            merchantDocumentCommandService.deletePermanent(pb.merchant_document.MerchantDocumentOuterClass.FindMerchantDocumentByIdRequest.newBuilder()
                    .setDocumentId(id)
                    .build())
                    .map(MerchantDocumentDto.SimpleResponse::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to permanently delete merchant document " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDocumentDto.SimpleResponse> restoreAllMerchantDocuments() {
        return telemetryHelper.traceAndMetric("merchant_document.restoreAllMerchantDocuments", () -> 
            merchantDocumentCommandService.restoreAll(com.google.protobuf.Empty.getDefaultInstance())
                    .map(MerchantDocumentDto.SimpleResponse::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to restore all merchant documents: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDocumentDto.SimpleResponse> deleteAllMerchantDocuments() {
        return telemetryHelper.traceAndMetric("merchant_document.deleteAllMerchantDocuments", () -> 
            merchantDocumentCommandService.deleteAllPermanent(com.google.protobuf.Empty.getDefaultInstance())
                    .map(MerchantDocumentDto.SimpleResponse::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to permanently delete all merchant documents: " + throwable.getMessage(), throwable))
        );
    }
}
