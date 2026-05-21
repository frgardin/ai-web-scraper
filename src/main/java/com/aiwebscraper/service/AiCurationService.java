package com.aiwebscraper.service;

import com.aiwebscraper.model.CuratedItem;
import com.aiwebscraper.model.NewsItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class AiCurationService {

    private static final Logger log = LoggerFactory.getLogger(AiCurationService.class);
    private static final String SYSTEM_PROMPT = """
        You are an expert AI news editor. When given a list of raw news items, you select the most \
        significant stories about artificial intelligence, machine learning, large language models, \
        and related technologies. You always respond with valid JSON only — no markdown, no explanation.""";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiCurationService(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.objectMapper = objectMapper;
    }

    public List<CuratedItem> curate(List<NewsItem> items) {
        if (items.isEmpty()) {
            log.info("No items to curate");
            return List.of();
        }

        try {
            String prompt = buildPrompt(items);
            log.info("Sending {} items to Claude for curation...", items.size());

            List<CuratedItem> curated = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(new ParameterizedTypeReference<>() {});

            log.info("Claude selected {} items", curated != null ? curated.size() : 0);
            return curated != null ? curated : fallback(items);

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
