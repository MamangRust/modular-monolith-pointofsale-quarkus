package com.sanedge.gateway.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for transaction status failed by card number, year and month")
public class GetTransactionStatusFailedByCardNumberRequest {
    @NotBlank
    @Parameter(description = "Card number", example = "1234567890123456")
    private String cardNumber;

    @NotNull
    @Parameter(description = "Year", example = "2026")
    private Integer year;

    @NotNull
    @Parameter(description = "Month", example = "6")
    private Integer month;
}
