package com.sanedge.gateway.service;

import com.sanedge.gateway.dto.RoleDto;
import com.sanedge.gateway.domain.requests.FindAllRolesRequest;
import io.smallrye.mutiny.Uni;

public interface RoleService {
    Uni<RoleDto.ApiResponsePaginationRole> listRoles(FindAllRolesRequest request);
    Uni<RoleDto.ApiResponseRole> getRole(int id);
    Uni<RoleDto.ApiResponsePaginationRoleDeleteAt> getActiveRoles(FindAllRolesRequest request);
    Uni<RoleDto.ApiResponsePaginationRoleDeleteAt> getTrashedRoles(FindAllRolesRequest request);
    Uni<RoleDto.ApiResponseRole> createRole(RoleDto.CreateRequest body);
    Uni<RoleDto.ApiResponseRole> updateRole(int id, RoleDto.UpdateRequest body);
    Uni<RoleDto.ApiResponseRoleDeleteAt> deleteRole(int id);
    Uni<RoleDto.ApiResponseRoleDeleteAt> restoreRole(int id);
    Uni<RoleDto.SimpleResponse> deleteRolePermanent(int id);
    Uni<RoleDto.SimpleResponse> restoreAllRole();
    Uni<RoleDto.SimpleResponse> deleteAllRolePermanent();
}
