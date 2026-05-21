package com.aiwebscraper.service;

import com.aiwebscraper.config.AppProperties;
import com.aiwebscraper.model.CuratedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class NewsCacheService {

    private static final Logger log = LoggerFactory.getLogger(NewsCacheService.class);
    private static final String KEY_PREFIX = "news:curated:";

    private final RedisTemplate<String, CuratedItem> redisTemplate;
    private final Duration ttl;

    public NewsCacheService(RedisTemplate<String, CuratedItem> redisTemplate, AppProperties appProperties) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(appProperties.cache().ttlHours());
    }

    public Optional<CuratedItem> get(String url) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + url));
        } catch (Exception e) {
            log.warn("Cache read failed for url={}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String url, CuratedItem item) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + url, item, ttl);
        } catch (Exception e) {
            log.warn("Cache write failed for url={}: {}", url, e.getMessage());
        }
    }

    public void putAll(List<CuratedItem> items) {
        items.forEach(item -> put(item.url(), item));
    }
}
