package com.sanedge.gateway.service.impl;

import com.sanedge.gateway.dto.MerchantDto;
import com.sanedge.gateway.domain.requests.FindAllMerchantsRequest;
import com.sanedge.gateway.service.MerchantService;
import com.sanedge.gateway.telemetry.TelemetryHelper;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class MerchantServiceImpl implements MerchantService {

    private static final Logger LOG = Logger.getLogger(MerchantServiceImpl.class);

    @Inject
    TelemetryHelper telemetryHelper;

    @GrpcClient("merchant")
    pb.merchant.MutinyMerchantQueryServiceGrpc.MutinyMerchantQueryServiceStub merchantQueryService;

    @GrpcClient("merchant")
    pb.merchant.MutinyMerchantCommandServiceGrpc.MutinyMerchantCommandServiceStub merchantCommandService;

    @Override
    public Uni<MerchantDto.ApiResponsePaginationMerchant> listMerchants(FindAllMerchantsRequest request) {
        return telemetryHelper.traceAndMetric("merchant.listMerchants", () -> 
            merchantQueryService.findAllMerchant(pb.merchant.Merchant.FindAllMerchantRequest.newBuilder()
                    .setPage(request.getPage())
                    .setPageSize(request.getSize())
                    .setSearch(request.getSearch() == null ? "" : request.getSearch())
                    .build())
                    .map(MerchantDto.ApiResponsePaginationMerchant::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to list merchants: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDto.ApiResponseMerchant> getMerchant(int id) {
        return telemetryHelper.traceAndMetric("merchant.getMerchant", () -> 
            merchantQueryService.findByIdMerchant(pb.merchant.Merchant.FindByIdMerchantRequest.newBuilder()
                    .setMerchantId(id)
                    .build())
                    .map(MerchantDto.ApiResponseMerchant::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to get merchant " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDto.ApiResponsePaginationMerchantDeleteAt> getActiveMerchants(FindAllMerchantsRequest request) {
        return telemetryHelper.traceAndMetric("merchant.getActiveMerchants", () -> 
            merchantQueryService.findByActive(pb.merchant.Merchant.FindAllMerchantRequest.newBuilder()
                    .setPage(request.getPage())
                    .setPageSize(request.getSize())
                    .setSearch(request.getSearch() == null ? "" : request.getSearch())
                    .build())
                    .map(MerchantDto.ApiResponsePaginationMerchantDeleteAt::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to list active merchants: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDto.ApiResponsePaginationMerchantDeleteAt> getTrashedMerchants(FindAllMerchantsRequest request) {
        return telemetryHelper.traceAndMetric("merchant.getTrashedMerchants", () -> 
            merchantQueryService.findByTrashed(pb.merchant.Merchant.FindAllMerchantRequest.newBuilder()
                    .setPage(request.getPage())
                    .setPageSize(request.getSize())
                    .setSearch(request.getSearch() == null ? "" : request.getSearch())
                    .build())
                    .map(MerchantDto.ApiResponsePaginationMerchantDeleteAt::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to list trashed merchants: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDto.ApiResponseMerchant> createMerchant(MerchantDto.CreateRequest body) {
        return telemetryHelper.traceAndMetric("merchant.createMerchant", () -> 
            merchantCommandService.createMerchant(pb.merchant.MerchantCommand.CreateMerchantRequest.newBuilder()
                    .setName(body.name())
                    .setUserId(body.userId())
                    .build())
                    .map(MerchantDto.ApiResponseMerchant::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to create merchant: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDto.ApiResponseMerchant> updateMerchant(int id, MerchantDto.UpdateRequest body) {
        return telemetryHelper.traceAndMetric("merchant.updateMerchant", () -> 
            merchantCommandService.updateMerchant(pb.merchant.MerchantCommand.UpdateMerchantRequest.newBuilder()
                    .setMerchantId(id)
                    .setName(body.name())
                    .setUserId(body.userId())
                    .setStatus(body.status() == null ? "" : body.status())
                    .build())
                    .map(MerchantDto.ApiResponseMerchant::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to update merchant " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDto.ApiResponseMerchantDeleteAt> deleteMerchant(int id) {
        return telemetryHelper.traceAndMetric("merchant.deleteMerchant", () -> 
            merchantCommandService.trashedMerchant(pb.merchant.Merchant.FindByIdMerchantRequest.newBuilder()
                    .setMerchantId(id)
                    .build())
                    .map(MerchantDto.ApiResponseMerchantDeleteAt::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to soft-delete merchant " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDto.ApiResponseMerchantDeleteAt> restoreMerchant(int id) {
        return telemetryHelper.traceAndMetric("merchant.restoreMerchant", () -> 
            merchantCommandService.restoreMerchant(pb.merchant.Merchant.FindByIdMerchantRequest.newBuilder()
                    .setMerchantId(id)
                    .build())
                    .map(MerchantDto.ApiResponseMerchantDeleteAt::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to restore merchant " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDto.SimpleResponse> deleteMerchantPermanent(int id) {
        return telemetryHelper.traceAndMetric("merchant.deleteMerchantPermanent", () -> 
            merchantCommandService.deleteMerchantPermanent(pb.merchant.Merchant.FindByIdMerchantRequest.newBuilder()
                    .setMerchantId(id)
                    .build())
                    .map(MerchantDto.SimpleResponse::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to permanently delete merchant " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDto.SimpleResponse> restoreAllMerchant() {
        return telemetryHelper.traceAndMetric("merchant.restoreAllMerchant", () -> 
            merchantCommandService.restoreAllMerchant(com.google.protobuf.Empty.getDefaultInstance())
                    .map(MerchantDto.SimpleResponse::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to restore all merchants: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<MerchantDto.SimpleResponse> deleteAllMerchantPermanent() {
        return telemetryHelper.traceAndMetric("merchant.deleteAllMerchantPermanent", () -> 
            merchantCommandService.deleteAllMerchantPermanent(com.google.protobuf.Empty.getDefaultInstance())
                    .map(MerchantDto.SimpleResponse::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to permanently delete all merchants: " + throwable.getMessage(), throwable))
        );
    }
}
