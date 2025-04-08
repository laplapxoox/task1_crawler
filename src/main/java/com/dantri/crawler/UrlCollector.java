package com.dantri.crawler;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lấy các url từ các trang web
 */
public class UrlCollector {
    private static final Logger logger = LoggerFactory.getLogger(UrlCollector.class);
    private static final Pattern ARTICLE_URL_PATTERN = Pattern.compile("https:\\/\\/dantri\\.com\\.vn\\/[^\\/]+\\/.*-\\d+\\.htm");
    private static final String PAGINATION_PATTERN = "/trang-{page}.htm";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static final long REQUEST_DELAY_MS = 300;
    private static final String BASE_DIR = "data";
    private static final Pattern URL_TIMESTAMP_PATTERN = Pattern.compile(".*-(\\d+)\\.htm");

    public Set<String> collectUrls(String categoryUrl) {
        Set<String> articleUrls = new HashSet<>();
        logger.info("Collecting article URLs from category: {}", categoryUrl);

        int page = 1;
        while (true) {
            String pageUrl = page == 1 ? categoryUrl : categoryUrl.replace(".htm", PAGINATION_PATTERN.replace("{page}", String.valueOf(page)));
            logger.debug("Processing page: {}", pageUrl);

            Document doc = null;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    Thread.sleep(REQUEST_DELAY_MS);
                    doc = Jsoup.connect(pageUrl)
                            .userAgent(USER_AGENT)
                            .get();
                    break;
                } catch (HttpStatusException e) {
                    if (e.getStatusCode() == 429) {
                        logger.warn("Received 429 error for URL: {}. Attempt {}/{}, retrying after {}ms", pageUrl, attempt, MAX_RETRIES, RETRY_DELAY_MS);
                        if (attempt == MAX_RETRIES) {
                            logger.error("Max retries reached for URL: {}", pageUrl, e);
                            return articleUrls;
                        }
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            logger.error("Retry interrupted for URL: {}", pageUrl, ie);
                            return articleUrls;
                        }
                    } else {
                        logger.error("Error collecting URLs from page: {}", pageUrl, e);
                        return articleUrls;
                    }
                } catch (IOException e) {
                    logger.error("Error collecting URLs from page: {}", pageUrl, e);
                    return articleUrls;
                } catch (InterruptedException e) {
                    logger.error("Request delay interrupted for URL: {}", pageUrl, e);
                    return articleUrls;
                }
            }

            if (doc == null) {
                logger.error("Failed to fetch page after retries: {}", pageUrl);
                return articleUrls;
            }

            Elements links = doc.select("article.article-item a[href]");
            if (links.isEmpty()) {
                logger.info("No more articles found on page {}, stopping pagination", page);
                break;
            }

            boolean hasRecentArticles = false;
            for (org.jsoup.nodes.Element link : links) {
                String url = link.attr("abs:href");
                if (ARTICLE_URL_PATTERN.matcher(url).matches()) {
                    if (DateFilter.isUrlWithinSixMonths(url)) {
                        if (isArticleExists(url)) {
                            articleUrls.add(url);
                            logger.debug("Found article URL: {}", url);
                            hasRecentArticles = true;
                        } else {
                            logger.debug("Article already exists, skipping: {}", url);
                        }
                    } else {
                        logger.debug("Article URL outside 6 months, skipping: {}", url);
                    }
                }
            }

            // Nếu không còn bài viết nào trong 6 tháng, dừng phân trang
            if (!hasRecentArticles) {
                logger.info("No recent articles found on page {}, stopping pagination", page);
                break;
            }

            page++;
        }

        logger.info("Collected {} article URLs from category: {}", articleUrls.size(), categoryUrl);
        return articleUrls;
    }

    public Set<String> collectUrlsFromPage(String categoryUrl, int page) {
        Set<String> articleUrls = new HashSet<>();
        String pageUrl = page == 1 ? categoryUrl : categoryUrl.replace(".htm", PAGINATION_PATTERN.replace("{page}", String.valueOf(page)));
        logger.debug("Processing page: {}", pageUrl);

        Document doc = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Thread.sleep(REQUEST_DELAY_MS);
                doc = Jsoup.connect(pageUrl)
                        .userAgent(USER_AGENT)
                        .get();
                break;
            } catch (HttpStatusException e) {
                if (e.getStatusCode() == 429) {
                    logger.warn("Received 429 error for URL: {}. Attempt {}/{}, retrying after {}ms", pageUrl, attempt, MAX_RETRIES, RETRY_DELAY_MS);
                    if (attempt == MAX_RETRIES) {
                        logger.error("Max retries reached for URL: {}", pageUrl, e);
                        return articleUrls;
                    }
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        logger.error("Retry interrupted for URL: {}", pageUrl, ie);
                        return articleUrls;
                    }
                } else {
                    logger.error("Error collecting URLs from page: {}", pageUrl, e);
                    return articleUrls;
                }
            } catch (IOException e) {
                logger.error("Error collecting URLs from page: {}", pageUrl, e);
                return articleUrls;
            } catch (InterruptedException e) {
                logger.error("Request delay interrupted for URL: {}", pageUrl, e);
                return articleUrls;
            }
        }

        if (doc == null) {
            logger.error("Failed to fetch page after retries: {}", pageUrl);
            return articleUrls;
        }

        Elements links = doc.select("article.article-item a[href]");
        for (org.jsoup.nodes.Element link : links) {
            String url = link.attr("abs:href");
            if (ARTICLE_URL_PATTERN.matcher(url).matches()) {
                if (isArticleExists(url)) {
                    articleUrls.add(url);
                    logger.debug("Found article URL: {}", url);
                } else {
                    logger.debug("Article already exists, skipping: {}", url);
                }
            }
        }

        logger.info("Collected {} article URLs from page {} of category: {}", articleUrls.size(), page, categoryUrl);
        return articleUrls;
    }

    public Set<String> collectUrlsFromFirstPage(String categoryUrl) {
        return collectUrlsFromPage(categoryUrl, 1);
    }

    private boolean isArticleExists(String url) {
        try {
            // Trích xuất danh mục từ URL
            String category = extractCategory(url);
            if (category == null) {
                logger.warn("Could not determine category for URL: {}", url);
                return true;
            }

            String timestamp = extractTimestampFromUrl(url);
            if (timestamp == null) {
                logger.warn("Could not extract timestamp from URL: {}", url);
                return true;
            }

            String year = timestamp.substring(0, 4);
            String month = timestamp.substring(4, 6);

            // Xác định đường dẫn file CSV
            String filePath = String.format("%s/%s/%s/%s/%s.csv", BASE_DIR, category, year, month, timestamp);
            File file = new File(filePath);
            if (file.exists()) {
                logger.debug("Article file exists: {}", filePath);
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.error("Error checking if article exists: {}", url, e);
            return true;
        }
    }

    private String extractCategory(String url) {
        Pattern pattern = Pattern.compile("https://dantri\\.com\\.vn/([^/]+)/.*");
        Matcher matcher = pattern.matcher(url);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractTimestampFromUrl(String url) {
        Matcher matcher = URL_TIMESTAMP_PATTERN.matcher(url);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }
}