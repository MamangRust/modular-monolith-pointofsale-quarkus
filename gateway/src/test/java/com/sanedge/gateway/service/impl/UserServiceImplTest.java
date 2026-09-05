package com.sanedge.gateway.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.gateway.dto.UserDto;
import com.sanedge.gateway.telemetry.TelemetryHelper;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private TelemetryHelper telemetryHelper;
    @Mock
    private pb.user.MutinyUserQueryServiceGrpc.MutinyUserQueryServiceStub userQueryService;
    @Mock
    private pb.user.MutinyUserCommandServiceGrpc.MutinyUserCommandServiceStub userCommandService;

    private UserServiceImpl service;

    private void inject(String name, Object value) throws Exception {
        Field f = UserServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(telemetryHelper.traceAndMetric(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Uni<?>> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
        service = new UserServiceImpl();
        inject("telemetryHelper", telemetryHelper);
        inject("userQueryService", userQueryService);
        inject("userCommandService", userCommandService);
    }

    @Test
    void listUsers_PropagatesResponse() {
        pb.user.UserQuery.ApiResponsePaginationUser proto = pb.user.UserQuery.ApiResponsePaginationUser.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(userQueryService.findAll(any(pb.user.User.FindAllUserRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.listUsers(new com.sanedge.gateway.domain.requests.FindAllUsersRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getActiveUsers_PropagatesResponse() {
        pb.user.UserQuery.ApiResponsePaginationUserDeleteAt proto = pb.user.UserQuery.ApiResponsePaginationUserDeleteAt.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(userQueryService.findByActive(any(pb.user.User.FindAllUserRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getActiveUsers(new com.sanedge.gateway.domain.requests.FindAllUsersRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getTrashedUsers_PropagatesResponse() {
        pb.user.UserQuery.ApiResponsePaginationUserDeleteAt proto = pb.user.UserQuery.ApiResponsePaginationUserDeleteAt.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(userQueryService.findByTrashed(any(pb.user.User.FindAllUserRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getTrashedUsers(new com.sanedge.gateway.domain.requests.FindAllUsersRequest(null, 1, 10)).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void getUser_PropagatesResponse() {
        pb.user.User.ApiResponseUser proto = pb.user.User.ApiResponseUser.newBuilder()
                .setStatus("success").setMessage("ok").build();
        lenient().when(userQueryService.findById(any(pb.user.User.FindByIdUserRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.getUser(1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void createUser_PropagatesResponse() {
        pb.user.User.ApiResponseUser proto = pb.user.User.ApiResponseUser.newBuilder()
                .setStatus("success").setMessage("created").build();
        UserDto.CreateRequest req = new UserDto.CreateRequest("John", "Doe", "u@e.com", "p", "p");
        lenient().when(userCommandService.create(any(pb.user.UserCommand.CreateUserRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.createUser(req).await().indefinitely();
        assertThat(result.message()).isEqualTo("created");
    }

    @Test
    void updateUser_PropagatesResponse() {
        pb.user.User.ApiResponseUser proto = pb.user.User.ApiResponseUser.newBuilder()
                .setStatus("success").setMessage("updated").build();
        lenient().when(userCommandService.update(any(pb.user.UserCommand.UpdateUserRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.updateUser(1, new UserDto.UpdateRequest(1, "John", "Doe", "u@e.com", null, null)).await().indefinitely();
        assertThat(result.message()).isEqualTo("updated");
    }

    @Test
    void deleteUser_TrashStub_Propagates() {
        pb.user.User.ApiResponseUserDeleteAt proto = pb.user.User.ApiResponseUserDeleteAt.newBuilder()
                .setStatus("success").setMessage("trashed").build();
        lenient().when(userCommandService.trashedUser(any(pb.user.User.FindByIdUserRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.deleteUser(1).await().indefinitely();
        assertThat(result.message()).isEqualTo("trashed");
    }

    @Test
    void restoreUser_RestoreStub_Propagates() {
        pb.user.User.ApiResponseUserDeleteAt proto = pb.user.User.ApiResponseUserDeleteAt.newBuilder()
                .setStatus("success").setMessage("restored").build();
        lenient().when(userCommandService.restoreUser(any(pb.user.User.FindByIdUserRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.restoreUser(1).await().indefinitely();
        assertThat(result.message()).isEqualTo("restored");
    }

    @Test
    void deleteUserPermanent_Propagates() {
        pb.user.UserCommand.ApiResponseUserDelete proto = pb.user.UserCommand.ApiResponseUserDelete.newBuilder()
                .setStatus("success").setMessage("deleted").build();
        lenient().when(userCommandService.deleteUserPermanent(any(pb.user.User.FindByIdUserRequest.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.deleteUserPermanent(1).await().indefinitely();
        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void restoreAllUser_Propagates() {
        pb.user.UserCommand.ApiResponseUserAll proto = pb.user.UserCommand.ApiResponseUserAll.newBuilder()
                .setStatus("success").setMessage("all restored").build();
        lenient().when(userCommandService.restoreAllUser(any(com.google.protobuf.Empty.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.restoreAllUser().await().indefinitely();
        assertThat(result.message()).isEqualTo("all restored");
    }

    @Test
    void deleteAllUserPermanent_Propagates() {
        pb.user.UserCommand.ApiResponseUserAll proto = pb.user.UserCommand.ApiResponseUserAll.newBuilder()
                .setStatus("success").setMessage("all deleted").build();
        lenient().when(userCommandService.deleteAllUserPermanent(any(com.google.protobuf.Empty.class)))
                .thenAnswer(inv -> Uni.createFrom().item(proto));

        var result = service.deleteAllUserPermanent().await().indefinitely();
        assertThat(result.message()).isEqualTo("all deleted");
    }
}
