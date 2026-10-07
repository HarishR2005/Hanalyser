package com.hanalyser.service;

import com.hanalyser.dto.StockAnalysisDTO;
import com.hanalyser.entity.StockAnalysisHistory;
import com.hanalyser.entity.User;
import com.hanalyser.repository.StockAnalysisHistoryRepository;
import com.hanalyser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockValuationService {

    private final YahooFinanceService yahooFinanceService;
    private final StockAnalysisHistoryRepository historyRepository;
    private final UserRepository userRepository;

    private static final double DISCOUNT_RATE = 0.10; // 10% discount rate
    private static final int DCF_YEARS = 10;

    @Transactional
    public StockAnalysisDTO analyze(String ticker) {
        Map<String, Object> raw = yahooFinanceService.fetchStockData(ticker);

        double currentPrice = toDouble(raw.get("currentPrice"), 0.0);
        double eps = toDouble(raw.get("eps"), 0.0);
        double beta = toDouble(raw.get("beta"), 1.0);
        double debtToEquity = toDouble(raw.get("debtToEquity"), 0.0);
        double earningsGrowth = toDouble(raw.get("earningsGrowth"), 0.0);
        double freeCashFlow = toDouble(raw.get("freeCashFlow"), 0.0);
        double previousClose = toDouble(raw.get("previousClose"), 0.0);
        double high52 = toDouble(raw.get("fiftyTwoWeekHigh"), currentPrice);
        double low52 = toDouble(raw.get("fiftyTwoWeekLow"), currentPrice);
        double dayHigh = toDouble(raw.get("dayHigh"), currentPrice);
        double dayLow = toDouble(raw.get("dayLow"), currentPrice);
        boolean hasFundamentalValuationData = eps > 0 || freeCashFlow > 0;

        // ---- Valuation Formulae ----

        // 1. Intrinsic Value (DCF on FCF)
        double intrinsicValue = calculateIntrinsicValue(freeCashFlow, earningsGrowth, DISCOUNT_RATE);
        if (intrinsicValue <= 0 || currentPrice <= 0) {
            intrinsicValue = estimateMarketFairValue(currentPrice, previousClose, high52, low52, dayHigh, dayLow);
        }

        // Per share if needed
        double sharesEstimated = toLong(raw.get("marketCap"), 0L) > 0
                ? toLong(raw.get("marketCap"), 0L) / (currentPrice > 0 ? currentPrice : 1)
                : 0;
        double intrinsicPerShare = sharesEstimated > 0 && freeCashFlow > 0
                ? intrinsicValue / sharesEstimated
                : intrinsicValue;
        if (intrinsicPerShare <= 0) {
            intrinsicPerShare = estimateMarketFairValue(currentPrice, previousClose, high52, low52, dayHigh, dayLow);
        }

        // 2. Graham Fair Value: EPS * (8.5 + 2g) where g is growth %
        double growthPercent = earningsGrowth * 100;
        double grahamValue = eps > 0
                ? eps * (8.5 + 2 * Math.max(growthPercent, 0))
                : estimateMarketFairValue(currentPrice, previousClose, high52, low52, dayHigh, dayLow);
        if (grahamValue <= 0) {
            grahamValue = estimateMarketFairValue(currentPrice, previousClose, high52, low52, dayHigh, dayLow);
        }

        // 3. Blended Fair Value
        double marketFairValue = estimateMarketFairValue(currentPrice, previousClose, high52, low52, dayHigh, dayLow);
        double fairValue = hasFundamentalValuationData
                ? (intrinsicPerShare * 0.45 + grahamValue * 0.35 + marketFairValue * 0.20)
                : marketFairValue;
        if (fairValue <= 0 || fairValue > currentPrice * 5) {
            fairValue = marketFairValue;
        }

        // 4. Margin of Safety
        double marginOfSafety = fairValue > 0 ? ((fairValue - currentPrice) / fairValue) * 100 : 0;

        // 5. P/E Ratio
        double peRatio = toDouble(raw.get("peRatio"), 0.0);
        if (peRatio <= 0 && eps > 0) peRatio = currentPrice / eps;

        // 6. Risk Score: (Volatility + DebtRatio + Beta) / 3
        double volatility = calculateVolatility(currentPrice, high52, low52);
        double debtRatio = Math.min(debtToEquity / 100, 1.0);
        double betaNorm = Math.min(Math.abs(beta) / 2.0, 1.0);
        double riskScore = ((volatility + debtRatio + betaNorm) / 3.0) * 10;
        riskScore = Math.min(riskScore, 10);

        RecommendationResult recommendationResult =
                calculateRecommendation(
                        currentPrice,
                        fairValue,
                        previousClose,
                        high52,
                        low52,
                        peRatio,
                        earningsGrowth,
                        riskScore
                );
        String valuation = recommendationResult.valuation();
        String recommendation = recommendationResult.recommendation();
        int confidence = recommendationResult.confidence();
        double buyBelow = round(fairValue * 0.92);
        double sellAbove = round(fairValue * 1.12);
        double holdUntil = round(fairValue);

        // ---- Confidence Pie Breakdown ----
        double buyConf, sellConf, holdConf;
        if ("BUY".equals(recommendation)) {
            buyConf = confidence;
            holdConf = (100 - confidence) * 0.6;
            sellConf = (100 - confidence) * 0.4;
        } else if ("SELL".equals(recommendation)) {
            sellConf = confidence;
            holdConf = (100 - confidence) * 0.6;
            buyConf = (100 - confidence) * 0.4;
        } else {
            holdConf = confidence;
            buyConf = (100 - confidence) * 0.55;
            sellConf = (100 - confidence) * 0.45;
        }

        String riskLevel = riskScore < 3.5 ? "LOW" : riskScore < 6.5 ? "MEDIUM" : "HIGH";

        StockAnalysisDTO dto = StockAnalysisDTO.builder()
                .ticker(String.valueOf(raw.getOrDefault("ticker", ticker.toUpperCase())))
                .companyName((String) raw.get("companyName"))
                .exchange((String) raw.get("exchange"))
                .currency((String) raw.get("currency"))
                .currentPrice(round(currentPrice))
                .previousClose(round(toDouble(raw.get("previousClose"), 0.0)))
                .dayHigh(round(toDouble(raw.get("dayHigh"), 0.0)))
                .dayLow(round(toDouble(raw.get("dayLow"), 0.0)))
                .fiftyTwoWeekHigh(round(toDouble(raw.get("fiftyTwoWeekHigh"), 0.0)))
                .fiftyTwoWeekLow(round(toDouble(raw.get("fiftyTwoWeekLow"), 0.0)))
                .volume(toLong(raw.get("volume"), 0L))
                .marketCap(toLong(raw.get("marketCap"), 0L))
                .eps(round(eps))
                .peRatio(round(peRatio))
                .forwardPE(round(toDouble(raw.get("forwardPE"), 0.0)))
                .pbRatio(round(toDouble(raw.get("pbRatio"), 0.0)))
                .dividendYield(round(toDouble(raw.get("dividendYield"), 0.0) * 100))
                .beta(round(beta))
                .debtToEquity(round(debtToEquity))
                .freeCashFlow(round(freeCashFlow))
                .revenueGrowth(round(toDouble(raw.get("revenueGrowth"), 0.05) * 100))
                .earningsGrowth(round(growthPercent))
                .intrinsicValue(round(intrinsicPerShare))
                .fairValue(round(fairValue))
                .grahamValue(round(grahamValue))
                .marginOfSafety(round(marginOfSafety))
                .riskScore(round(riskScore))
                .valuation(valuation)
                .recommendation(recommendation)
                .buyBelow(buyBelow)
                .sellAbove(sellAbove)
                .holdUntil(holdUntil)
                .confidence(confidence)
                .buyConfidence(round(buyConf))
                .sellConfidence(round(sellConf))
                .holdConfidence(round(holdConf))
                .riskLevel(riskLevel)
                .analyzedAt(LocalDateTime.now())
                .analystSummary(buildSummary(ticker, valuation, recommendation, fairValue, currentPrice, high52, low52))
                .build();

        // Save to history
        saveHistory(dto);

        return dto;
    }

    private double calculateIntrinsicValue(double fcf, double growthRate, double discountRate) {
        if (fcf <= 0) return 0;
        double totalPV = 0;
        double currentFCF = fcf;
        double sustainableGrowth = Math.min(growthRate, 0.25); // cap at 25%
        for (int n = 1; n <= DCF_YEARS; n++) {
            currentFCF *= (1 + sustainableGrowth);
            totalPV += currentFCF / Math.pow(1 + discountRate, n);
            sustainableGrowth *= 0.9; // decay growth
        }
        // Terminal value (Gordon Growth)
        double terminalGrowth = 0.03;
        double terminalValue = (currentFCF * (1 + terminalGrowth)) / (discountRate - terminalGrowth);
        totalPV += terminalValue / Math.pow(1 + discountRate, DCF_YEARS);
        return totalPV;
    }

    private double calculateVolatility(double price, double high52, double low52) {
        if (high52 <= 0 || low52 <= 0) return 0.3;
        return (high52 - low52) / high52;
    }

    private RecommendationResult calculateRecommendation(
            double currentPrice,
            double fairValue,
            double previousClose,
            double high52,
            double low52,
            double peRatio,
            double earningsGrowth,
            double riskScore
    ) {
        double valuationGap = currentPrice > 0 ? (fairValue - currentPrice) / currentPrice : 0;
        double pricePosition = calculatePricePosition(currentPrice, high52, low52);
        double dayChange = previousClose > 0 ? (currentPrice - previousClose) / previousClose : 0;

        int score = 0;
        score += valuationGap >= 0.15 ? 3 : valuationGap >= 0.08 ? 2 : valuationGap >= 0.03 ? 1 : 0;
        score -= valuationGap <= -0.15 ? 3 : valuationGap <= -0.08 ? 2 : valuationGap <= -0.03 ? 1 : 0;

        score += pricePosition <= 0.25 ? 2 : pricePosition <= 0.40 ? 1 : 0;
        score -= pricePosition >= 0.82 ? 2 : pricePosition >= 0.68 ? 1 : 0;

        if (peRatio > 0) {
            score += peRatio <= 18 ? 1 : 0;
            score -= peRatio >= 45 ? 1 : 0;
        }

        score += earningsGrowth >= 0.10 ? 1 : 0;
        score -= earningsGrowth < -0.05 ? 1 : 0;
        score -= riskScore >= 7.0 ? 1 : 0;
        score -= dayChange <= -0.04 ? 1 : 0;

        String recommendation;
        if (score >= 3) {
            recommendation = "BUY";
        } else if (score <= -3) {
            recommendation = "SELL";
        } else {
            recommendation = "HOLD";
        }

        String valuation;
        if (valuationGap >= 0.08 || pricePosition <= 0.28) {
            valuation = "UNDERVALUED";
        } else if (valuationGap <= -0.08 || pricePosition >= 0.78) {
            valuation = "OVERVALUED";
        } else {
            valuation = "FAIRLY_VALUED";
        }

        int confidence = (int) Math.round(
                Math.min(94, 58 + Math.abs(score) * 7 + Math.abs(valuationGap) * 60)
        );

        return new RecommendationResult(valuation, recommendation, confidence);
    }

    private double estimateMarketFairValue(
            double currentPrice,
            double previousClose,
            double high52,
            double low52,
            double dayHigh,
            double dayLow
    ) {
        if (currentPrice <= 0) {
            return 0;
        }

        double pricePosition = calculatePricePosition(currentPrice, high52, low52);
        double rangeMidpoint = high52 > low52 ? (high52 + low52) / 2 : currentPrice;
        double dayMidpoint = dayHigh > dayLow ? (dayHigh + dayLow) / 2 : currentPrice;
        double dayChange = previousClose > 0 ? (currentPrice - previousClose) / previousClose : 0;
        double momentumAdjustment = clamp(dayChange * 2.0, -0.06, 0.06);

        double fairValue = (rangeMidpoint * 0.65 + dayMidpoint * 0.20 + currentPrice * 0.15)
                * (1 + momentumAdjustment);

        if (pricePosition <= 0.25) {
            fairValue = Math.max(fairValue, currentPrice * 1.18);
        } else if (pricePosition <= 0.40) {
            fairValue = Math.max(fairValue, currentPrice * 1.08);
        } else if (pricePosition >= 0.82) {
            fairValue = Math.min(fairValue, currentPrice * 0.86);
        } else if (pricePosition >= 0.68) {
            fairValue = Math.min(fairValue, currentPrice * 0.94);
        }

        return fairValue;
    }

    private double calculatePricePosition(double currentPrice, double high52, double low52) {
        if (currentPrice <= 0 || high52 <= low52) {
            return 0.5;
        }

        return clamp((currentPrice - low52) / (high52 - low52), 0, 1);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void saveHistory(StockAnalysisDTO dto) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            userRepository.findByEmail(email).ifPresent(user -> {
                StockAnalysisHistory history = StockAnalysisHistory.builder()
                        .user(user)
                        .ticker(dto.getTicker())
                        .companyName(dto.getCompanyName())
                        .currentPrice(dto.getCurrentPrice())
                        .fairValue(dto.getFairValue())
                        .intrinsicValue(dto.getIntrinsicValue())
                        .marginOfSafety(dto.getMarginOfSafety())
                        .peRatio(dto.getPeRatio())
                        .riskScore(dto.getRiskScore())
                        .valuation(dto.getValuation())
                        .recommendation(dto.getRecommendation())
                        .buyBelow(dto.getBuyBelow())
                        .sellAbove(dto.getSellAbove())
                        .holdUntil(dto.getHoldUntil())
                        .confidence(dto.getConfidence())
                        .build();
                historyRepository.save(history);
            });
        } catch (Exception e) {
            log.warn("Could not save analysis history: {}", e.getMessage());
        }
    }

    private String buildSummary(String ticker, String valuation, String recommendation,
                                 double fairValue, double currentPrice, double high52, double low52) {
        double gap = fairValue > 0 ? Math.abs(fairValue - currentPrice) / fairValue * 100 : 0;
        double pricePosition = calculatePricePosition(currentPrice, high52, low52) * 100;
        return switch (recommendation) {
            case "BUY" -> String.format(
                "%s is trading %.1f%% below estimated fair value and sits around the %.0fth percentile of its 52-week range. " +
                "The setup supports a BUY signal while risk remains monitored.", ticker.toUpperCase(), gap, pricePosition);
            case "SELL" -> String.format(
                "%s is trading %.1f%% above estimated fair value and sits around the %.0fth percentile of its 52-week range. " +
                "The risk/reward profile supports a SELL signal.", ticker.toUpperCase(), gap, pricePosition);
            default -> String.format(
                "%s is trading close to estimated fair value around the %.0fth percentile of its 52-week range. " +
                "The current risk/reward profile supports a HOLD signal.", ticker.toUpperCase(), pricePosition);
        };
    }

    private record RecommendationResult(
            String valuation,
            String recommendation,
            int confidence
    ) {
    }

    private double toDouble(Object val, double def) {
        if (val == null) return def;
        try { return ((Number) val).doubleValue(); } catch (Exception e) { return def; }
    }

    private long toLong(Object val, long def) {
        if (val == null) return def;
        try { return ((Number) val).longValue(); } catch (Exception e) { return def; }
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}

