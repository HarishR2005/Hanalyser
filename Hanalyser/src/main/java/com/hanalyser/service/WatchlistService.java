package com.hanalyser.service;

import com.hanalyser.dto.WatchlistDTOs.*;
import com.hanalyser.entity.User;
import com.hanalyser.entity.Watchlist;
import com.hanalyser.repository.UserRepository;
import com.hanalyser.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final UserRepository userRepository;

    public List<WatchlistItem> getWatchlist() {
        User user = getCurrentUser();
        return watchlistRepository.findByUserOrderByAddedAtDesc(user)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public WatchlistItem addToWatchlist(AddRequest request) {
        User user = getCurrentUser();
        if (watchlistRepository.existsByUserAndTicker(user, request.getTicker().toUpperCase())) {
            throw new RuntimeException("Already in watchlist.");
        }
        Watchlist entry = Watchlist.builder()
                .user(user)
                .ticker(request.getTicker().toUpperCase())
                .companyName(request.getCompanyName())
                .targetPrice(request.getTargetPrice())
                .notes(request.getNotes())
                .build();
        return toDto(watchlistRepository.save(entry));
    }

    @Transactional
    public void removeFromWatchlist(String ticker) {
        User user = getCurrentUser();
        watchlistRepository.deleteByUserAndTicker(user, ticker.toUpperCase());
    }

    private WatchlistItem toDto(Watchlist w) {
        WatchlistItem dto = new WatchlistItem();
        dto.setId(w.getId());
        dto.setTicker(w.getTicker());
        dto.setCompanyName(w.getCompanyName());
        dto.setTargetPrice(w.getTargetPrice());
        dto.setNotes(w.getNotes());
        dto.setAddedAt(w.getAddedAt());
        return dto;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}

