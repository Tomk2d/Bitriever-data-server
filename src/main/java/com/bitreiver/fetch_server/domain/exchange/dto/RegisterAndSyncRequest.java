package com.bitreiver.fetch_server.domain.exchange.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "거래소 자격인증 등록 및 연동 비동기 요청")
public class RegisterAndSyncRequest {
    @Schema(description = "사용자 UUID", required = true)
    @NotBlank(message = "user_id는 필수입니다.")
    @JsonProperty("user_id")
    private String userId;

    @Schema(description = "거래소 타입 코드 (1=업비트, 2=빗썸, 3=코인원)", required = true)
    @NotNull(message = "거래소는 필수입니다.")
    @JsonProperty("exchange_provider")
    private Short exchangeProvider;

    @Schema(description = "Access Key", required = true)
    @NotBlank(message = "Access Key는 필수입니다.")
    @JsonProperty("access_key")
    private String accessKey;

    @Schema(description = "Secret Key", required = true)
    @NotBlank(message = "Secret Key는 필수입니다.")
    @JsonProperty("secret_key")
    private String secretKey;
}
