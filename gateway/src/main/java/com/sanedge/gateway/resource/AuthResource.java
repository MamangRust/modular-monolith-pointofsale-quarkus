package com.sanedge.gateway.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.sanedge.gateway.dto.AuthDto;

import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import jakarta.annotation.security.RolesAllowed;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Authentication endpoints")
public class AuthResource {

    @GrpcClient("auth")
    pb.MutinyAuthServiceGrpc.MutinyAuthServiceStub authService;

    @POST
    @Path("/register")
    @Operation(summary = "Register a new user")
    public Uni<Response> register(AuthDto.RegisterRequest body) {
        return authService.registerUser(pb.Auth.RegisterRequest.newBuilder()
                .setFirstname(body.firstname())
                .setLastname(body.lastname())
                .setEmail(body.email())
                .setPassword(body.password())
                .setConfirmPassword(body.confirmPassword())
                .build())
                .map(proto -> Response.status(Response.Status.CREATED)
                        .entity(AuthDto.RegisterResponse.from(proto))
                        .build());
    }

    @POST
    @Path("/login")
    @Operation(summary = "Login a user")
    public Uni<Response> login(AuthDto.LoginRequest body) {
        return authService.loginUser(pb.Auth.LoginRequest.newBuilder()
                .setEmail(body.email())
                .setPassword(body.password())
                .build())
                .map(proto -> Response.ok(AuthDto.LoginResponse.from(proto)).build());
    }

    @POST
    @Path("/verify")
    @Operation(summary = "Verify user email by verification code")
    public Uni<Response> verify(AuthDto.VerifyCodeRequest body) {
        return authService.verifyCode(pb.Auth.VerifyCodeRequest.newBuilder()
                .setCode(body.code())
                .build())
                .map(proto -> Response.ok(AuthDto.SimpleResponse.from(proto)).build());
    }

    @POST
    @Path("/forgot-password")
    @Operation(summary = "Initiate forgot password request")
    public Uni<Response> forgotPassword(AuthDto.ForgotPasswordRequest body) {
        return authService.forgotPassword(pb.Auth.ForgotPasswordRequest.newBuilder()
                .setEmail(body.email())
                .build())
                .map(proto -> Response.ok(AuthDto.SimpleResponse.from(proto)).build());
    }

    @POST
    @Path("/reset-password")
    @Operation(summary = "Reset user password")
    public Uni<Response> resetPassword(AuthDto.ResetPasswordRequest body) {
        return authService.resetPassword(pb.Auth.ResetPasswordRequest.newBuilder()
                .setResetToken(body.resetToken())
                .setPassword(body.password())
                .setConfirmPassword(body.confirmPassword())
                .build())
                .map(proto -> Response.ok(AuthDto.SimpleResponse.from(proto)).build());
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Refresh user access token")
    public Uni<Response> refresh(AuthDto.RefreshTokenRequest body) {
        return authService.refreshToken(pb.Auth.RefreshTokenRequest.newBuilder()
                .setRefreshToken(body.refreshToken())
                .build())
                .map(proto -> Response.ok(AuthDto.RefreshTokenResponse.from(proto)).build());
    }

    @GET
    @Path("/me")
    @RolesAllowed({ "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER" })
    @Operation(summary = "Get current logged-in user profile")
    public Uni<Response> getMe(@QueryParam("userId") int userId) {
        return authService.getMe(pb.Auth.GetMeRequest.newBuilder()
                .setUserId(userId)
                .build())
                .map(proto -> Response.ok(AuthDto.GetMeResponse.from(proto)).build());
    }
}
