package com.dantri.webcrawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Triển khai thuật toán BFS để thu thập bài viết từ website
 */
public class WebCrawler {
    private static final Logger logger = LoggerFactory.getLogger(WebCrawler.class);
    private static final String START_URL = ConfigLoader.getStartUrl();
    private static final Pattern ARTICLE_URL_PATTERN = Pattern.compile(ConfigLoader.getArticleUrlPattern());
    private static final Pattern CATEGORY_URL_PATTERN = Pattern.compile(ConfigLoader.getCategoryUrlPattern());
    private static final int DEFAULT_MAX_LEVEL = ConfigLoader.getDefaultMaxLevel();
    private static final int MAX_LEVEL_WITHIN_SIX_MONTHS = ConfigLoader.getMaxLevelWithinSixMonths();
    private static final int MAX_URLS_PER_CRAWL = ConfigLoader.getMaxUrlsPerCrawl();
    private static final long SIX_MONTHS_MILLIS = ConfigLoader.getSixMonthsMillis(); // 6 tháng tính bằng milliseconds

    private final LinkExtractor linkExtractor;
    private final ArticleParser articleParser;
    private final ArticleStorage articleStorage;
    private final VisitedUrlsManager visitedUrlsManager;

    public WebCrawler(LinkExtractor linkExtractor, ArticleParser articleParser, ArticleStorage articleStorage, VisitedUrlsManager visitedUrlsManager) {
        this.linkExtractor = linkExtractor;
        this.articleParser = articleParser;
        this.articleStorage = articleStorage;
        this.visitedUrlsManager = visitedUrlsManager;
    }

    /**
     * Bắt đầu thuật toán
     */
    public void crawl() {
        logger.info("Starting BFS crawl from: {}", START_URL);

        int maxLevel = determineMaxLevel();
        logger.info("Max level for this crawl: {}", maxLevel);

        PriorityQueue<UrlWithLevel> queue = new PriorityQueue<>(Comparator.comparingInt(UrlWithLevel::getLevel));
        queue.add(new UrlWithLevel(START_URL, 0));

        Set<String> seenCategoriesInThisCrawl = new HashSet<>();

        int processedUrls = 0;

        while (!queue.isEmpty() && processedUrls < MAX_URLS_PER_CRAWL) {
            UrlWithLevel current = queue.poll();
            String url = current.getUrl();
            int level = current.getLevel();

            if (level > maxLevel) {
                logger.debug("Reached max level ({}), skipping URL: {}", maxLevel, url);
                continue;
            }

            boolean isArticle = ARTICLE_URL_PATTERN.matcher(url).matches();
            boolean isCategory = CATEGORY_URL_PATTERN.matcher(url).matches();

            if (isArticle) {
                if (visitedUrlsManager.isVisited(url)) {
                    logger.debug("Article URL already visited, skipping: {}", url);
                    continue;
                }
                visitedUrlsManager.addVisitedUrl(url);
                Article article = articleParser.parseArticle(url);
                if (article != null) {
                    if (isWithinSixMonths(article.getPublishTime())) {
                        logger.debug("Article is older than 6 months, skipping: {}", url);
                        continue;
                    }
//                    if (articleStorage.articleExists(article)) continue;
                    articleStorage.saveArticle(article);
                }
            }

            if (isCategory) {
                logger.debug("Processing category URL: {} (level {})", url, level);
            } else if (isArticle) {
                logger.debug("Processing article URL: {} (level {})", url, level);
            } else {
                visitedUrlsManager.addVisitedUrl(url);
                logger.debug("Processing other URL: {} (level {})", url, level);
            }

            Set<String> outlinks = linkExtractor.extractLinks(url);
            if (outlinks.isEmpty()) {
                logger.debug("No outlinks found for URL: {}", url);
            }

            for (String outlink : outlinks) {
                boolean outlinkIsArticle = ARTICLE_URL_PATTERN.matcher(outlink).matches();
                boolean outlinkIsCategory = CATEGORY_URL_PATTERN.matcher(outlink).matches();

                if (outlinkIsArticle) {
                    if (visitedUrlsManager.isVisited(outlink)) {
                        continue;
                    }
                    visitedUrlsManager.addVisitedUrl(outlink);
                    Article article = articleParser.parseArticle(outlink);
                    if (article != null) {
                        if (isWithinSixMonths(article.getPublishTime())) {
                            logger.debug("Article is older than 6 months, skipping: {}", outlink);
                            continue;
                        }
//                        if (articleStorage.articleExists(article)) continue;
                        articleStorage.saveArticle(article);
                    }
                } else if (outlinkIsCategory) {
                    // Kiểm tra xem URL danh mục đã được thêm vào queue trong lần crawl này chưa
                    if (seenCategoriesInThisCrawl.contains(outlink)) {
                        logger.debug("Category URL already added to queue in this crawl, skipping: {}", outlink);
                        continue;
                    }
                    seenCategoriesInThisCrawl.add(outlink);
                    queue.add(new UrlWithLevel(outlink, level + 1));
                } else {
                    queue.add(new UrlWithLevel(outlink, level + 1));
                    visitedUrlsManager.addVisitedUrl(outlink);
                }
            }

            processedUrls++;
            if (processedUrls >= MAX_URLS_PER_CRAWL) {
                logger.info("Reached max URLs per crawl ({}), stopping. Queue size: {}", MAX_URLS_PER_CRAWL, queue.size());
                break;
            }
        }

        logger.info("Finished BFS crawl. Processed {} URLs.", processedUrls);
    }

    // Xác định số cấp để duyệt
    private int determineMaxLevel() {
        Date latest = articleStorage.getLatestPublishTime();
        Date oldest = articleStorage.getOldestPublishTime();
        if (latest == null || oldest == null) {
            return MAX_LEVEL_WITHIN_SIX_MONTHS;
        }

        long monthsBetween = Duration.between(
                Instant.ofEpochMilli(oldest.getTime()),
                Instant.ofEpochMilli(latest.getTime())
        ).toDays() / 30;

        if (monthsBetween >= 6) {
            logger.info("Data spans 6 months or more, using default max level: {}", DEFAULT_MAX_LEVEL);
            return DEFAULT_MAX_LEVEL;
        } else {
            logger.info("Data spans less than 6 months ({} months), using max level: {}", monthsBetween, MAX_LEVEL_WITHIN_SIX_MONTHS);
            return MAX_LEVEL_WITHIN_SIX_MONTHS;
        }
    }

    // Kiểm tra thời gian của bài báo có trong vòng 6 tháng
    private boolean isWithinSixMonths(Date publishTime) {
        if (publishTime == null) {
            return true;
        }
        long currentTime = System.currentTimeMillis();
        long publishTimeMillis = publishTime.getTime();
        return (currentTime - publishTimeMillis) > SIX_MONTHS_MILLIS;
    }

    /**
     * Class để lưu trữ url để đưa vào Queue
     */
    private static class UrlWithLevel {
        private final String url;
        private final int level;

        public UrlWithLevel(String url, int level) {
            this.url = url;
            this.level = level;
        }

        public String getUrl() {
            return url;
        }

        public int getLevel() {
            return level;
        }
    }
}