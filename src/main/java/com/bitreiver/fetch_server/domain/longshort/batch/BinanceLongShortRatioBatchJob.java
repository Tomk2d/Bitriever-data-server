package com.bitreiver.fetch_server.domain.longshort.batch;

import com.bitreiver.fetch_server.domain.longshort.service.LongShortRatioService;
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

            longShortRatioService.fetchAllAndSaveToRedis(period, limit);

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
