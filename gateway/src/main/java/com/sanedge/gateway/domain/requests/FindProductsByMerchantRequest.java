package com.sanedge.gateway.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for listing products by merchant with filters")
public class FindProductsByMerchantRequest {
    @NotNull
    @Parameter(description = "Merchant ID", example = "1")
    private Integer merchantId;

    @Parameter(description = "Search keyword", example = "")
    private String search = "";

    @Parameter(description = "Category ID filter", example = "1")
    private Integer categoryId;

    @Parameter(description = "Minimum price", example = "0")
    private Integer minPrice;

    @Parameter(description = "Maximum price", example = "1000000")
    private Integer maxPrice;

    @Min(1)
    @Parameter(description = "Page number", example = "1")
    private Integer page = 1;

    @Min(1)
    @Parameter(description = "Page size", example = "20")
    private Integer size = 20;
}
