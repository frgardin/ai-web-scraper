package com.aiwebscraper.scraper;

import com.aiwebscraper.config.AppProperties;
import com.aiwebscraper.model.NewsItem;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class RssFeedScraper implements NewsScraper {

    private static final Logger log = LoggerFactory.getLogger(RssFeedScraper.class);
    private static final List<String> DEFAULT_FEEDS = List.of(
        "https://techcrunch.com/category/artificial-intelligence/feed/",
        "https://www.theverge.com/rss/ai-artificial-intelligence/index.xml",
        "https://venturebeat.com/category/ai/feed/",
        "https://www.technologyreview.com/feed/"
    );
    private static final Map<String, String> FEED_NAMES = Map.of(
        "techcrunch.com",        "TechCrunch AI",
        "theverge.com",          "The Verge AI",
        "venturebeat.com",       "VentureBeat AI",
        "technologyreview.com",  "MIT Technology Review"
    );

    private final List<String> feedUrls;

    public RssFeedScraper(AppProperties properties) {
        List<String> configured = properties.news().rssFeeds();
        this.feedUrls = (configured != null && !configured.isEmpty()) ? configured : DEFAULT_FEEDS;
    }

    @Override
    public List<NewsItem> scrape() {
        List<NewsItem> items = new ArrayList<>();
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);

        for (String feedUrl : feedUrls) {
            try {
                SyndFeed feed = new SyndFeedInput().build(
                    new XmlReader(URI.create(feedUrl).toURL())
                );
                String feedName = resolveName(feedUrl, feed.getTitle());

                for (SyndEntry entry : feed.getEntries()) {
                    Instant published = resolveDate(entry);
                    if (published.isBefore(cutoff)) continue;

                    String summary = entry.getDescription() != null
                        ? entry.getDescription().getValue() : "";
                    summary = summary.replaceAll("<[^>]+>", "").strip();
                    if (summary.length() > 300) summary = summary.substring(0, 300) + "...";

                    items.add(new NewsItem(
                        entry.getTitle() != null ? entry.getTitle().strip() : "",
                        entry.getLink(),
                        feedName,
                        summary,
                        published
                    ));
                }
            } catch (Exception e) {
                log.warn("RSS feed {} failed: {}", feedUrl, e.getMessage());
            }
        }

        return items;
    }

    private String resolveName(String feedUrl, String fallback) {
        return FEED_NAMES.entrySet().stream()
            .filter(e -> feedUrl.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(fallback != null ? fallback : feedUrl);
    }

    private Instant resolveDate(SyndEntry entry) {
        Date date = entry.getPublishedDate() != null
            ? entry.getPublishedDate() : entry.getUpdatedDate();
        return date != null ? date.toInstant() : Instant.now();
    }

    @Override
    public String sourceName() {
        return "RSS Feeds";
    }
}
