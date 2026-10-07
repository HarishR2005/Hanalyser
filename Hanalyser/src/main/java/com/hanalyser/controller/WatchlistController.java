package com.hanalyser.controller;

import com.hanalyser.dto.WatchlistDTOs.*;
import com.hanalyser.service.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    public ResponseEntity<List<WatchlistItem>> getWatchlist() {
        return ResponseEntity.ok(watchlistService.getWatchlist());
    }

    @PostMapping("/add")
    public ResponseEntity<WatchlistItem> add(@Valid @RequestBody AddRequest request) {
        return ResponseEntity.ok(watchlistService.addToWatchlist(request));
    }

    @DeleteMapping("/{ticker}")
    public ResponseEntity<Map<String, String>> remove(@PathVariable String ticker) {
        watchlistService.removeFromWatchlist(ticker);
        return ResponseEntity.ok(Map.of("message", "Removed from watchlist"));
    }
}

