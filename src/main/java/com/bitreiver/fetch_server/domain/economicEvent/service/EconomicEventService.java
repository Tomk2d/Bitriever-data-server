package com.bitreiver.fetch_server.domain.economicEvent.service;

public interface EconomicEventService {
    int fetchAndSaveMonthlyData(String yearMonth);

    int fetchAndSaveAllMonthlyData();
}
