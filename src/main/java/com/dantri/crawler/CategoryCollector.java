package com.dantri.crawler;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Thu thập danh sách các thư mục bài viết
 */
public class CategoryCollector {
    private static final Logger logger = LoggerFactory.getLogger(CategoryCollector.class);
    private static final String HOMEPAGE_URL = "https://dantri.com.vn/";
    private static final String CATEGORY_SELECTOR = "nav.menu ol.menu-wrap li.has-child a[href]";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static final long REQUEST_DELAY_MS = 300;
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("https://dantri\\.com\\.vn/[^/]+\\.htm");

    public List<String> collectCategories() {
        List<String> categoryUrls = new ArrayList<>();
        logger.info("Collecting categories from homepage: {}", HOMEPAGE_URL);

        Document doc = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Thread.sleep(REQUEST_DELAY_MS);
                doc = Jsoup.connect(HOMEPAGE_URL)
                        .userAgent(USER_AGENT)
                        .get();
                break;
            } catch (HttpStatusException e) {
                if (e.getStatusCode() == 429) {
                    logger.warn("Received 429 error for URL: {}. Attempt {}/{}, retrying after {}ms", HOMEPAGE_URL, attempt, MAX_RETRIES, RETRY_DELAY_MS);
                    if (attempt == MAX_RETRIES) {
                        logger.error("Max retries reached for URL: {}", HOMEPAGE_URL, e);
                        return categoryUrls;
                    }
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        logger.error("Retry interrupted for URL: {}", HOMEPAGE_URL, ie);
                        return categoryUrls;
                    }
                } else {
                    logger.error("Error collecting categories from homepage: {}", HOMEPAGE_URL, e);
                    return categoryUrls;
                }
            } catch (IOException e) {
                logger.error("Error collecting categories from homepage: {}", HOMEPAGE_URL, e);
                return categoryUrls;
            } catch (InterruptedException e) {
                logger.error("Request delay interrupted for URL: {}", HOMEPAGE_URL, e);
                return categoryUrls;
            }
        }

        if (doc == null) {
            logger.error("Failed to fetch homepage after retries: {}", HOMEPAGE_URL);
            return categoryUrls;
        }

        Elements categoryLinks = doc.select(CATEGORY_SELECTOR);
        for (org.jsoup.nodes.Element link : categoryLinks) {
            String url = link.attr("abs:href");
            if (CATEGORY_PATTERN.matcher(url).matches() &&
                    !url.endsWith("video-page.htm") &&
                    !url.endsWith("tin-moi-nhat.htm") &&
                    !url.equals("https://dantri.com.vn/")) {
                categoryUrls.add(url);
                logger.debug("Found category URL: {}", url);
            }
        }

        logger.info("Found {} categories", categoryUrls.size());
        return categoryUrls;
    }
}