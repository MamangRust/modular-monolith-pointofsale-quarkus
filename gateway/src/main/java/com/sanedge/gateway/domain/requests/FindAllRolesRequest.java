package com.sanedge.gateway.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for listing all roles with pagination and search")
public class FindAllRolesRequest {
    @Parameter(description = "Search keyword", example = "")
    private String search = "";

    @Min(1)
    @Parameter(description = "Page number", example = "1")
    private Integer page = 1;

    @Min(1)
    @Parameter(description = "Page size", example = "20")
    private Integer size = 20;
}
