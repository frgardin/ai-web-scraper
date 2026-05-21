package com.aiwebscraper.model;

import java.time.Instant;

public record NewsItem(
    String title,
    String url,
    String source,
    String rawSummary,
    Instant publishedAt
) {}
