package com.sanedge.gateway.dto;

import java.util.List;
import java.util.stream.Collectors;

public class RoleDto {
    public record CreateRequest(String name) {
    }

    public record UpdateRequest(
            int id,
            String name) {
    }

    public record RoleResponse(
            int id,
            String name,
            String createdAt,
            String updatedAt) {
        public static RoleResponse from(pb.role.Role.RoleResponse proto) {
            return new RoleResponse(
                    proto.getId(),
                    proto.getName(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt());
        }
    }

    public record RoleResponseDeleteAt(
            int id,
            String name,
            String createdAt,
            String updatedAt,
            String deletedAt) {
        public static RoleResponseDeleteAt from(pb.role.Role.RoleResponseDeleteAt proto) {
            return new RoleResponseDeleteAt(
                    proto.getId(),
                    proto.getName(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt(),
                    proto.hasDeletedAt() ? proto.getDeletedAt().getValue() : null);
        }
    }

    public record ApiResponseRole(
            String status,
            String message,
            RoleResponse data) {
        public static ApiResponseRole from(pb.role.Role.ApiResponseRole proto) {
            return new ApiResponseRole(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? RoleResponse.from(proto.getData()) : null);
        }
    }

    public record ApiResponseRoleDeleteAt(
            String status,
            String message,
            RoleResponseDeleteAt data) {
        public static ApiResponseRoleDeleteAt from(pb.role.Role.ApiResponseRoleDeleteAt proto) {
            return new ApiResponseRoleDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? RoleResponseDeleteAt.from(proto.getData()) : null);
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

    public record ApiResponsePaginationRole(
            String status,
            String message,
            List<RoleResponse> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationRole from(pb.role.RoleQuery.ApiResponsePaginationRole proto) {
            List<RoleResponse> list = proto.getDataList().stream()
                    .map(RoleResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationRole(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPaginationMeta() ? PaginationMeta.from(proto.getPaginationMeta()) : null);
        }
    }

    public record ApiResponsePaginationRoleDeleteAt(
            String status,
            String message,
            List<RoleResponseDeleteAt> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationRoleDeleteAt from(pb.role.RoleQuery.ApiResponsePaginationRoleDeleteAt proto) {
            List<RoleResponseDeleteAt> list = proto.getDataList().stream()
                    .map(RoleResponseDeleteAt::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationRoleDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPaginationMeta() ? PaginationMeta.from(proto.getPaginationMeta()) : null);
        }
    }

    public record SimpleResponse(
            String status,
            String message) {
        public static SimpleResponse from(pb.role.RoleCommand.ApiResponseRoleDelete proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }
        public static SimpleResponse from(pb.role.RoleCommand.ApiResponseRoleAll proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }
    }
}
