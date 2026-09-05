package com.sanedge.transaction.domain.requests;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "FindTransactionMonthMerchantRange", description = "Request for transaction monthly stats range by merchant ID")
public class FindTransactionMonthMerchantRange {
    @NotNull
    @Parameter(description = "Merchant ID", example = "1")
    private Long merchantId;

    @NotNull
    @Parameter(description = "Start year", example = "2025")
    private Integer startYear;

    @NotNull
    @Parameter(description = "Start month", example = "1")
    private Integer startMonth;

    @NotNull
    @Parameter(description = "End year", example = "2025")
    private Integer endYear;

    @NotNull
    @Parameter(description = "End month", example = "12")
    private Integer endMonth;
}
