package com.hanalyser.repository;

import com.hanalyser.entity.StockAnalysisHistory;
import com.hanalyser.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StockAnalysisHistoryRepository extends JpaRepository<StockAnalysisHistory, Long> {
    List<StockAnalysisHistory> findByUserOrderByAnalyzedAtDesc(User user);
    Page<StockAnalysisHistory> findByUser(User user, Pageable pageable);
    Page<StockAnalysisHistory> findByUserOrderByAnalyzedAtDesc(User user, Pageable pageable);
    List<StockAnalysisHistory> findByUserAndTickerOrderByAnalyzedAtDesc(User user, String ticker);
    long countByUser(User user);
}

