package com.bitreiver.fetch_server.global.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BatchScheduler {
    
    private final JobLauncher jobLauncher;
    private final Job fetchRecentFearGreedDataJob;
    
    public BatchScheduler(
            @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
            @Qualifier("fetchRecentFearGreedDataJob") Job fetchRecentFearGreedDataJob) {
        this.jobLauncher = jobLauncher;
        this.fetchRecentFearGreedDataJob = fetchRecentFearGreedDataJob;
    }
    
    /**
     * 공포/탐욕 지수 최근 데이터 조회 배치
     * 5분마다 실행 (300,000ms = 5분)
     */
    @Scheduled(fixedRate = 300000)
    public void scheduleFetchRecentFearGreedData() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
            
            jobLauncher.run(fetchRecentFearGreedDataJob, jobParameters);
                        
        } catch (Exception e) {
            log.error("공포/탐욕 지수 최근 데이터 조회 배치 작업 실행 실패: {}", e.getMessage(), e);
        }
    }
}