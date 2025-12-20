package com.bitreiver.fetch_server.global.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.bitreiver.fetch_server.domain.longshort.batch.BinanceLongShortRatioBatchJob;

@Slf4j
@Component
public class BatchScheduler {
    
    private final JobLauncher jobLauncher;
    private final Job fetchRecentFearGreedDataJob;
    private final Job fetchYesterdayFearGreedDataJob;
    private final Job binanceLongShortRatioJob;
    
    public BatchScheduler(
            @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
            @Qualifier("fetchRecentFearGreedDataJob") Job fetchRecentFearGreedDataJob,
            @Qualifier("fetchYesterdayFearGreedDataJob") Job fetchYesterdayFearGreedDataJob,
            @Qualifier("binanceLongShortRatioJob") Job binanceLongShortRatioJob) {
        this.jobLauncher = jobLauncher;
        this.fetchRecentFearGreedDataJob = fetchRecentFearGreedDataJob;
        this.fetchYesterdayFearGreedDataJob = fetchYesterdayFearGreedDataJob;
        this.binanceLongShortRatioJob = binanceLongShortRatioJob;
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

    /**
     * 공포/탐욕 지수 어제 데이터 조회 배치
     * UTC 9시 03분마다 실행
     */
    @Scheduled(cron = "0 3 9 * * *")
    public void scheduleFetchYesterdayFearGreedData() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
            
            jobLauncher.run(fetchYesterdayFearGreedDataJob, jobParameters);
        } catch (Exception e) {
            log.error("공포/탐욕 지수 어제 데이터 조회 배치 작업 실행 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * Binance Long/Short Ratio 배치
     * 1시간, 4시간, 12시간, 1일 마다 실행
     * 롱숏 비율 공통 수행 메서드
     */
    private void runBinanceLongShortJob(String period, long limit) {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("period", period)
                    .addLong("limit", limit)
                    .toJobParameters();

            jobLauncher.run(binanceLongShortRatioJob, jobParameters);
        } catch (Exception e) {
            log.error("Binance Long/Short Ratio 배치 실행 실패 - period: {}, error: {}",
                    period, e.getMessage(), e);
        }
    }

    /**
     * 1시간마다 period=1h 실행
     * 매 정시: 0분 0초
     */
    @Scheduled(cron = "0 0 * * * *")
    public void scheduleBinanceLongShort1h() {
        runBinanceLongShortJob("1h", 30L);
    }

    /**
     * 4시간마다 period=4h 실행 (0시10분, 4시10분, 8시10분, 12시10분, 16시10분, 20시10분)
     */
    @Scheduled(cron = "0 10 */4 * * *")
    public void scheduleBinanceLongShort4h() {
        runBinanceLongShortJob("4h", 30L);
    }

    /**
     * 12시간마다 period=12h 실행 (0시20분, 12시20분)
     */
    @Scheduled(cron = "0 20 */12 * * *")
    public void scheduleBinanceLongShort12h() {
        runBinanceLongShortJob("12h", 30L);
    }

    /**
     * 하루마다 period=1d 실행 (매일 0시)
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void scheduleBinanceLongShort1d() {
        runBinanceLongShortJob("1d", 30L);
    }
}