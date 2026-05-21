package com.aiwebscraper.scraper;

import com.aiwebscraper.model.NewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class HackerNewsScraper implements NewsScraper {

    private static final Logger log = LoggerFactory.getLogger(HackerNewsScraper.class);
    private static final String ALGOLIA_URL = "https://hn.algolia.com/api/v1/search";
    private static final List<String> QUERIES = List.of(
        "artificial intelligence LLM",
        "machine learning deep learning",
        "ChatGPT Claude Gemini OpenAI"
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HackerNewsScraper(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<NewsItem> scrape() {
        List<NewsItem> items = new ArrayList<>();
        long cutoff = Instant.now().minus(24, ChronoUnit.HOURS).getEpochSecond();

        for (String query : QUERIES) {
            try {
                String raw = restClient.get()
                    .uri(ALGOLIA_URL, b -> b
                        .queryParam("query", query)
                        .queryParam("tags", "story")
                        .queryParam("hitsPerPage", "15")
                        .queryParam("numericFilters", "created_at_i>" + cutoff)
                        .build())
                    .retrieve()
                    .body(String.class);

                JsonNode response = objectMapper.readTree(raw);
                if (response != null && response.has("hits")) {
                    for (JsonNode hit : response.get("hits")) {
                        items.add(toNewsItem(hit));
                    }
                }
            } catch (Exception e) {
                log.warn("HN query '{}' failed: {}", query, e.getMessage());
            }
        }

        return items;
    }

    private NewsItem toNewsItem(JsonNode hit) {
        String url = hit.path("url").asText("");
        if (url.isBlank()) {
            url = "https://news.ycombinator.com/item?id=" + hit.path("objectID").asText();
        }
        String summary = "Points: " + hit.path("points").asInt()
                       + " | Comments: " + hit.path("num_comments").asInt();
        return new NewsItem(
            hit.path("title").asText(""),
            url,
            sourceName(),
            summary,
            Instant.ofEpochSecond(hit.path("created_at_i").asLong())
        );
    }

    @Override
    public String sourceName() {
        return "Hacker News";
    }
}
