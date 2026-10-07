package com.hanalyser.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_analysis_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAnalysisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String ticker;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "current_price")
    private Double currentPrice;

    @Column(name = "fair_value")
    private Double fairValue;

    @Column(name = "intrinsic_value")
    private Double intrinsicValue;

    @Column(name = "margin_of_safety")
    private Double marginOfSafety;

    @Column(name = "pe_ratio")
    private Double peRatio;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(length = 20)
    private String valuation;

    @Column(length = 10)
    private String recommendation;

    @Column(name = "buy_below")
    private Double buyBelow;

    @Column(name = "sell_above")
    private Double sellAbove;

    @Column(name = "hold_until")
    private Double holdUntil;

    @Column
    private Integer confidence;

    @Column(name = "analyzed_at")
    @Builder.Default
    private LocalDateTime analyzedAt = LocalDateTime.now();
}

