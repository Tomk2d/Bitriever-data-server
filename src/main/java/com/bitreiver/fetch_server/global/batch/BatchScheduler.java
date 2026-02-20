package com.bitreiver.fetch_server.global.batch;

import com.bitreiver.fetch_server.domain.article.service.ArticleCrawlerService;
import com.bitreiver.fetch_server.domain.coin.service.CoinImageService;
import com.bitreiver.fetch_server.domain.coin.service.CoinService;
import com.bitreiver.fetch_server.domain.coinone.service.CoinoneService;
import com.bitreiver.fetch_server.domain.upbit.service.UpbitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class BatchScheduler {
    
    private final JobLauncher jobLauncher;
    private final Job fetchRecentFearGreedDataJob;
    private final Job fetchYesterdayFearGreedDataJob;
    private final Job fetchRecentFearGreedToDbJob;
    private final Job binanceLongShortRatioJob;
    private final Job fetchEconomicIndicesJob;
    private final Job fetchEconomicEventsJob;
    private final UpbitService upbitService;
    private final CoinoneService coinoneService;
    private final CoinService coinService;
    private final CoinImageService coinImageService;
    private final ArticleCrawlerService articleCrawlerService;

    
    public BatchScheduler(
            @Qualifier("asyncJobLauncher") JobLauncher jobLauncher,
            @Qualifier("fetchRecentFearGreedDataJob") Job fetchRecentFearGreedDataJob,
            @Qualifier("fetchYesterdayFearGreedDataJob") Job fetchYesterdayFearGreedDataJob,
            @Qualifier("fetchRecentFearGreedToDbJob") Job fetchRecentFearGreedToDbJob,
            @Qualifier("binanceLongShortRatioJob") Job binanceLongShortRatioJob,
            @Qualifier("fetchEconomicIndicesJob") Job fetchEconomicIndicesJob,
            @Qualifier("fetchEconomicEventsJob") Job fetchEconomicEventsJob,
            UpbitService upbitService,
            CoinoneService coinoneService,
            CoinService coinService,
            CoinImageService coinImageService,
            ArticleCrawlerService articleCrawlerService) {
        this.jobLauncher = jobLauncher;
        this.fetchRecentFearGreedDataJob = fetchRecentFearGreedDataJob;
        this.fetchYesterdayFearGreedDataJob = fetchYesterdayFearGreedDataJob;
        this.fetchRecentFearGreedToDbJob = fetchRecentFearGreedToDbJob;
        this.binanceLongShortRatioJob = binanceLongShortRatioJob;
        this.fetchEconomicIndicesJob = fetchEconomicIndicesJob;
        this.fetchEconomicEventsJob = fetchEconomicEventsJob;
        this.upbitService = upbitService;
        this.coinoneService = coinoneService;
        this.coinService = coinService;
        this.coinImageService = coinImageService;
        this.articleCrawlerService = articleCrawlerService;
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
     * 공포/탐욕 지수 어제 데이터 조회 배치 (Redis)
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
     * 공포/탐욕 지수 DB 증분 저장 배치
     * UTC 00:15에 실행 - 일주일치 데이터 중 DB에 없는 날짜만 저장
     */
    @Scheduled(cron = "0 15 0 * * *", zone = "UTC")
    public void scheduleFetchFearGreedToDb() {
        try {
            log.info("공포/탐욕 지수 DB 증분 저장 배치 시작");
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
            
            jobLauncher.run(fetchRecentFearGreedToDbJob, jobParameters);
        } catch (Exception e) {
            log.error("공포/탐욕 지수 DB 증분 저장 배치 작업 실행 실패: {}", e.getMessage(), e);
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
     * 매 정시: 1분 0초
     */
    @Scheduled(cron = "0 7 * * * *")
    public void scheduleBinanceLongShort1h() {
        runBinanceLongShortJob("1h", 30L);
    }

    /**
     * 4시간마다 period=4h 실행 (0시7분, 4시7분, 8시7분, 12시7분, 16시7분, 20시7분)
     */
    @Scheduled(cron = "0 7 */4 * * *")
    public void scheduleBinanceLongShort4h() {
        runBinanceLongShortJob("4h", 30L);
    }

    /**
     * 12시간마다 period=12h 실행 (0시7분, 12시7분)
     */
    @Scheduled(cron = "0 7 */12 * * *")
    public void scheduleBinanceLongShort12h() {
        runBinanceLongShortJob("12h", 30L);
    }

    /**
     * 하루마다 period=1d 실행 (매일 0시 1분)
     */
    @Scheduled(cron = "0 1 0 * * *")
    public void scheduleBinanceLongShort1d() {
        runBinanceLongShortJob("1d", 30L);
    }

    /**
     * 경제 지표 수집 배치
     * 10분마다 실행 (600,000ms = 10분)
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void scheduleFetchEconomicIndices() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
            
            jobLauncher.run(fetchEconomicIndicesJob, jobParameters);
        } catch (Exception e) {
            log.error("경제 지표 수집 배치 작업 실행 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 경제 지표 이벤트 수집 배치
     * 매일 한국시간 00:10:00에 실행 (하루 1회)
     * 2026-01부터 현재 달 +2개월까지 수집
     * 각 요청 사이에 1초 딜레이 적용
     */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void scheduleFetchEconomicEvents() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
            
            jobLauncher.run(fetchEconomicEventsJob, jobParameters);
        } catch (Exception e) {
            log.error("경제 지표 이벤트 수집 배치 작업 실행 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 업비트 코인 종목 패치 배치
     * UTC 00:01:00에 실행 (하루 1회). 성공 시 일봉 동기화를 체인으로 실행.
     */
    @Async
    @Scheduled(cron = "0 1 0 * * *", zone = "UTC")
    public void scheduleUpbitCoinListFetch() {
        try {
            log.info("업비트 코인 종목 패치 배치 작업 시작");
            
            List<Map<String, Object>> savedList = upbitService.fetchAllCoinList()
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
                .doOnNext(fetchedCoinList -> {
                    if (fetchedCoinList == null || fetchedCoinList.isEmpty()) {
                        log.warn("업비트 코인 목록이 비어있습니다.");
                        return;
                    }
                    
                    // 코인 목록 저장
                    Map<String, Object> result = coinService.saveAllCoinList(fetchedCoinList);
                    log.info("업비트 코인 목록 저장 완료: {}", result);
                    
                    // 아이콘 다운로드
                    try {
                        int downloadedCount = coinImageService.downloadCoinImages(fetchedCoinList);
                        log.info("업비트 코인 아이콘 다운로드 완료: {}개", downloadedCount);
                    } catch (Exception e) {
                        log.warn("업비트 코인 아이콘 다운로드 중 오류 발생: {}", e.getMessage());
                    }
                })
                .doOnError(error -> log.error("업비트 코인 종목 패치 배치 작업 실패: {}", error.getMessage(), error))
                .map(list -> list != null ? list : Collections.<Map<String, Object>>emptyList())
                .block(Duration.ofMinutes(10)); // 최대 10분 대기
            
            if (savedList != null && !savedList.isEmpty()) {
                log.info("업비트 일봉 동기화 시작 (종목 패치 성공 후 체인)");
                try {
                    upbitService.syncAllCoinDailyCandles().block(Duration.ofMinutes(30));
                    log.info("업비트 일봉 동기화 완료");
                } catch (Exception e) {
                    log.error("업비트 일봉 동기화 실패: {}", e.getMessage(), e);
                }
            }
            
            log.info("업비트 코인 종목 패치 배치 작업 완료");
        } catch (Exception e) {
            log.error("업비트 코인 종목 패치 배치 작업 실행 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 코인원 코인 종목 패치 배치
     * UTC 00:03:00에 실행 (하루 1회). 성공 시 일봉 동기화를 체인으로 실행.
     */
    @Async
    @Scheduled(cron = "0 3 0 * * *", zone = "UTC")
    public void scheduleCoinoneCoinListFetch() {
        try {
            log.info("코인원 코인 종목 패치 배치 작업 시작");
            
            List<Map<String, Object>> savedList = coinoneService.fetchAllCoinList()
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
                .doOnNext(fetchedCoinList -> {
                    if (fetchedCoinList == null || fetchedCoinList.isEmpty()) {
                        log.warn("코인원 코인 목록이 비어있습니다.");
                        return;
                    }
                    
                    // 코인 목록 저장 (업비트에 없는 종목만 저장)
                    Map<String, Object> result = coinService.saveAllCoinList(fetchedCoinList);
                    log.info("코인원 코인 목록 저장 완료: {}", result);
                    
                    // 아이콘 다운로드
                    try {
                        int downloadedCount = coinImageService.downloadCoinImages(fetchedCoinList);
                        log.info("코인원 코인 아이콘 다운로드 완료: {}개", downloadedCount);
                    } catch (Exception e) {
                        log.warn("코인원 코인 아이콘 다운로드 중 오류 발생: {}", e.getMessage());
                    }
                })
                .doOnError(error -> log.error("코인원 코인 종목 패치 배치 작업 실패: {}", error.getMessage(), error))
                .map(list -> list != null ? list : Collections.<Map<String, Object>>emptyList())
                .block(Duration.ofMinutes(10)); // 최대 10분 대기
            
            if (savedList != null && !savedList.isEmpty()) {
                log.info("코인원 일봉 동기화 시작 (종목 패치 성공 후 체인)");
                try {
                    coinoneService.syncAllCoinDailyCandles().block(Duration.ofMinutes(30));
                    log.info("코인원 일봉 동기화 완료");
                } catch (Exception e) {
                    log.error("코인원 일봉 동기화 실패: {}", e.getMessage(), e);
                }
            }
            
            log.info("코인원 코인 종목 패치 배치 작업 완료");
        } catch (Exception e) {
            log.error("코인원 코인 종목 패치 배치 작업 실행 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 블록미디어 기사 증분 크롤링 배치
     * 10분마다 실행 - DB에 저장된 기사 이후의 새 기사만 수집
     * 중복 방지: publisherType + articleId 또는 originalUrl로 체크
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void scheduleBlockMediaArticleCrawl() {
        log.info("블록미디어 기사 증분 크롤링 배치 시작");
        try {
            Integer savedCount = articleCrawlerService.crawlNewArticles()
                .block(Duration.ofMinutes(30)); // 최대 30분 대기
            log.info("블록미디어 기사 증분 크롤링 배치 완료: 저장된 기사 수={}", savedCount);
        } catch (Exception e) {
            log.error("블록미디어 기사 증분 크롤링 배치 실패: {}", e.getMessage(), e);
        }
    }
}