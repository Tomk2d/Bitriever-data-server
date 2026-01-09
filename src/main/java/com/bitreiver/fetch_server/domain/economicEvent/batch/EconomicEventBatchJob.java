package com.bitreiver.fetch_server.domain.economicEvent.batch;

import com.bitreiver.fetch_server.domain.economicEvent.service.EconomicEventService;
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
public class EconomicEventBatchJob {
    private final EconomicEventService economicEventService;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Tasklet fetchEconomicEventsTasklet() {
        return (contribution, chunkContext) -> {
            int totalSaved = economicEventService.fetchAndSaveAllMonthlyData();
            log.info("경제 지표 이벤트 배치 작업 완료: 총 저장={}", totalSaved);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step fetchEconomicEventsStep() {
        return new StepBuilder("fetchEconomicEventsStep", jobRepository)
            .tasklet(fetchEconomicEventsTasklet(), transactionManager)
            .build();
    }
    
    @Bean
    public Job fetchEconomicEventsJob() {
        return new JobBuilder("fetchEconomicEventsJob", jobRepository)
            .start(fetchEconomicEventsStep())
            .build();
    }
}
