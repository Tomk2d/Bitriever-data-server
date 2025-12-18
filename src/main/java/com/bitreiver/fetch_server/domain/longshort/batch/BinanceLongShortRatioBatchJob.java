package com.bitreiver.fetch_server.domain.longshort.batch;

import com.bitreiver.fetch_server.domain.longshort.service.LongShortRatioService;
import com.bitreiver.fetch_server.global.cache.RedisCacheService;
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

import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor

public class BinanceLongShortRatioBatchJob {
    private final LongShortRatioService longShortRatioService;
    private final RedisCacheService redisCacheService;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Tasklet binanceLongShortRatioTasklet() {
        return (contribution, chunkContext) -> {
            Map<String, Object> params = chunkContext.getStepContext().getJobParameters();

            String period = (String) params.get("period");
            Long limit = params.containsKey("limit")
                    ? Long.valueOf(params.get("limit").toString())
                    : 30L;

            log.info("Binance Long/Short Ratio 배치 실행 - period: {}, limit: {}", period, limit);

            Map<String, Object> result = longShortRatioService.fetchAll(period, limit);

            // TTL 설정 (현재는 period 주기와 거의 동일하게 설정 + 약간의 여유 시간)
            long ttlSeconds = switch (period) {
                case "1h" -> 3600L;          // 1시간
                case "4h" -> 4 * 3600L;      // 4시간
                case "12h" -> 12 * 3600L;    // 12시간
                case "1d" -> 24 * 3600L;     // 1일
                default -> 3600L;
            } + 600L; // 10분 여유 시간

            // 심볼 + period 단위로 Redis에 저장
            @SuppressWarnings("unchecked")
            Map<String, java.util.List<com.bitreiver.fetch_server.infra.binance.dto.BinanceLongShortRatioResponse>> data =
                    (Map<String, java.util.List<com.bitreiver.fetch_server.infra.binance.dto.BinanceLongShortRatioResponse>>) result.get("data");

            if (data == null || data.isEmpty()) {
                log.warn("Binance Long/Short Ratio 데이터가 비어 있습니다. period: {}", period);
            } else {
                data.forEach((symbol, ratios) -> {
                    String redisKey = "binance:longShortRatio:" + symbol + ":" + period;
                    redisCacheService.set(redisKey, ratios, ttlSeconds);
                    log.info("Binance Long/Short Ratio Redis 저장 완료 - key: {}, size: {}, ttl: {}초",
                            redisKey, ratios.size(), ttlSeconds);
                });
            }

            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step binanceLongShortRatioStep() {
        return new StepBuilder("binanceLongShortRatioStep", jobRepository)
                .tasklet(binanceLongShortRatioTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Job binanceLongShortRatioJob() {
        return new JobBuilder("binanceLongShortRatioJob", jobRepository)
                .start(binanceLongShortRatioStep())
                .build();
    }


}
