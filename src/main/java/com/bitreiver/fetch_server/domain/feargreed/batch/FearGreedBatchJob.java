package com.bitreiver.fetch_server.domain.feargreed.batch;

import com.bitreiver.fetch_server.domain.feargreed.service.FearGreedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FearGreedBatchJob {

    private final FearGreedService fearGreedService;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 공포/탐욕 지수 최근 데이터 조회 Tasklet
     */
    @Bean
    public Tasklet fetchRecentFearGreedDataTasklet() {
        return (contribution, chunkContext) -> {
            fearGreedService.fetchRecentDataAndSaveToRedis();
            return RepeatStatus.FINISHED;
        };
    }

    /**
     * 공포/탐욕 지수 최근 데이터 조회 Step
     */
    @Bean
    public Step fetchRecentFearGreedDataStep() {
        return new StepBuilder("fetchRecentFearGreedDataStep", jobRepository)
            .tasklet(fetchRecentFearGreedDataTasklet(), transactionManager)
            .build();
    }

    /**
     * 공포/탐욕 지수 최근 데이터 조회 Job
     */
    @Bean
    public Job fetchRecentFearGreedDataJob() {
        return new JobBuilder("fetchRecentFearGreedDataJob", jobRepository)
            .start(fetchRecentFearGreedDataStep())
            .build();
    }

    /**
     * 공포/탐욕 지수 어제 데이터 조회 Tasklet
     */
    @Bean
    public Tasklet fetchYesterdayFearGreedDataTasklet() {
        return (contribution, chunkContext) -> {
            fearGreedService.fetchYesterdayDataAndSaveToRedis();
            return RepeatStatus.FINISHED;
        };
    }

    /**
     * 공포/탐욕 지수 어제 데이터 조회 Step
     */
    @Bean
    public Step fetchYesterdayFearGreedDataStep() {
        return new StepBuilder("fetchYesterdayFearGreedDataStep", jobRepository)
            .tasklet(fetchYesterdayFearGreedDataTasklet(), transactionManager)
            .build();
    }

    /**
     * 공포/탐욕 지수 어제 데이터 조회 Job
     */
    @Bean
    public Job fetchYesterdayFearGreedDataJob() {
        return new JobBuilder("fetchYesterdayFearGreedDataJob", jobRepository)
            .start(fetchYesterdayFearGreedDataStep())
            .build();
    }
}