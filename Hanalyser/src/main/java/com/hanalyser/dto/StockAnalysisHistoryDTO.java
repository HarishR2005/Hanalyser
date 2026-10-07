package com.hanalyser.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StockAnalysisHistoryDTO {
    private Long id;
    private String ticker;
    private String companyName;
    private Double currentPrice;
    private Double fairValue;
    private Double intrinsicValue;
    private Double marginOfSafety;
    private Double peRatio;
    private Double riskScore;
    private String valuation;
    private String recommendation;
    private Double buyBelow;
    private Double sellAbove;
    private Double holdUntil;
    private Integer confidence;
    private LocalDateTime analyzedAt;
}

