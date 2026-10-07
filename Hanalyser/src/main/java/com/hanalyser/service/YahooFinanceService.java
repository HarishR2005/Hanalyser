package com.hanalyser.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class YahooFinanceService {

    private static final String QUOTE_URL =
            "https://query1.finance.yahoo.com/v7/finance/quote?symbols=";

    private static final String CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=5d&interval=1d";

    private static final String SUMMARY_URL =
            "https://query2.finance.yahoo.com/v10/finance/quoteSummary/%s" +
                    "?modules=financialData,defaultKeyStatistics";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public YahooFinanceService() {

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        this.objectMapper = new ObjectMapper();
    }

    public Map<String, Object> fetchStockData(String ticker) {

        String normalizedTicker = normalizeTicker(ticker);
        List<String> candidates =
                buildTickerCandidates(normalizedTicker);

        log.info("Fetching stock data for {} using candidates {}", normalizedTicker, candidates);

        for (String candidate : candidates) {

            Map<String, Object> quoteData =
                    fetchQuote(candidate);

            if (quoteData.isEmpty()) {
                continue;
            }

            String yahooSymbol =
                    String.valueOf(
                            quoteData.getOrDefault(
                                    "ticker",
                                    candidate
                            )
                    );

            Map<String, Object> summaryData =
                    fetchSummary(yahooSymbol);

            Map<String, Object> merged =
                    new HashMap<>(quoteData);

            merged.putAll(summaryData);

            return merged;
        }

        throw new RuntimeException(
                "Invalid ticker symbol or Yahoo Finance returned no quote data"
        );
    }

    private String normalizeTicker(String ticker) {

        return ticker.trim().toUpperCase();
    }

    private List<String> buildTickerCandidates(String ticker) {

        LinkedHashSet<String> candidates =
                new LinkedHashSet<>();

        if (isIndianYahooSymbol(ticker)) {
            candidates.add(ticker);
        }

        return new ArrayList<>(candidates);
    }

    private boolean isIndianYahooSymbol(String ticker) {
        return ticker.matches("[A-Z0-9&\\-]{1,20}\\.(NS|BO)");
    }

    private Map<String, Object> fetchQuote(String ticker) {

        try {

            String url =
                    QUOTE_URL +
                            URLEncoder.encode(
                                    ticker,
                                    StandardCharsets.UTF_8
                            );

            HttpRequest request = buildRequest(url);

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            log.info("Yahoo Response: {}", response.body());

            if (response.statusCode() != 200) {

                log.warn(
                        "Yahoo quote endpoint returned HTTP {} for {}. Body: {}",
                        response.statusCode(),
                        ticker,
                        response.body()
                );

                return fetchChartQuote(ticker);
            }

            JsonNode root =
                    objectMapper.readTree(response.body());

            JsonNode result =
                    root.path("quoteResponse")
                            .path("result");

            if (!result.isArray() || result.isEmpty()) {

                log.warn("No stock found for {}", ticker);

                return fetchChartQuote(ticker);
            }

            JsonNode q = result.get(0);

            String returnedSymbol =
                    q.path("symbol")
                            .asText("");

            log.info(
                    "Yahoo matched {} -> {}",
                    ticker,
                    returnedSymbol
            );

            double currentPrice =
                    q.path("regularMarketPrice")
                            .asDouble(0);

            if (currentPrice <= 0) {

                log.warn(
                        "Invalid price for {}",
                        ticker
                );

                return fetchChartQuote(ticker);
            }

            Map<String, Object> data =
                    new HashMap<>();

            data.put(
                    "ticker",
                    returnedSymbol
            );

            data.put(
                    "companyName",
                    q.path("longName")
                            .asText(
                                    q.path("shortName")
                                            .asText("Unknown Company")
                            )
            );

            data.put(
                    "exchange",
                    q.path("fullExchangeName")
                            .asText("Unknown")
            );

            data.put(
                    "currency",
                    q.path("currency")
                            .asText("INR")
            );

            data.put(
                    "currentPrice",
                    currentPrice
            );

            data.put(
                    "previousClose",
                    q.path("regularMarketPreviousClose")
                            .asDouble(0)
            );

            data.put(
                    "open",
                    q.path("regularMarketOpen")
                            .asDouble(0)
            );

            data.put(
                    "dayHigh",
                    q.path("regularMarketDayHigh")
                            .asDouble(0)
            );

            data.put(
                    "dayLow",
                    q.path("regularMarketDayLow")
                            .asDouble(0)
            );

            data.put(
                    "volume",
                    q.path("regularMarketVolume")
                            .asLong(0)
            );

            data.put(
                    "marketCap",
                    q.path("marketCap")
                            .asLong(0)
            );

            data.put(
                    "fiftyTwoWeekHigh",
                    q.path("fiftyTwoWeekHigh")
                            .asDouble(0)
            );

            data.put(
                    "fiftyTwoWeekLow",
                    q.path("fiftyTwoWeekLow")
                            .asDouble(0)
            );

            data.put(
                    "eps",
                    q.path("epsTrailingTwelveMonths")
                            .asDouble(0)
            );

            data.put(
                    "peRatio",
                    q.path("trailingPE")
                            .asDouble(0)
            );

            data.put(
                    "forwardPE",
                    q.path("forwardPE")
                            .asDouble(0)
            );

            data.put(
                    "pbRatio",
                    q.path("priceToBook")
                            .asDouble(0)
            );

            data.put(
                    "dividendYield",
                    q.path("trailingAnnualDividendYield")
                            .asDouble(0)
            );

            data.put(
                    "beta",
                    q.path("beta")
                            .asDouble(1)
            );

            return data;

        } catch (Exception e) {

            log.error(
                    "Yahoo quote fetch error for {}",
                    ticker,
                    e
            );

            return fetchChartQuote(ticker);
        }
    }

    private Map<String, Object> fetchChartQuote(String ticker) {

        try {

            String encodedTicker =
                    URLEncoder.encode(
                            ticker,
                            StandardCharsets.UTF_8
                    );

            String url =
                    String.format(
                            CHART_URL,
                            encodedTicker
                    );

            log.info("Fetching Yahoo chart fallback: {}", url);

            HttpRequest request =
                    buildRequest(url);

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            log.info("Yahoo chart response for {}: {}", ticker, response.body());

            if (response.statusCode() != 200) {
                log.warn(
                        "Yahoo chart endpoint returned HTTP {} for {}. Body: {}",
                        response.statusCode(),
                        ticker,
                        response.body()
                );

                return Map.of();
            }

            JsonNode root =
                    objectMapper.readTree(response.body());

            JsonNode result =
                    root.path("chart")
                            .path("result");

            if (!result.isArray() || result.isEmpty()) {
                log.warn("Yahoo chart returned no result for {}", ticker);

                return Map.of();
            }

            JsonNode meta =
                    result.get(0)
                            .path("meta");

            String symbol =
                    meta.path("symbol")
                            .asText(ticker);

            double currentPrice =
                    firstPositive(
                            meta.path("regularMarketPrice").asDouble(0),
                            meta.path("previousClose").asDouble(0),
                            lastNumericValue(
                                    result.get(0)
                                            .path("indicators")
                                            .path("quote")
                                            .path(0)
                                            .path("close")
                            )
                    );

            if (currentPrice <= 0) {
                log.warn("Yahoo chart returned invalid price for {}", ticker);

                return Map.of();
            }

            Map<String, Object> data =
                    new HashMap<>();

            data.put("ticker", symbol);
            data.put(
                    "companyName",
                    meta.path("longName")
                            .asText(
                                    meta.path("shortName")
                                            .asText(symbol)
                            )
            );
            data.put(
                    "exchange",
                    indianExchangeName(symbol)
            );
            data.put(
                    "currency",
                    textOrDefault(
                            meta.path("currency"),
                            "INR"
                    )
            );
            data.put("currentPrice", currentPrice);
            data.put(
                    "previousClose",
                    firstPositive(
                            meta.path("previousClose").asDouble(0),
                            meta.path("chartPreviousClose").asDouble(0)
                    )
            );
            data.put(
                    "open",
                    lastNumericValue(
                            result.get(0)
                                    .path("indicators")
                                    .path("quote")
                                    .path(0)
                                    .path("open")
                    )
            );
            data.put(
                    "dayHigh",
                    firstPositive(
                            meta.path("regularMarketDayHigh").asDouble(0),
                            lastNumericValue(
                                    result.get(0)
                                            .path("indicators")
                                            .path("quote")
                                            .path(0)
                                            .path("high")
                            )
                    )
            );
            data.put(
                    "dayLow",
                    firstPositive(
                            meta.path("regularMarketDayLow").asDouble(0),
                            lastNumericValue(
                                    result.get(0)
                                            .path("indicators")
                                            .path("quote")
                                            .path(0)
                                            .path("low")
                            )
                    )
            );
            data.put(
                    "volume",
                    firstPositiveLong(
                            meta.path("regularMarketVolume").asLong(0),
                            lastLongValue(
                                    result.get(0)
                                            .path("indicators")
                                            .path("quote")
                                            .path(0)
                                            .path("volume")
                            )
                    )
            );
            data.put("marketCap", 0L);
            data.put(
                    "fiftyTwoWeekHigh",
                    meta.path("fiftyTwoWeekHigh")
                            .asDouble(0)
            );
            data.put(
                    "fiftyTwoWeekLow",
                    meta.path("fiftyTwoWeekLow")
                            .asDouble(0)
            );
            data.put("eps", 0D);
            data.put("peRatio", 0D);
            data.put("forwardPE", 0D);
            data.put("pbRatio", 0D);
            data.put("dividendYield", 0D);
            data.put("beta", 1D);

            return data;

        } catch (Exception e) {

            log.error(
                    "Yahoo chart fallback error for {}",
                    ticker,
                    e
            );

            return Map.of();
        }
    }

    private Map<String, Object> fetchSummary(String ticker) {

        try {

            String url =
                    String.format(
                            SUMMARY_URL,
                            ticker
                    );

            HttpRequest request =
                    buildRequest(url);

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() != 200) {
                return Map.of();
            }

            JsonNode root =
                    objectMapper.readTree(response.body());

            JsonNode result =
                    root.path("quoteSummary")
                            .path("result");

            if (!result.isArray() || result.isEmpty()) {
                return Map.of();
            }

            JsonNode res = result.get(0);

            JsonNode fd =
                    res.path("financialData");

            Map<String, Object> data =
                    new HashMap<>();

            data.put(
                    "revenueGrowth",
                    getRawValue(fd, "revenueGrowth")
            );

            data.put(
                    "earningsGrowth",
                    getRawValue(fd, "earningsGrowth")
            );

            data.put(
                    "freeCashFlow",
                    getRawValue(fd, "freeCashflow")
            );

            data.put(
                    "debtToEquity",
                    getRawValue(fd, "debtToEquity")
            );

            data.put(
                    "roe",
                    getRawValue(fd, "returnOnEquity")
            );

            return data;

        } catch (Exception e) {

            log.warn(
                    "Summary fetch failed: {}",
                    e.getMessage()
            );

            return Map.of();
        }
    }

    private HttpRequest buildRequest(String url) {

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Referer", "https://finance.yahoo.com/")
                .GET()
                .build();
    }

    private double firstPositive(double... values) {

        for (double value : values) {
            if (value > 0) {
                return value;
            }
        }

        return 0;
    }

    private double lastNumericValue(JsonNode values) {

        if (!values.isArray()) {
            return 0;
        }

        for (int i = values.size() - 1; i >= 0; i--) {
            JsonNode value =
                    values.get(i);

            if (value != null && value.isNumber()) {
                return value.asDouble();
            }
        }

        return 0;
    }

    private long lastLongValue(JsonNode values) {

        if (!values.isArray()) {
            return 0;
        }

        for (int i = values.size() - 1; i >= 0; i--) {
            JsonNode value =
                    values.get(i);

            if (value != null && value.isNumber()) {
                return value.asLong();
            }
        }

        return 0;
    }

    private long firstPositiveLong(long... values) {

        for (long value : values) {
            if (value > 0) {
                return value;
            }
        }

        return 0;
    }

    private String indianExchangeName(String ticker) {

        if (ticker.endsWith(".NS")) {
            return "NSE";
        }

        if (ticker.endsWith(".BO")) {
            return "BSE";
        }

        return "Unknown";
    }

    private String textOrDefault(JsonNode node, String defaultValue) {

        String value =
                node.asText("");

        return value.isBlank()
                ? defaultValue
                : value;
    }

    private double getRawValue(
            JsonNode parent,
            String field
    ) {

        JsonNode node =
                parent.path(field);

        JsonNode raw =
                node.path("raw");

        return raw.isNumber()
                ? raw.asDouble()
                : 0;
    }
}

