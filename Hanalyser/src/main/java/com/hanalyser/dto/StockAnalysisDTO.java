package com.hanalyser.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class StockAnalysisDTO {
    private String ticker;
    private String companyName;
    private String exchange;
    private String currency;

    // Price Data
    private Double currentPrice;
    private Double previousClose;
    private Double dayHigh;
    private Double dayLow;
    private Double fiftyTwoWeekHigh;
    private Double fiftyTwoWeekLow;
    private Long volume;
    private Long marketCap;

    // Fundamental Metrics
    private Double eps;
    private Double peRatio;
    private Double forwardPE;
    private Double pbRatio;
    private Double dividendYield;
    private Double beta;
    private Double debtToEquity;
    private Double roe;
    private Double freeCashFlow;
    private Double revenueGrowth;
    private Double earningsGrowth;

    // Valuation Results
    private Double intrinsicValue;
    private Double fairValue;
    private Double grahamValue;
    private Double marginOfSafety;
    private Double riskScore;

    // Recommendation
    private String valuation;     // UNDERVALUED / OVERVALUED / FAIRLY_VALUED
    private String recommendation; // BUY / SELL / HOLD
    private Double buyBelow;
    private Double sellAbove;
    private Double holdUntil;
    private Integer confidence;

    // Chart Data (percentage breakdown for pie chart)
    private Double buyConfidence;
    private Double sellConfidence;
    private Double holdConfidence;

    // Meta
    private LocalDateTime analyzedAt;
    private String riskLevel;   // LOW / MEDIUM / HIGH
    private String analystSummary;
}

