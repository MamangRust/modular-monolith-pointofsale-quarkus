package com.sanedge.gateway.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.gateway.dto.RoleDto;
import com.sanedge.gateway.service.RoleService;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class RoleResourceTest {
    @Mock
    RoleService roleService;
    private RoleResource roleResource;

    @BeforeEach
    void setUp() throws Exception {
        roleResource = new RoleResource();
        Field f = RoleResource.class.getDeclaredField("roleService");
        f.setAccessible(true);
        f.set(roleResource, roleService);
    }

    @Test
    void listRoles_Success() {
        RoleDto.ApiResponsePaginationRole dto = new RoleDto.ApiResponsePaginationRole(
                "success", "ok", List.of(), null);
        lenient().when(roleService.listRoles(any())).thenReturn(Uni.createFrom().item(dto));
        Response r = roleResource.listRoles(1, 10, "").await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void getRole_Success() {
        RoleDto.ApiResponseRole dto = new RoleDto.ApiResponseRole("success", "ok", null);
        lenient().when(roleService.getRole(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = roleResource.getRole(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void createRole_Success_Returns201() {
        RoleDto.ApiResponseRole dto = new RoleDto.ApiResponseRole("success", "created", null);
        lenient().when(roleService.createRole(any())).thenReturn(Uni.createFrom().item(dto));
        RoleDto.CreateRequest req = new RoleDto.CreateRequest("Admin");
        Response r = roleResource.createRole(req).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(201);
    }

    @Test
    void deleteRole_Success() {
        RoleDto.ApiResponseRoleDeleteAt dto = new RoleDto.ApiResponseRoleDeleteAt("success", "trashed", null);
        lenient().when(roleService.deleteRole(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = roleResource.deleteRole(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void restoreRole_Success() {
        RoleDto.ApiResponseRoleDeleteAt dto = new RoleDto.ApiResponseRoleDeleteAt("success", "restored", null);
        lenient().when(roleService.restoreRole(anyInt())).thenReturn(Uni.createFrom().item(dto));
        Response r = roleResource.restoreRole(1).await().indefinitely();
        assertThat(r.getStatus()).isEqualTo(200);
    }
}
