package com.dantri.webcrawler;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lấy outlinks từ một trang web
 */
public class LinkExtractor {
    private static final Logger logger = LoggerFactory.getLogger(LinkExtractor.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final long REQUEST_DELAY_MS = 300;
    private static final Pattern DANTRI_URL_PATTERN = Pattern.compile("https://dantri\\.com\\.vn/.*");


    public Set<String> extractLinks(String url) {
        Set<String> links = new HashSet<>();
        logger.debug("Extracting links from: {}", url);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Thread.sleep(REQUEST_DELAY_MS);
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .get();

                doc.select("a[href]").forEach(element -> {
                    String href = element.attr("abs:href");
                    if (DANTRI_URL_PATTERN.matcher(href).matches()) {
                        links.add(href);
                    }
                });

                logger.debug("Found {} outlinks from: {}", links.size(), url);
                return links;
            } catch (HttpStatusException e) {
                if (e.getStatusCode() == 429) {
                    logger.warn("Received 429 error for URL: {}. Attempt {}/{}, retrying after {}ms", url, attempt, MAX_RETRIES, RETRY_DELAY_MS);
                    if (attempt == MAX_RETRIES) {
                        logger.error("Max retries reached for URL: {}", url, e);
                        return links;
                    }
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        logger.error("Retry interrupted for URL: {}", url, ie);
                        return links;
                    }
                } else {
                    logger.error("Error extracting links from: {}", url, e);
                    return links;
                }
            } catch (IOException e) {
                logger.error("Error extracting links from: {}", url, e);
                return links;
            } catch (InterruptedException e) {
                logger.error("Request delay interrupted for URL: {}", url, e);
                return links;
            }
        }
        return links;
    }
}