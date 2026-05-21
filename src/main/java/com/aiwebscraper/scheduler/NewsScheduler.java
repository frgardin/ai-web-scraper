package com.aiwebscraper.scheduler;

import com.aiwebscraper.config.AppProperties;
import com.aiwebscraper.model.CuratedItem;
import com.aiwebscraper.model.NewsItem;
import com.aiwebscraper.service.AiCurationService;
import com.aiwebscraper.service.EmailService;
import com.aiwebscraper.service.NewsAggregatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NewsScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NewsScheduler.class);

    private final NewsAggregatorService aggregator;
    private final AiCurationService curation;
    private final EmailService emailService;
    private final boolean runOnStartup;

    public NewsScheduler(NewsAggregatorService aggregator, AiCurationService curation,
                         EmailService emailService, AppProperties properties) {
        this.aggregator = aggregator;
        this.curation = curation;
        this.emailService = emailService;
        this.runOnStartup = properties.runOnStartup();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (runOnStartup) {
            log.info("Running news digest on startup...");
            runDigest();
        }
    }

    @Scheduled(cron = "${app.schedule.cron:0 0 8 * * *}")
    public void scheduledDigest() {
        log.info("Running scheduled news digest...");
        runDigest();
    }

    private void runDigest() {
        List<NewsItem> raw = aggregator.aggregateAllNews();
        List<CuratedItem> curated = curation.curate(raw);
        emailService.sendDigest(curated);
    }
}
