package com.aiwebscraper.scraper;

import com.aiwebscraper.model.NewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class RedditScraper implements NewsScraper {

    private static final Logger log = LoggerFactory.getLogger(RedditScraper.class);
    private static final List<String> SUBREDDITS = List.of(
        "artificial", "MachineLearning", "singularity", "LocalLLaMA"
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RedditScraper(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder
            .defaultHeader("User-Agent", "ai-web-scraper/1.0 (daily digest bot)")
            .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<NewsItem> scrape() {
        List<NewsItem> items = new ArrayList<>();

        for (String subreddit : SUBREDDITS) {
            try {
                String raw = restClient.get()
                    .uri("https://www.reddit.com/r/{sub}/top.json?limit=15&t=day", subreddit)
                    .retrieve()
                    .body(String.class);

                JsonNode response = objectMapper.readTree(raw);
                if (response == null) continue;

                for (JsonNode child : response.path("data").path("children")) {
                    JsonNode data = child.path("data");
                    if (!data.path("stickied").asBoolean()) {
                        items.add(toNewsItem(data, subreddit));
                    }
                }
            } catch (Exception e) {
                log.warn("Reddit r/{} failed: {}", subreddit, e.getMessage());
            }
        }

        return items;
    }

    private NewsItem toNewsItem(JsonNode data, String subreddit) {
        String url = data.path("url").asText("");
        if (!url.startsWith("http")) {
            url = "https://reddit.com" + data.path("permalink").asText("");
        }
        String summary = data.path("selftext").asText("").strip();
        if (summary.length() > 250) summary = summary.substring(0, 250) + "...";
        if (summary.isBlank()) summary = "↑ " + data.path("score").asInt()
                                       + "  💬 " + data.path("num_comments").asInt();
        return new NewsItem(
            data.path("title").asText(""),
            url,
            "Reddit r/" + subreddit,
            summary,
            Instant.ofEpochSecond(data.path("created_utc").asLong())
        );
    }

    @Override
    public String sourceName() {
        return "Reddit";
    }
}
