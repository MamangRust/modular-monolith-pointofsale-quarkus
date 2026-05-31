package com.sanedge.gateway.dto;

import java.util.List;
import java.util.stream.Collectors;

public class UserDto {

    public record CreateRequest(
            String firstname,
            String lastname,
            String email,
            String password,
            String confirmPassword) {
    }

    public record UpdateRequest(
            int id,
            String firstname,
            String lastname,
            String email,
            String password,
            String confirmPassword) {
    }

    public record UserResponse(
            int id,
            String firstname,
            String lastname,
            String email,
            String createdAt,
            String updatedAt) {
        public static UserResponse from(pb.user.User.UserResponse proto) {
            return new UserResponse(
                    proto.getId(),
                    proto.getFirstname(),
                    proto.getLastname(),
                    proto.getEmail(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt());
        }
    }

    public record UserResponseDeleteAt(
            int id,
            String firstname,
            String lastname,
            String email,
            String createdAt,
            String updatedAt,
            String deletedAt) {
        public static UserResponseDeleteAt from(pb.user.User.UserResponseDeleteAt proto) {
            return new UserResponseDeleteAt(
                    proto.getId(),
                    proto.getFirstname(),
                    proto.getLastname(),
                    proto.getEmail(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt(),
                    proto.hasDeletedAt() ? proto.getDeletedAt().getValue() : null);
        }
    }

    public record ApiResponseUser(
            String status,
            String message,
            UserResponse data) {
        public static ApiResponseUser from(pb.user.User.ApiResponseUser proto) {
            return new ApiResponseUser(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? UserResponse.from(proto.getData()) : null);
        }
    }

    public record ApiResponseUserDeleteAt(
            String status,
            String message,
            UserResponseDeleteAt data) {
        public static ApiResponseUserDeleteAt from(pb.user.User.ApiResponseUserDeleteAt proto) {
            return new ApiResponseUserDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    proto.hasData() ? UserResponseDeleteAt.from(proto.getData()) : null);
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

    public record ApiResponsePaginationUser(
            String status,
            String message,
            List<UserResponse> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationUser from(pb.user.UserQuery.ApiResponsePaginationUser proto) {
            List<UserResponse> list = proto.getDataList().stream()
                    .map(UserResponse::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationUser(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPaginationMeta() ? PaginationMeta.from(proto.getPaginationMeta()) : null);
        }
    }

    public record ApiResponsePaginationUserDeleteAt(
            String status,
            String message,
            List<UserResponseDeleteAt> data,
            PaginationMeta paginationMeta) {
        public static ApiResponsePaginationUserDeleteAt from(pb.user.UserQuery.ApiResponsePaginationUserDeleteAt proto) {
            List<UserResponseDeleteAt> list = proto.getDataList().stream()
                    .map(UserResponseDeleteAt::from)
                    .collect(Collectors.toList());
            return new ApiResponsePaginationUserDeleteAt(
                    proto.getStatus(),
                    proto.getMessage(),
                    list,
                    proto.hasPaginationMeta() ? PaginationMeta.from(proto.getPaginationMeta()) : null);
        }
    }

    public record SimpleResponse(
            String status,
            String message) {
        public static SimpleResponse from(pb.user.UserCommand.ApiResponseUserDelete proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }
        public static SimpleResponse from(pb.user.UserCommand.ApiResponseUserAll proto) {
            return new SimpleResponse(proto.getStatus(), proto.getMessage());
        }
    }
}
