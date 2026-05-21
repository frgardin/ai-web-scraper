# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Setup

### 1. Start local SMTP (Mailpit)
```bash
docker compose up -d
# Emails viewable at http://localhost:8025
```

### 2. Set Anthropic API key
```bash
export ANTHROPIC_API_KEY=sk-ant-...
```
Without this, the app still runs but skips AI summaries (falls back to recency sort).

### 3. Configure recipients
Edit `src/main/resources/application.properties`:
```properties
app.email.recipients=you@example.com
```

## Build & Run

```bash
# Build
mvn clean package

# Run (fires immediately on startup)
java -jar target/ai-web-scraper-1.0.0.jar

# Run via Maven
mvn spring-boot:run

# Run a single test
mvn test -Dtest=ClassName

# Skip tests during build
mvn clean package -DskipTests
```

## Architecture

Spring Boot CLI app (`spring.main.web-application-type=none`). On startup it:

1. **Scrapes** three source types in `scraper/`:
   - `HackerNewsScraper` — Algolia HN Search API, 3 AI-term queries, last 24 h
   - `RedditScraper` — Reddit JSON API, 4 subreddits (artificial, MachineLearning, singularity, LocalLLaMA), top/day
   - `RssFeedScraper` — ROME-parsed RSS from TechCrunch AI, The Verge AI, VentureBeat AI, MIT Tech Review

2. **Aggregates** in `NewsAggregatorService` — merges all sources, deduplicates by URL

3. **Curates** in `AiCurationService` — sends raw items to `claude-haiku-4-5-20251001` via Anthropic REST API; Claude picks top 15 and writes an AI summary + significance label per item; falls back to recency sort if API key is missing or the call fails

4. **Emails** in `EmailService` — renders `templates/news-email.html` (Thymeleaf), sends via JavaMail to `localhost:1025` (Mailpit)

### Adding a new scraper
Implement `NewsScraper`, annotate with `@Component`. Spring auto-discovers and includes it in aggregation.

### Switching to a real email provider
Update `spring.mail.*` in `application.properties` with real SMTP credentials. Mailpit is only for local testing.
