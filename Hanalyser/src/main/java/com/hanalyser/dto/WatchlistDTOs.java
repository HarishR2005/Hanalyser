package com.hanalyser.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

public class WatchlistDTOs {

    @Data
    public static class AddRequest {
        @NotBlank(message = "Ticker is required")
        private String ticker;
        private String companyName;
        private Double targetPrice;
        private String notes;
    }

    @Data
    public static class WatchlistItem {
        private Long id;
        private String ticker;
        private String companyName;
        private Double targetPrice;
        private String notes;
        private LocalDateTime addedAt;
    }
}

