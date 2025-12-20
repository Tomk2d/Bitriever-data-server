package com.bitreiver.fetch_server.domain.feargreed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FearGreedRedisDto {
    private LocalDate date;
    private String dateString;
    private Integer value;
}
