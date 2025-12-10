package com.bitreiver.fetch_server.domain.exchange.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Schema(description = "거래소 자격증명 요청")
public class ExchangeCredentialRequest {
    @Schema(description = "거래소 타입 코드", example = "1", required = true, allowableValues = {"1", "2", "3", "11", "12", "13", "14"})
    @NotNull(message = "거래소는 필수입니다.")
    private Short exchangeProvider;
    
    @Schema(description = "거래소 API Access Key", example = "your-access-key-here", required = true)
    @NotBlank(message = "Access Key는 필수입니다.")
    private String accessKey;
    
    @Schema(description = "거래소 API Secret Key", example = "your-secret-key-here", required = true)
    @NotBlank(message = "Secret Key는 필수입니다.")
    private String secretKey;
}

