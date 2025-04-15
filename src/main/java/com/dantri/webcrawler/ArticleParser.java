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
import java.util.Date;
import java.util.List;

/**
 * Phân tích và trích xuất thông tin từ url
 */
public class ArticleParser {
    private static final Logger logger = LoggerFactory.getLogger(ArticleParser.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final long REQUEST_DELAY_MS = ConfigLoader.getRequestDelayMs();
    private static final long RETRY_DELAY_MS = ConfigLoader.getRetryDelayMs();
    private static final int MAX_RETRIES = ConfigLoader.getMaxRetries();
    private static final List<String> CONTENT_SELECTORS = ConfigLoader.getContentSelectors();
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
                JsonNode breadcrumbNode = null;
                for (org.jsoup.nodes.Element script : doc.select("script[type=\"application/ld+json\"]")) {
                    JsonNode node = mapper.readTree(script.html());
                    if (node.has("@type")) {
                        String type = node.get("@type").asText();
                        if (type.equals("NewsArticle")) {
                            newsArticleNode = node;
                        } else if (type.equals("BreadcrumbList")) {
                            breadcrumbNode = node;
                        } else if (type.equals("ItemList")) {
                            logger.debug("URL is an ItemList (event page), not a NewsArticle: {}", url);
                            return null;
                        }
                    }
                }

                if (newsArticleNode == null) {
                    logger.error("No NewsArticle JSON-LD found for URL: {}", url);
                    return null;
                }

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

                String category = null;
                if (breadcrumbNode != null && breadcrumbNode.has("itemListElement")) {
                    JsonNode items = breadcrumbNode.get("itemListElement");
                    if (items.isArray()) {
                        int position = ConfigLoader.getCategoryPosition();
                        if (items.size() > position) {
                            JsonNode categoryNode = items.get(position);
                            if (ConfigLoader.isNestedArray()) {
                                categoryNode = categoryNode.get(0); // Xử lý cấu trúc lồng mảng
                            }

                            String urlField = ConfigLoader.getCategoryUrlField();
                            String categoryUrl = null;
                            if (urlField.contains(".")) {
                                String[] fields = urlField.split("\\.");
                                JsonNode urlNode = categoryNode;
                                for (String field : fields) {
                                    urlNode = urlNode.get(field);
                                    if (urlNode == null) break;
                                }
                                categoryUrl = urlNode != null ? urlNode.asText() : null;
                            } else {
                                categoryUrl = categoryNode.has(urlField) ? categoryNode.get(urlField).asText() : null;
                            }

                            if (categoryUrl != null) {
                                // Lấy phần cuối của URL làm danh mục
                                String[] urlParts = categoryUrl.split("/");
                                category = urlParts[urlParts.length - 1];
                                // Cắt bỏ đuôi nếu có
                                String suffix = ConfigLoader.getCategoryUrlSuffix();
                                if (!suffix.isEmpty() && category.endsWith(suffix)) {
                                    category = category.substring(0, category.length() - suffix.length());
                                }
                            }
                        }
                    }
                }
                if (category == null) {
                    logger.warn("Could not determine category for URL: {}", url);
                    category = "unknown";
                }

                String content = "";
                for (String selector : CONTENT_SELECTORS) {
                    content = doc.select(selector).text();
                    if (!content.isEmpty()) {
                        logger.debug("Found content using selector: {}", selector);
                        break;
                    }
                }
                if (content.isEmpty()) {
                    logger.warn("Could not parse content for URL: {}, trying default selectors", url);
                    for (String selector : ConfigLoader.getDefaultContentSelectors()) {
                        content = doc.select(selector).text();
                        if (!content.isEmpty()) {
                            logger.debug("Found content using default selector: {}", selector);
                            break;
                        }
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
                article.setCategory(category);

                logger.debug("Parsed article: {}", article.getTitle());
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