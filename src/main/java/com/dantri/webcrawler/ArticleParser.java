package com.dantri.webcrawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Phân tích và trích xuất thông tin từ url
 */
public class ArticleParser {
    private static final Logger logger = LoggerFactory.getLogger(ArticleParser.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final long REQUEST_DELAY_MS = 300;
    private static final List<String> CONTENT_SELECTORS = Arrays.asList(
            "div.singular-content",
            "div.e-magazine__body.dnews__body",
            "div.e-magazine__body",
            "div.e-magazine__body#content",
            "div[itemprop=\"articleBody\"]"
    );
    private static final ObjectMapper mapper = new ObjectMapper();

    public Article parseArticle(String url) {
//        logger.info("Parsing article: {}", url);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Thread.sleep(REQUEST_DELAY_MS);

                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .get();

                JsonNode newsArticleNode = null;
                for (org.jsoup.nodes.Element script : doc.select("script[type=\"application/ld+json\"]")) {
                    JsonNode node = mapper.readTree(script.html());
                    if (node.has("@type")) {
                        String type = node.get("@type").asText();
                        if (type.equals("NewsArticle")) {
                            newsArticleNode = node;
                            break;
                        }
                    }
                }

                if (newsArticleNode == null) {
                    logger.error("No NewsArticle JSON-LD found for URL: {}", url);
                    return null;
                }

                // Lấy tiêu đề, tác giả, ngày tháng, tóm tắt
                String title = newsArticleNode.get("headline").asText();
                String description = newsArticleNode.get("description").asText();

                String author;
                JsonNode authorNode = newsArticleNode.get("author");
                if (authorNode.isArray()) {
                    StringBuilder authorBuilder = new StringBuilder();
                    for (JsonNode authorItem : authorNode) {
                        if (authorItem.has("name")) {
                            if (!authorBuilder.isEmpty()) {
                                authorBuilder.append(", ");
                            }
                            authorBuilder.append(authorItem.get("name").asText());
                        }
                    }
                    author = authorBuilder.toString();
                } else {
                    author = authorNode.has("name") ? authorNode.get("name").asText() : "";
                }

                String publishTimeStr = newsArticleNode.get("datePublished").asText();
                OffsetDateTime offsetDateTime = OffsetDateTime.parse(publishTimeStr);
                Date publishTime = Date.from(offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()).toInstant());

                // Lấy nội dung bài viết
                String content = "";
                for (String selector : CONTENT_SELECTORS) {
                    content = doc.select(selector).text();
                    if (!content.isEmpty()) {
                        logger.debug("Found content using selector: {}", selector);
                        break;
                    }
                }
                if (content.isEmpty()) {
                    logger.warn("Could not parse content for URL: {}", url);
                }

                Article article = new Article();
                article.setUrl(url);
                article.setTitle(title);
                article.setDescription(description);
                article.setContent(content);
                article.setPublishTime(publishTime);
                article.setAuthor(author);

                logger.info("Parsed new article: {}", article.getUrl());
                return article;
            } catch (HttpStatusException e) {
                if (e.getStatusCode() == 429) {
                    logger.warn("Received 429 error for URL: {}. Attempt {}/{}, retrying after {}ms", url, attempt, MAX_RETRIES, RETRY_DELAY_MS);
                    if (attempt == MAX_RETRIES) {
                        logger.error("Max retries reached for URL: {}", url, e);
                        return null;
                    }
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        logger.error("Retry interrupted for URL: {}", url, ie);
                        return null;
                    }
                } else {
                    logger.error("Error parsing article: {}", url, e);
                    return null;
                }
            } catch (Exception e) {
                logger.error("Error parsing article: {}", url, e);
                return null;
            }
        }
        return null;
    }
}