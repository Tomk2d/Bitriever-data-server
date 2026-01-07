package com.bitreiver.fetch_server.domain.economicIndex.enums;

public enum EconomicIndexType {
    KOSPI("^KS11"),
    KOSDAQ("^KQ11"),
    NASDAQ("^IXIC"),
    S_P_500("^GSPC"), 
    DOW_JONES("^DJI"),
    USD_KRW("KRW=X");
    
    private final String symbol;

    EconomicIndexType(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
