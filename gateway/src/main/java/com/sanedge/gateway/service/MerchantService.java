package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.MerchantDto;
import com.sanedge.gateway.domain.requests.FindAllMerchantsRequest;
import io.smallrye.mutiny.Uni;

public interface MerchantService {
    Uni<MerchantDto.ApiResponsePaginationMerchant> listMerchants(FindAllMerchantsRequest request);
    Uni<MerchantDto.ApiResponseMerchant> getMerchant(int id);
    Uni<MerchantDto.ApiResponsePaginationMerchantDeleteAt> getActiveMerchants(FindAllMerchantsRequest request);
    Uni<MerchantDto.ApiResponsePaginationMerchantDeleteAt> getTrashedMerchants(FindAllMerchantsRequest request);
    Uni<MerchantDto.ApiResponseMerchant> createMerchant(MerchantDto.CreateRequest body);
    Uni<MerchantDto.ApiResponseMerchant> updateMerchant(int id, MerchantDto.UpdateRequest body);
    Uni<MerchantDto.ApiResponseMerchantDeleteAt> deleteMerchant(int id);
    Uni<MerchantDto.ApiResponseMerchantDeleteAt> restoreMerchant(int id);
    Uni<MerchantDto.SimpleResponse> deleteMerchantPermanent(int id);
    Uni<MerchantDto.SimpleResponse> restoreAllMerchant();
    Uni<MerchantDto.SimpleResponse> deleteAllMerchantPermanent();
}
