package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.MerchantDto;
import io.smallrye.mutiny.Uni;

public interface MerchantService {
    Uni<MerchantDto.ApiResponsePaginationMerchant> listMerchants(int page, int size, String search);
    Uni<MerchantDto.ApiResponseMerchant> getMerchant(int id);
    Uni<MerchantDto.ApiResponsePaginationMerchantDeleteAt> getActiveMerchants(int page, int size, String search);
    Uni<MerchantDto.ApiResponsePaginationMerchantDeleteAt> getTrashedMerchants(int page, int size, String search);
    Uni<MerchantDto.ApiResponseMerchant> createMerchant(MerchantDto.CreateRequest body);
    Uni<MerchantDto.ApiResponseMerchant> updateMerchant(int id, MerchantDto.UpdateRequest body);
    Uni<MerchantDto.ApiResponseMerchantDeleteAt> deleteMerchant(int id);
    Uni<MerchantDto.ApiResponseMerchantDeleteAt> restoreMerchant(int id);
    Uni<MerchantDto.SimpleResponse> deleteMerchantPermanent(int id);
    Uni<MerchantDto.SimpleResponse> restoreAllMerchant();
    Uni<MerchantDto.SimpleResponse> deleteAllMerchantPermanent();
}
