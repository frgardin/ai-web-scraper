package com.aiwebscraper.service;

import com.aiwebscraper.model.CuratedItem;
import com.aiwebscraper.model.NewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class AiCurationService {

    private static final Logger log = LoggerFactory.getLogger(AiCurationService.class);
    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String SYSTEM_PROMPT = """
        You are an expert AI news editor. When given a list of raw news items, you select the most \
        significant stories about artificial intelligence, machine learning, large language models, \
        and related technologies. You always respond with valid JSON only — no markdown, no explanation.""";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public AiCurationService(RestClient.Builder builder, ObjectMapper objectMapper,
                              @Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public List<CuratedItem> curate(List<NewsItem> items) {
        if (apiKey.isBlank()) {
            log.warn("ANTHROPIC_API_KEY not set — falling back to recency sort, no AI summaries");
            return fallback(items);
        }
        if (items.isEmpty()) {
            log.info("No items to curate");
            return List.of();
        }

        try {
            String userMessage = buildPrompt(items);
            String requestBody = buildRequestBody(userMessage);

            log.info("Sending {} items to Claude for curation...", items.size());

            String raw = restClient.post()
                .uri(ANTHROPIC_API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .body(requestBody)
                .retrieve()
                .body(String.class);

            JsonNode response = objectMapper.readTree(raw);
            String text = response.path("content").get(0).path("text").asText();
            List<CuratedItem> curated = parseCuratedItems(text);
            log.info("Claude selected {} items", curated.size());
            return curated;

        } catch (Exception e) {
            log.error("Claude curation failed: {} — falling back to recency sort", e.getMessage());
            return fallback(items);
        }
    }

    private String buildPrompt(List<NewsItem> items) throws Exception {
        List<Map<String, String>> simplified = items.stream()
            .map(i -> Map.of(
                "title",   i.title(),
                "url",     i.url(),
                "source",  i.source(),
                "summary", truncate(i.rawSummary(), 200)
            ))
            .toList();

        String json = objectMapper.writeValueAsString(simplified);
        return """
            From the following news items, select the top 15 most significant AI stories. \
            Prioritise: major model releases, research breakthroughs, industry shifts, policy changes. \
            Return a JSON array only — no markdown. Each object must have exactly these fields: \
            "title", "url", "source", "aiSummary" (2 sentences explaining the significance), \
            "significance" (3-5 word label like "Major Model Release").

            Items:
            """ + json;
    }

    private String buildRequestBody(String userMessage) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
            "model", MODEL,
            "max_tokens", 4096,
            "system", SYSTEM_PROMPT,
            "messages", List.of(Map.of("role", "user", "content", userMessage))
        ));
    }

    private List<CuratedItem> parseCuratedItems(String json) throws Exception {
        return objectMapper.readerForListOf(CuratedItem.class).readValue(json);
    }

    private List<CuratedItem> fallback(List<NewsItem> items) {
        return items.stream()
            .sorted(Comparator.comparing(NewsItem::publishedAt).reversed())
            .limit(15)
            .map(i -> new CuratedItem(i.title(), i.url(), i.source(),
                i.rawSummary(), "Recent AI News"))
            .toList();
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) return text != null ? text : "";
        return text.substring(0, max) + "...";
    }
}
