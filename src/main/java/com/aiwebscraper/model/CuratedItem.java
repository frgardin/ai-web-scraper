package com.aiwebscraper.model;

public record CuratedItem(
    String title,
    String url,
    String source,
    String aiSummary,
    String significance
) {}
