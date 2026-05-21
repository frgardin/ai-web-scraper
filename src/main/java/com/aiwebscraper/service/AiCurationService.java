package com.aiwebscraper.service;

import com.aiwebscraper.model.CuratedItem;
import com.aiwebscraper.model.NewsItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AiCurationService {

    private static final Logger log = LoggerFactory.getLogger(AiCurationService.class);
    private static final String SYSTEM_PROMPT = """
        You are an expert AI news editor. When given a list of raw news items, you select the most \
        significant stories about artificial intelligence, machine learning, large language models, \
        and related technologies. You always respond with valid JSON only — no markdown, no explanation.""";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final NewsCacheService cacheService;

    public AiCurationService(ChatClient.Builder builder, ObjectMapper objectMapper, NewsCacheService cacheService) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
    }

    public List<CuratedItem> curate(List<NewsItem> items) {
        if (items.isEmpty()) {
            log.info("No items to curate");
            return List.of();
        }

        Map<String, Instant> urlToPublishedAt = items.stream()
            .collect(Collectors.toMap(NewsItem::url, NewsItem::publishedAt, (a, b) -> a));

        List<CuratedItem> cachedItems = new ArrayList<>();
        List<NewsItem> newItems = new ArrayList<>();

        for (NewsItem item : items) {
            Optional<CuratedItem> cached = cacheService.get(item.url());
            if (cached.isPresent()) {
                cachedItems.add(cached.get());
            } else {
                newItems.add(item);
            }
        }

        log.info("Cache: {} hits, {} misses — sending {} new items to Claude",
            cachedItems.size(), newItems.size(), newItems.size());

        List<CuratedItem> newCurated = List.of();
        if (!newItems.isEmpty()) {
            try {
                String prompt = buildPrompt(newItems);
                String raw = chatClient.prompt().user(prompt).call().content();
                String cleanJson = stripMarkdownFences(raw);
                newCurated = objectMapper.readValue(cleanJson, new TypeReference<>() {});
                if (newCurated == null) newCurated = List.of();
                log.info("Claude summarized {} new items", newCurated.size());
                cacheService.putAll(newCurated);
            } catch (Exception e) {
                log.error("Claude curation failed: {} — using cache only + recency fallback", e.getMessage());
                newCurated = newItems.stream()
                    .map(i -> new CuratedItem(i.title(), i.url(), i.source(), i.rawSummary(), "Recent AI News"))
                    .toList();
            }
        }

        return Stream.concat(cachedItems.stream(), newCurated.stream())
            .sorted(Comparator.comparing(
                item -> urlToPublishedAt.getOrDefault(item.url(), Instant.EPOCH),
                Comparator.reverseOrder()
            ))
            .limit(15)
            .toList();
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
            Summarize ALL of the following news items about AI. For EACH item, return an object with \
            these exact fields: "title", "url", "source", \
            "aiSummary" (2 sentences explaining why it matters), \
            "significance" (3-5 word label like "Major Model Release"). \
            Return a JSON array only — no markdown. Include every item provided.

            Items:
            """ + json;
    }

    private String stripMarkdownFences(String text) {
        String s = text == null ? "" : text.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:json)?\\s*\n?", "");
            s = s.replaceFirst("\\s*```\\s*$", "");
        }
        return s.strip();
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) return text != null ? text : "";
        return text.substring(0, max) + "...";
    }
}
