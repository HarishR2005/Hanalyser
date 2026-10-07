package com.hanalyser.controller;

import com.hanalyser.dto.StockAnalysisDTO;
import com.hanalyser.dto.StockAnalysisHistoryDTO;
import com.hanalyser.entity.StockAnalysisHistory;
import com.hanalyser.entity.User;
import com.hanalyser.repository.StockAnalysisHistoryRepository;
import com.hanalyser.repository.UserRepository;
import com.hanalyser.service.StockValuationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockController {

    private final StockValuationService valuationService;
    private final StockAnalysisHistoryRepository historyRepository;
    private final UserRepository userRepository;

    @GetMapping("/analyze/{ticker}")
    public ResponseEntity<StockAnalysisDTO> analyze(@PathVariable String ticker) {
        if (ticker == null || ticker.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        StockAnalysisDTO result = valuationService.analyze(ticker.trim().toUpperCase());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(Authentication auth,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Page<StockAnalysisHistory> history = historyRepository.findByUserOrderByAnalyzedAtDesc(
                user, PageRequest.of(page, size));
        return ResponseEntity.ok(history.map(this::toHistoryDto));
    }

    @GetMapping("/history/recent")
    public ResponseEntity<List<StockAnalysisHistoryDTO>> getRecentHistory(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<StockAnalysisHistory> history = historyRepository
                .findByUserOrderByAnalyzedAtDesc(user).stream().limit(5).toList();
        return ResponseEntity.ok(
                history.stream()
                        .map(this::toHistoryDto)
                        .toList()
        );
    }

    // Alias: /api/history (matches spec)
    @GetMapping("/history/all")
    public ResponseEntity<?> getHistoryAlias(Authentication auth,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return getHistory(auth, page, size);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        long totalAnalyses = historyRepository.countByUser(user);
        List<StockAnalysisHistory> recent = historyRepository.findByUserOrderByAnalyzedAtDesc(user);

        long buyCount = recent.stream().filter(h -> "BUY".equals(h.getRecommendation())).count();
        long sellCount = recent.stream().filter(h -> "SELL".equals(h.getRecommendation())).count();
        long holdCount = recent.stream().filter(h -> "HOLD".equals(h.getRecommendation())).count();

        return ResponseEntity.ok(Map.of(
            "totalAnalyses", totalAnalyses,
            "buyCount", buyCount,
            "sellCount", sellCount,
            "holdCount", holdCount,
            "userName", user.getName()
        ));
    }

    private StockAnalysisHistoryDTO toHistoryDto(StockAnalysisHistory history) {
        return StockAnalysisHistoryDTO.builder()
                .id(history.getId())
                .ticker(history.getTicker())
                .companyName(history.getCompanyName())
                .currentPrice(history.getCurrentPrice())
                .fairValue(history.getFairValue())
                .intrinsicValue(history.getIntrinsicValue())
                .marginOfSafety(history.getMarginOfSafety())
                .peRatio(history.getPeRatio())
                .riskScore(history.getRiskScore())
                .valuation(history.getValuation())
                .recommendation(history.getRecommendation())
                .buyBelow(history.getBuyBelow())
                .sellAbove(history.getSellAbove())
                .holdUntil(history.getHoldUntil())
                .confidence(history.getConfidence())
                .analyzedAt(history.getAnalyzedAt())
                .build();
    }
}

