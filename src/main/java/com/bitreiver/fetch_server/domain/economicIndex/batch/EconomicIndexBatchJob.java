package com.bitreiver.fetch_server.domain.economicIndex.batch;

import com.bitreiver.fetch_server.domain.economicIndex.service.EconomicIndexService;
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
public class EconomicIndexBatchJob {
    private final EconomicIndexService economicIndexService;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Tasklet fetchEconomicIndicesTasklet() {
        return (contribution, chunkContext) -> {
            economicIndexService.fetchAndCacheAll();
            return RepeatStatus.FINISHED;
        };
    }
    
    @Bean
    public Step fetchEconomicIndicesStep() {
        return new StepBuilder("fetchEconomicIndicesStep", jobRepository)
            .tasklet(fetchEconomicIndicesTasklet(), transactionManager)
            .build();
    }
    
    @Bean
    public Job fetchEconomicIndicesJob() {
        return new JobBuilder("fetchEconomicIndicesJob", jobRepository)
            .start(fetchEconomicIndicesStep())
            .build();
    }
}
