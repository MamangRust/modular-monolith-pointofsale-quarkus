package com.sanedge.gateway.resource;

import java.util.List;
import java.util.stream.Collectors;

import com.sanedge.common.chaos.ChaosManager;
import com.sanedge.common.chaos.ChaosPolicy;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Chaos control plane (Fase 13). Reads the gateway-local {@link ChaosManager}
 * (hot-reloaded from {@code chaos.yaml} every 5s) and exposes policy listing,
 * halt and reload. Kafka policies are evaluated per service inside each
 * service's {@code KafkaService} (drop/reject/latency) and are managed by
 * editing {@code chaos.yaml} — the gateway instance only controls gateway-local
 * (HTTP/SQL) chaos.
 */
@Path("/api/chaos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChaosResource {

    @Inject
    ChaosManager chaosManager;

    @GET
    @Path("/policies")
    public Uni<Response> policies() {
        List<PolicyView> views = chaosManager.getPolicies().stream()
                .map(p -> new PolicyView(p.getName(), p.getType(), p.getTarget(), p.isEnabled(),
                        p.getErrorChance(), p.getLatencyMs(), p.isDropMessage(), p.isRejectMessage()))
                .collect(Collectors.toList());
        return Uni.createFrom().item(Response.ok(new PoliciesResponse("success", views)).build());
    }

    @POST
    @Path("/halt")
    public Uni<Response> halt() {
        chaosManager.halt();
        return Uni.createFrom().item(Response.ok(new StatusResponse("success", "All chaos policies halted")).build());
    }

    @POST
    @Path("/reload")
    public Uni<Response> reload() {
        chaosManager.loadConfig();
        return Uni.createFrom().item(Response.ok(new StatusResponse("success", "Chaos config reloaded")).build());
    }

    public record PolicyView(String name, String type, String target, boolean enabled,
            double errorChance, long latencyMs, boolean dropMessage, boolean rejectMessage) {
    }

    public record PoliciesResponse(String status, List<PolicyView> data) {
    }

    public record StatusResponse(String status, String message) {
    }
}
