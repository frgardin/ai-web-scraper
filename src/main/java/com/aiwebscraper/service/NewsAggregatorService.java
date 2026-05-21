package com.aiwebscraper.service;

import com.aiwebscraper.config.AppProperties;
import com.aiwebscraper.model.NewsItem;
import com.aiwebscraper.scraper.NewsScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NewsAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(NewsAggregatorService.class);

    private final List<NewsScraper> scrapers;
    private final int maxItems;

    public NewsAggregatorService(List<NewsScraper> scrapers, AppProperties appProperties) {
        this.scrapers = scrapers;
        this.maxItems = appProperties.news().maxItems();
    }

    public List<NewsItem> aggregateAllNews() {
        List<NewsItem> all = new ArrayList<>();

        for (NewsScraper scraper : scrapers) {
            try {
                List<NewsItem> items = scraper.scrape();
                log.info("Fetched {} items from {}", items.size(), scraper.sourceName());
                all.addAll(items);
            } catch (Exception e) {
                log.error("Scraper {} failed: {}", scraper.sourceName(), e.getMessage());
            }
        }

        // Deduplicate by URL, first occurrence wins
        Map<String, NewsItem> seen = new LinkedHashMap<>();
        for (NewsItem item : all) {
            if (item.title() != null && !item.title().isBlank()
                    && item.url() != null && !item.url().isBlank()) {
                seen.putIfAbsent(item.url(), item);
            }
        }

        List<NewsItem> result = new ArrayList<>(seen.values());
        if (result.size() > maxItems) result = result.subList(0, maxItems);
        log.info("Aggregated {} unique items across all sources", result.size());
        return result;
    }
}
