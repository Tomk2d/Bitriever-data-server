package com.bitreiver.fetch_server.domain.asset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
@Schema(description = "비동기 자산 동기화 요청")
public class AssetsSyncAsyncRequest {
    @Schema(description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
    @NotBlank(message = "사용자 ID는 필수입니다.")
    @JsonProperty("user_id")
    private String userId;
    
    @Schema(description = "완료 시 호출할 콜백 URL (app-server 기준 상대 경로)", 
            example = "/api/callback/sync-complete", required = true)
    @NotBlank(message = "콜백 URL은 필수입니다.")
    @JsonProperty("callback_url")
    private String callbackUrl;
}
