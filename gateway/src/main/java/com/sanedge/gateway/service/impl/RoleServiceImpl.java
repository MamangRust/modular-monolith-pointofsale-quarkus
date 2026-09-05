package com.sanedge.gateway.service.impl;

import com.sanedge.gateway.dto.RoleDto;
import com.sanedge.gateway.domain.requests.FindAllRolesRequest;
import com.sanedge.gateway.service.RoleService;
import com.sanedge.gateway.telemetry.TelemetryHelper;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RoleServiceImpl implements RoleService {

    private static final Logger LOG = Logger.getLogger(RoleServiceImpl.class);

    @Inject
    TelemetryHelper telemetryHelper;

    @GrpcClient("role")
    pb.role.MutinyRoleServiceGrpc.MutinyRoleServiceStub roleQueryService;

    @GrpcClient("role")
    pb.role.MutinyRoleCommandServiceGrpc.MutinyRoleCommandServiceStub roleCommandService;

    @Override
    public Uni<RoleDto.ApiResponsePaginationRole> listRoles(FindAllRolesRequest request) {
        return telemetryHelper.traceAndMetric("role.listRoles", () -> 
            roleQueryService.findAllRole(pb.role.Role.FindAllRoleRequest.newBuilder()
                    .setPage(request.getPage())
                    .setPageSize(request.getSize())
                    .setSearch(request.getSearch() == null ? "" : request.getSearch())
                    .build())
                    .map(RoleDto.ApiResponsePaginationRole::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to list roles: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<RoleDto.ApiResponseRole> getRole(int id) {
        return telemetryHelper.traceAndMetric("role.getRole", () -> 
            roleQueryService.findByIdRole(pb.role.Role.FindByIdRoleRequest.newBuilder()
                    .setRoleId(id)
                    .build())
                    .map(RoleDto.ApiResponseRole::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to get role " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<RoleDto.ApiResponsePaginationRoleDeleteAt> getActiveRoles(FindAllRolesRequest request) {
        return telemetryHelper.traceAndMetric("role.getActiveRoles", () -> 
            roleQueryService.findByActive(pb.role.Role.FindAllRoleRequest.newBuilder()
                    .setPage(request.getPage())
                    .setPageSize(request.getSize())
                    .setSearch(request.getSearch() == null ? "" : request.getSearch())
                    .build())
                    .map(RoleDto.ApiResponsePaginationRoleDeleteAt::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to list active roles: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<RoleDto.ApiResponsePaginationRoleDeleteAt> getTrashedRoles(FindAllRolesRequest request) {
        return telemetryHelper.traceAndMetric("role.getTrashedRoles", () -> 
            roleQueryService.findByTrashed(pb.role.Role.FindAllRoleRequest.newBuilder()
                    .setPage(request.getPage())
                    .setPageSize(request.getSize())
                    .setSearch(request.getSearch() == null ? "" : request.getSearch())
                    .build())
                    .map(RoleDto.ApiResponsePaginationRoleDeleteAt::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to list trashed roles: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<RoleDto.ApiResponseRole> createRole(RoleDto.CreateRequest body) {
        return telemetryHelper.traceAndMetric("role.createRole", () -> 
            roleCommandService.createRole(pb.role.RoleCommand.CreateRoleRequest.newBuilder()
                    .setName(body.name())
                    .build())
                    .map(RoleDto.ApiResponseRole::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to create role: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<RoleDto.ApiResponseRole> updateRole(int id, RoleDto.UpdateRequest body) {
        return telemetryHelper.traceAndMetric("role.updateRole", () -> 
            roleCommandService.updateRole(pb.role.RoleCommand.UpdateRoleRequest.newBuilder()
                    .setId(id)
                    .setName(body.name())
                    .build())
                    .map(RoleDto.ApiResponseRole::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to update role " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<RoleDto.ApiResponseRoleDeleteAt> deleteRole(int id) {
        return telemetryHelper.traceAndMetric("role.deleteRole", () -> 
            roleCommandService.trashedRole(pb.role.Role.FindByIdRoleRequest.newBuilder()
                    .setRoleId(id)
                    .build())
                    .map(RoleDto.ApiResponseRoleDeleteAt::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to soft-delete role " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<RoleDto.ApiResponseRoleDeleteAt> restoreRole(int id) {
        return telemetryHelper.traceAndMetric("role.restoreRole", () -> 
            roleCommandService.restoreRole(pb.role.Role.FindByIdRoleRequest.newBuilder()
                    .setRoleId(id)
                    .build())
                    .map(RoleDto.ApiResponseRoleDeleteAt::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to restore role " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<RoleDto.SimpleResponse> deleteRolePermanent(int id) {
        return telemetryHelper.traceAndMetric("role.deleteRolePermanent", () -> 
            roleCommandService.deleteRolePermanent(pb.role.Role.FindByIdRoleRequest.newBuilder()
                    .setRoleId(id)
                    .build())
                    .map(RoleDto.SimpleResponse::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to permanently delete role " + id + ": " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<RoleDto.SimpleResponse> restoreAllRole() {
        return telemetryHelper.traceAndMetric("role.restoreAllRole", () -> 
            roleCommandService.restoreAllRole(com.google.protobuf.Empty.getDefaultInstance())
                    .map(RoleDto.SimpleResponse::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to restore all roles: " + throwable.getMessage(), throwable))
        );
    }

    @Override
    public Uni<RoleDto.SimpleResponse> deleteAllRolePermanent() {
        return telemetryHelper.traceAndMetric("role.deleteAllRolePermanent", () -> 
            roleCommandService.deleteAllRolePermanent(com.google.protobuf.Empty.getDefaultInstance())
                    .map(RoleDto.SimpleResponse::from)
                    .onFailure().invoke(throwable -> LOG.error("Failed to permanently delete all roles: " + throwable.getMessage(), throwable))
        );
    }
}
