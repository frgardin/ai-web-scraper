package com.aiwebscraper.scraper;

import com.aiwebscraper.model.NewsItem;

import java.util.List;

public interface NewsScraper {
    List<NewsItem> scrape();
    String sourceName();
}
