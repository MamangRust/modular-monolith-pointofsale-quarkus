package com.sanedge.gateway.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.gateway.dto.AuthDto;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class AuthResourceTest {

    @Mock
    private pb.MutinyAuthServiceGrpc.MutinyAuthServiceStub authService;

    private AuthResource authResource;

    @BeforeEach
    void setUp() throws Exception {
        authResource = new AuthResource();
        Field serviceField = AuthResource.class.getDeclaredField("authService");
        serviceField.setAccessible(true);
        serviceField.set(authResource, authService);
    }

    @Test
    void register_Success_Returns201() {
        pb.Auth.ApiResponseRegister proto = pb.Auth.ApiResponseRegister.newBuilder()
                .setStatus("success").setMessage("registered").build();
        lenient().when(authService.registerUser(any(pb.Auth.RegisterRequest.class)))
                .thenReturn(Uni.createFrom().item(proto));

        AuthDto.RegisterRequest req = new AuthDto.RegisterRequest("John", "Doe", "u@e.com", "pwd", "pwd");
        Response response = authResource.register(req).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo(201);
    }

    @Test
    void login_Success_Returns200() {
        pb.Auth.ApiResponseLogin proto = pb.Auth.ApiResponseLogin.newBuilder()
                .setStatus("success").setMessage("logged in").build();
        lenient().when(authService.loginUser(any(pb.Auth.LoginRequest.class)))
                .thenReturn(Uni.createFrom().item(proto));

        AuthDto.LoginRequest req = new AuthDto.LoginRequest("u@e.com", "pwd");
        Response response = authResource.login(req).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void verify_Success_Returns200() {
        pb.Auth.ApiResponseVerifyCode proto = pb.Auth.ApiResponseVerifyCode.newBuilder()
                .setStatus("success").setMessage("verified").build();
        lenient().when(authService.verifyCode(any(pb.Auth.VerifyCodeRequest.class)))
                .thenReturn(Uni.createFrom().item(proto));

        AuthDto.VerifyCodeRequest req = new AuthDto.VerifyCodeRequest("ABC123");
        Response response = authResource.verify(req).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void forgotPassword_Success_Returns200() {
        pb.Auth.ApiResponseForgotPassword proto = pb.Auth.ApiResponseForgotPassword.newBuilder()
                .setStatus("success").setMessage("email sent").build();
        lenient().when(authService.forgotPassword(any(pb.Auth.ForgotPasswordRequest.class)))
                .thenReturn(Uni.createFrom().item(proto));

        AuthDto.ForgotPasswordRequest req = new AuthDto.ForgotPasswordRequest("u@e.com");
        Response response = authResource.forgotPassword(req).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void resetPassword_Success_Returns200() {
        pb.Auth.ApiResponseResetPassword proto = pb.Auth.ApiResponseResetPassword.newBuilder()
                .setStatus("success").setMessage("password reset").build();
        lenient().when(authService.resetPassword(any(pb.Auth.ResetPasswordRequest.class)))
                .thenReturn(Uni.createFrom().item(proto));

        AuthDto.ResetPasswordRequest req = new AuthDto.ResetPasswordRequest("token", "newpwd", "newpwd");
        Response response = authResource.resetPassword(req).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void refresh_Success_Returns200() {
        pb.Auth.ApiResponseRefreshToken proto = pb.Auth.ApiResponseRefreshToken.newBuilder()
                .setStatus("success").setMessage("refreshed").build();
        lenient().when(authService.refreshToken(any(pb.Auth.RefreshTokenRequest.class)))
                .thenReturn(Uni.createFrom().item(proto));

        AuthDto.RefreshTokenRequest req = new AuthDto.RefreshTokenRequest("refresh");
        Response response = authResource.refresh(req).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void getMe_Success_Returns200() {
        pb.Auth.ApiResponseGetMe proto = pb.Auth.ApiResponseGetMe.newBuilder()
                .setStatus("success").setMessage("me").build();
        lenient().when(authService.getMe(any(pb.Auth.GetMeRequest.class)))
                .thenReturn(Uni.createFrom().item(proto));

        Response response = authResource.getMe(1).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
