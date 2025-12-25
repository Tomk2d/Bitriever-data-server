package com.bitreiver.fetch_server.infra.news.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsCrawlerDto {
    private Integer articleId;
    private String headline;
    private String summary;
    private String originalUrl;
    private String reporterName;
    private String publisherName;
    private Integer publisherType;
    private LocalDateTime publishedAt;
}
