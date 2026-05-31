package com.sanedge.gateway.dto;

import java.util.List;
import java.util.stream.Collectors;

public class MerchantDocumentDto {
    public record CreateRequest(
            int merchantId,
            String documentType,
            String documentUrl) {
    }

    public record UpdateRequest(
            int merchantId,
            String documentType,
            String documentUrl,
            String note,
            String status) {
    }

    public record UpdateStatusRequest(
            int merchantId,
            String note,
            String status) {
    }

    public record MerchantDocument(
            int documentId,
            int merchantId,
            String documentType,
            String documentUrl,
            String status,
            String note,
            String uploadedAt,
            String updatedAt) {
        public static MerchantDocument from(pb.merchant_document.MerchantDocumentOuterClass.MerchantDocument proto) {
            return new MerchantDocument(
                    proto.getDocumentId(),
                    proto.getMerchantId(),
                    proto.getDocumentType(),
                    proto.getDocumentUrl(),
                    proto.getStatus(),
                    proto.getNote(),
                    proto.getUploadedAt(),
                    proto.getUpdatedAt());
        }
    }

    public record MerchantDocumentDeleteAt(
            int documentId,
            int merchantId,
            String documentType,
            String documentUrl,
            String status,
            String note,
            String uploadedAt,
            String updatedAt,
            String deletedAt) {
        public static MerchantDocumentDeleteAt from(pb.merchant_document.MerchantDocumentOuterClass.MerchantDocumentDeleteAt proto) {
            return new MerchantDocumentDeleteAt(
                    proto.getDocumentId(),
                    proto.getMerchantId(),
                    proto.getDocumentType(),
                    proto.getDocumentUrl(),
                    proto.getStatus(),
                    proto.getNote(),
                    proto.getUploadedAt(),
                    proto.getUpdatedAt(),
                    proto.hasDeletedAt() ? proto.getDeletedAt().getValue() : null);
        }
    }

    public record ApiResponseMerchantDocument(
            String status,
            String message,
            MerchantDocument data) {
        public static ApiResponseMerchantDocument from(pb.merchant_document.MerchantDocumentOuterClass.ApiResponseMerchantDocument proto) {
            return new ApiResponseMerchantDocument(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? MerchantDocument.from(proto.getData()) : null);
        }
    }

    public record ApiResponseMerchantDocumentDeleteAt(
            String status,
            String message,
            MerchantDocumentDeleteAt data) {
        public static ApiResponseMerchantDocumentDeleteAt from(pb.merchant_document.MerchantDocumentOuterClass.ApiResponseMerchantDocumentDeleteAt proto) {
            return new ApiResponseMerchantDocumentDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? MerchantDocumentDeleteAt.from(proto.getData()) : null);
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

    public record ApiResponsePaginationMerchantDocument(
            String status,
            String message,
            List<MerchantDocument> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationMerchantDocument from(pb.merchant_document.MerchantDocumentQuery.ApiResponsePaginationMerchantDocument proto) {
            List<MerchantDocument> list = proto.getDataList().stream()
                    .map(MerchantDocument::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationMerchantDocument(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPaginationMeta() ? PaginationMeta.from(proto.getPaginationMeta()) : null);
        }
    }

    public record ApiResponsePaginationMerchantDocumentAt(
            String status,
            String message,
            List<MerchantDocumentDeleteAt> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationMerchantDocumentAt from(pb.merchant_document.MerchantDocumentQuery.ApiResponsePaginationMerchantDocumentAt proto) {
            List<MerchantDocumentDeleteAt> list = proto.getDataList().stream()
                    .map(MerchantDocumentDeleteAt::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationMerchantDocumentAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPaginationMeta() ? PaginationMeta.from(proto.getPaginationMeta()) : null);
        }
    }

    public record SimpleResponse(
            String status,
            String message) {
        public static SimpleResponse from(pb.merchant_document.MerchantDocumentCommand.ApiResponseMerchantDocumentDelete proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }

        public static SimpleResponse from(pb.merchant_document.MerchantDocumentCommand.ApiResponseMerchantDocumentAll proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }
    }
}
