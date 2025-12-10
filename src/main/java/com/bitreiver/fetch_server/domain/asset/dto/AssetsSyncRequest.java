package com.bitreiver.fetch_server.domain.asset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
@Schema(description = "자산 동기화 요청")
public class AssetsSyncRequest {
    @Schema(description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
    @NotBlank(message = "사용자 ID는 필수입니다.")
    @JsonProperty("user_id")
    private String userId;
}

