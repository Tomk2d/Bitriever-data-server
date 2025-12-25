package com.bitreiver.fetch_server.domain.article.service;

import java.util.List;

import com.bitreiver.fetch_server.infra.news.dto.NewsCrawlerDto;

public interface ArticleService {
    public int saveArticles(List<NewsCrawlerDto> articles);
    public List<NewsCrawlerDto> filterNewArticles(List<NewsCrawlerDto> articles);

}
