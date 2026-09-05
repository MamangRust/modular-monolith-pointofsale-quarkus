package com.sanedge.gateway.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.ws.rs.core.Response;

class GrpcExceptionMapperTest {

    private final GrpcExceptionMapper mapper = new GrpcExceptionMapper();

    @Test
    void mapsResourceExhaustedToHttp429() {
        StatusRuntimeException ex = Status.RESOURCE_EXHAUSTED
                .withDescription("Rate limit exceeded")
                .asRuntimeException();

        Response response = mapper.toResponse(ex);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void mapsNotFoundToHttp404() {
        StatusRuntimeException ex = Status.NOT_FOUND
                .withDescription("Order not found")
                .asRuntimeException();

        Response response = mapper.toResponse(ex);

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void mapsUnavailableToHttp503() {
        StatusRuntimeException ex = Status.UNAVAILABLE
                .withDescription("service down")
                .asRuntimeException();

        Response response = mapper.toResponse(ex);

        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void usesCodeNameWhenDescriptionMissing() {
        StatusRuntimeException ex = Status.RESOURCE_EXHAUSTED.asRuntimeException();

        Response response = mapper.toResponse(ex);
        String body = response.getEntity().toString();

        assertThat(body).contains("RESOURCE_EXHAUSTED");
    }

    @Test
    void fallsBackToHttp500ForUnknownCode() {
        StatusRuntimeException ex = Status.UNKNOWN.asRuntimeException();

        Response response = mapper.toResponse(ex);

        assertThat(response.getStatus()).isEqualTo(500);
    }
}
