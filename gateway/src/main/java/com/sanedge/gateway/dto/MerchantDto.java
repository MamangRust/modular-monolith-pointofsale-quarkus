package com.sanedge.gateway.dto;

import java.util.List;
import java.util.stream.Collectors;

public class MerchantDto {
    public record CreateRequest(
            String name,
            int userId) {
    }

    public record UpdateRequest(
            int merchantId,
            String name,
            int userId,
            String status) {
    }

    public record MerchantResponse(
            int id,
            String name,
            String apiKey,
            String status,
            int userId,
            String createdAt,
            String updatedAt) {
        public static MerchantResponse from(pb.merchant.Merchant.MerchantResponse proto) {
            return new MerchantResponse(
                    proto.getId(),
                    proto.getName(),
                    proto.getApiKey(),
                    proto.getStatus(),
                    proto.getUserId(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt());
        }
    }

    public record MerchantResponseDeleteAt(
            int id,
            String name,
            String apiKey,
            String status,
            int userId,
            String createdAt,
            String updatedAt,
            String deletedAt) {
        public static MerchantResponseDeleteAt from(pb.merchant.Merchant.MerchantResponseDeleteAt proto) {
            return new MerchantResponseDeleteAt(
                    proto.getId(),
                    proto.getName(),
                    proto.getApiKey(),
                    proto.getStatus(),
                    proto.getUserId(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt(),
                    proto.hasDeletedAt() ? proto.getDeletedAt().getValue() : null);
        }
    }

    public record ApiResponseMerchant(
            String status,
            String message,
            MerchantResponse data) {
        public static ApiResponseMerchant from(pb.merchant.Merchant.ApiResponseMerchant proto) {
            return new ApiResponseMerchant(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? MerchantResponse.from(proto.getData()) : null);
        }
    }

    public record ApiResponseMerchantDeleteAt(
            String status,
            String message,
            MerchantResponseDeleteAt data) {
        public static ApiResponseMerchantDeleteAt from(pb.merchant.Merchant.ApiResponseMerchantDeleteAt proto) {
            return new ApiResponseMerchantDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? MerchantResponseDeleteAt.from(proto.getData()) : null);
        }
    }

    public record PaginationMeta(
            int currentPage,
            int pageSize,
            int totalPage,
            int totalRecords) {
        public static PaginationMeta from(pb.common.PaginationMeta proto) {
            return new PaginationMeta(
                    proto.getCurrentPage(),
                    proto.getPageSize(),
                    proto.getTotalPages(),
                    proto.getTotalRecords());
        }
    }

    public record ApiResponsePaginationMerchant(
            String status,
            String message,
            List<MerchantResponse> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationMerchant from(pb.merchant.MerchantQuery.ApiResponsePaginationMerchant proto) {
            List<MerchantResponse> list = proto.getDataList().stream()
                    .map(MerchantResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationMerchant(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPaginationMeta() ? PaginationMeta.from(proto.getPaginationMeta()) : null);
        }
    }

    public record ApiResponsePaginationMerchantDeleteAt(
            String status,
            String message,
            List<MerchantResponseDeleteAt> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationMerchantDeleteAt from(pb.merchant.MerchantQuery.ApiResponsePaginationMerchantDeleteAt proto) {
            List<MerchantResponseDeleteAt> list = proto.getDataList().stream()
                    .map(MerchantResponseDeleteAt::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationMerchantDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPaginationMeta() ? PaginationMeta.from(proto.getPaginationMeta()) : null);
        }
    }

    public record SimpleResponse(
            String status,
            String message) {
        public static SimpleResponse from(pb.merchant.MerchantCommand.ApiResponseMerchantDelete proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }
        public static SimpleResponse from(pb.merchant.MerchantCommand.ApiResponseMerchantAll proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }
    }
}
