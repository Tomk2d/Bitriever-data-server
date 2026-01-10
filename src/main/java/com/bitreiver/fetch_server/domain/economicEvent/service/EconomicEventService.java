package com.bitreiver.fetch_server.domain.economicEvent.service;

import java.util.List;
import com.bitreiver.fetch_server.domain.economicEvent.entity.EconomicEvent;

public interface EconomicEventService {
    int fetchAndSaveMonthlyData(String yearMonth);

    int fetchAndSaveAllMonthlyData();

    List<EconomicEvent> getUpcomingEvents(int limit);

    void cacheUpcomingEvents(int limit);
}
