package com.bitreiver.fetch_server.global.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

    /**
     * Spring Batch 스키마 자동 초기화
     * Spring Boot 3.x에서는 자동 초기화가 제대로 작동하지 않을 수 있어 수동으로 설정
     * 스키마 파일은 Spring Batch JAR 내부에 포함되어 있습니다.
     */
    @Bean
    public DataSourceInitializer batchDataSourceInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        // Spring Batch 5.x 스키마 파일 경로
        Resource schemaResource = new ClassPathResource("org/springframework/batch/core/schema-postgresql.sql");
        populator.addScript(schemaResource);
        populator.setContinueOnError(true); // 테이블이 이미 존재하면 에러 무시
        populator.setIgnoreFailedDrops(true);
        
        initializer.setDatabasePopulator(populator);
        initializer.setEnabled(true); // 명시적으로 활성화
        return initializer;
    }

    /**
     * 비동기 JobLauncher 설정
     * 배치 작업이 메인 스레드를 블로킹하지 않도록 구성
     */
    @Bean
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setTaskExecutor(new SimpleAsyncTaskExecutor());
        jobLauncher.afterPropertiesSet();
        return jobLauncher;
    }
}

