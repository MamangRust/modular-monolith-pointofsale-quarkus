package com.sanedge.gateway.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for listing transactions by card number with pagination and search")
public class FindTransactionsByCardNumberRequest {
    @NotBlank
    @Parameter(description = "Card number", example = "1234567890123456")
    private String cardNumber;

    @Min(1)
    @Parameter(description = "Page number", example = "1")
    private Integer page = 1;

    @Min(1)
    @Parameter(description = "Page size", example = "20")
    private Integer size = 20;

    @Parameter(description = "Search keyword", example = "")
    private String search = "";
}
