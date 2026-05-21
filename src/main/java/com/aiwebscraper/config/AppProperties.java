package com.aiwebscraper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @DefaultValue Email email,
    @DefaultValue News news,
    @DefaultValue("true") boolean runOnStartup,
    @DefaultValue Schedule schedule
) {
    public record Email(
        @DefaultValue List<String> recipients,
        @DefaultValue("digest@localhost") String from,
        @DefaultValue("Daily AI News Digest") String subject
    ) {}

    public record News(
        @DefaultValue("20") int maxItems,
        @DefaultValue List<String> rssFeeds
    ) {}

    public record Schedule(
        @DefaultValue("0 0 8 * * *") String cron
    ) {}
}
