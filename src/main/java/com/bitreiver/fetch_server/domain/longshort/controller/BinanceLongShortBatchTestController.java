package com.bitreiver.fetch_server.domain.longshort.controller;

import com.bitreiver.fetch_server.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Slf4j
@RestController
@RequiredArgsConstructor
@Profile("test")
@RequestMapping("/test/batch/binance")
public class BinanceLongShortBatchTestController {

    private static final Set<String> ALLOWED_PERIODS = Set.of("1h", "4h", "12h", "1d");

    private final JobLauncher jobLauncher;

    @Qualifier("binanceLongShortRatioJob")
    private final Job binanceLongShortRatioJob;

    @PostMapping("/long-short-ratio")
    public ResponseEntity<ApiResponse<String>> triggerBinanceLongShortRatio(
            @RequestParam("period") String period,
            @RequestParam(value = "limit", required = false) Long limit
    ) {
        if (!ALLOWED_PERIODS.contains(period)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(
                            "INVALID_PERIOD",
                            "허용되지 않은 period 값입니다. 허용된 값: " + String.join(", ", ALLOWED_PERIODS)
                    ));
        }

        Long finalLimit = (limit != null) ? limit : 30L;

        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("period", period)
                    .addLong("limit", finalLimit)
                    .toJobParameters();

            jobLauncher.run(binanceLongShortRatioJob, jobParameters);

            String message = String.format(
                    "Binance Long/Short Ratio 배치를 수동 실행했습니다. period=%s, limit=%d",
                    period, finalLimit
            );
            log.info(message);

            return ResponseEntity.ok(ApiResponse.success(message));
        } catch (Exception e) {
            log.error("Binance Long/Short Ratio 수동 배치 실행 실패 - period: {}, error: {}", period, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(
                            "BATCH_EXECUTION_FAILED",
                            "배치 실행 중 오류가 발생했습니다: " + e.getMessage()
                    ));
        }
    }
}


