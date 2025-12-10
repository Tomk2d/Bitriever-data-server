package com.bitreiver.fetch_server.domain.feargreed.dto;

import java.time.LocalDate;

import com.bitreiver.fetch_server.domain.feargreed.entity.FearGreedIndex;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "공포/탐욕 지수 API 응답")
public class FearGreedApiResponse {
    @Schema(description = "공포/탐욕 지수 ID", example = "1")
    private Integer id;
    
    @Schema(description = "날짜", example = "2025-12-09")
    private LocalDate date;
    
    @Schema(description = "공포/탐욕 지수 값 (0-100)", example = "22")
    private Integer value;

    public static FearGreedApiResponse from(FearGreedIndex index) {
        return FearGreedApiResponse.builder()
            .id(index.getId())
            .date(index.getDate())
            .value(index.getValue())            
            .build();
    }
}
