package com.bitreiver.fetch_server.domain.longshort.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.data.redis.password=bitriever0320",
    "spring.data.redis.replica.host=localhost",
    "spring.data.redis.replica.port=6379"
})
class BinanceLongShortBatchTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("수동 배치 트리거 API - 1h period 성공 호출")
    void triggerBinanceLongShortRatio_1h_success() throws Exception {
        mockMvc.perform(
                        post("/test/batch/binance/long-short-ratio")
                                .param("period", "1h")
                                .param("limit", "30")
                )
                .andExpect(status().isOk());
        
        mockMvc.perform(
                    post("/test/batch/binance/long-short-ratio")
                        .param("period", "4h")
                        .param("limit", "30")
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                    post("/test/batch/binance/long-short-ratio")
                        .param("period", "12h")
                        .param("limit", "30")
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                    post("/test/batch/binance/long-short-ratio")
                        .param("period", "1d")
                        .param("limit", "30")
                )
                .andExpect(status().isOk());
    }
}


