package com.dantri.webcrawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lưu trữ bài viết vào file CSV, tổ chức theo cấu trúc thư mục
 */
public class ArticleStorage {
    private static final Logger logger = LoggerFactory.getLogger(ArticleStorage.class);
    private static final String BASE_DIR = "data";
    private static final SimpleDateFormat YEAR_FORMAT = new SimpleDateFormat("yyyy");
    private static final SimpleDateFormat MONTH_FORMAT = new SimpleDateFormat("MM");

    private Date latestPublishTime;
    private Date oldestPublishTime;
    private final String metadataFilePath;

    public ArticleStorage() {
        String domain = ConfigLoader.getCurrentDomain().getName();
        this.metadataFilePath = String.format("%s/metadata_%s.json", BASE_DIR, domain);
        loadMetadata();
    }

    /**
     * Saves an article to a CSV file.
     *
     * @param article The article to save
     * @return true if the article was saved, false otherwise
     */
    public boolean saveArticle(Article article) {
        try {
            String filePath = buildFilePath(article);
            if (filePath == null) {
                return false;
            }

            File file = new File(filePath);
            try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
                writer.writeNext(new String[]{"URL", "Title", "Description", "Content", "PublishTime", "Author", "Category"});
                writer.writeNext(new String[]{
                        article.getUrl(),
                        article.getTitle(),
                        article.getDescription(),
                        article.getContent(),
                        article.getPublishTime().toString(),
                        article.getAuthor(),
                        article.getCategory()
                });
                logger.info("Saved article to: {}", filePath);
            }

            updateMetadata(article.getPublishTime());
            return true;
        } catch (Exception e) {
            logger.error("Error saving article: {}", article.getUrl(), e);
            return false;
        }
    }

    /**
     * Updates the latest and oldest publish times based on the given publish time.
     */
    private void updateMetadata(Date publishTime) {
        boolean updated = false;
        if (latestPublishTime == null || publishTime.after(latestPublishTime)) {
            latestPublishTime = publishTime;
            updated = true;
        }
        if (oldestPublishTime == null || publishTime.before(oldestPublishTime)) {
            oldestPublishTime = publishTime;
            updated = true;
        }
        if (updated) {
            saveMetadata();
        }
    }

    /**
     * Loads metadata (latest and oldest publish times) from the metadata file for the current domain.
     */
    private void loadMetadata() {
        File metadataFile = new File(metadataFilePath);
        if (!metadataFile.exists()) {
            latestPublishTime = null;
            oldestPublishTime = null;
            return;
        }

        try (FileReader reader = new FileReader(metadataFile)) {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> metadata = mapper.readValue(reader, Map.class);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            if (metadata.containsKey("latestPublishTime")) {
                latestPublishTime = dateFormat.parse(metadata.get("latestPublishTime"));
            }
            if (metadata.containsKey("oldestPublishTime")) {
                oldestPublishTime = dateFormat.parse(metadata.get("oldestPublishTime"));
            }
        } catch (Exception e) {
            logger.error("Error loading metadata for domain: {}", ConfigLoader.getCurrentDomain().getName(), e);
            latestPublishTime = null;
            oldestPublishTime = null;
        }
    }

    /**
     * Saves metadata (latest and oldest publish times) to the metadata file for the current domain.
     */
    private void saveMetadata() {
        try {
            File dir = new File(BASE_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            Map<String, String> metadata = new HashMap<>();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            if (latestPublishTime != null) {
                metadata.put("latestPublishTime", dateFormat.format(latestPublishTime));
            }
            if (oldestPublishTime != null) {
                metadata.put("oldestPublishTime", dateFormat.format(oldestPublishTime));
            }

            ObjectMapper mapper = new ObjectMapper();
            try (FileWriter writer = new FileWriter(metadataFilePath)) {
                mapper.writeValue(writer, metadata);
            }
        } catch (Exception e) {
            logger.error("Error saving metadata for domain: {}", ConfigLoader.getCurrentDomain().getName(), e);
        }
    }

    /**
     * Gets the latest publish time of stored articles for the current domain.
     */
    public Date getLatestPublishTime() {
        return latestPublishTime;
    }

    /**
     * Gets the oldest publish time of stored articles for the current domain.
     */
    public Date getOldestPublishTime() {
        return oldestPublishTime;
    }

    /**
     * Checks if an article file already exists based on its publish time and timestamp.
     *
     * @param article The article to check
     * @return true if the article exists, false otherwise
     */
    public boolean articleExists(Article article) {
        try {
            String filePath = buildFilePath(article);
            if (filePath == null) {
                return false;
            }
            File file = new File(filePath);
            return file.exists();
        } catch (Exception e) {
            logger.error("Error checking if article exists: {}", article.getUrl(), e);
            return false;
        }
    }

    /**
     * Builds the file path for an article based on its domain, category, publish time, and timestamp.
     *
     * @param article The article
     * @return The file path, or null if the article is invalid
     */
    private String buildFilePath(Article article) {
        String domain = ConfigLoader.getCurrentDomain().getName();
        String category = article.getCategory();
        if (category == null) {
            logger.warn("Category is null for article: {}", article.getUrl());
            category = "unknown";
        }
        category = category.toLowerCase().replaceAll("[^a-z0-9-]", "-");

        Date publishTime = article.getPublishTime();
        if (publishTime == null) {
            logger.warn("Publish time is null for article: {}", article.getUrl());
            return null;
        }

        String timestamp = extractTimestampFromUrl(article.getUrl());
        if (timestamp == null) {
            logger.warn("Could not extract timestamp from URL: {}", article.getUrl());
            return null;
        }

        String year = YEAR_FORMAT.format(publishTime);
        String month = MONTH_FORMAT.format(publishTime);

        String dirPath = String.format("%s/%s/%s/%s/%s", BASE_DIR, domain, category, year, month);
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        return String.format("%s/%s.csv", dirPath, timestamp);
    }

    /**
     * Extracts the timestamp from the article URL based on the current domain's timestamp pattern.
     *
     * @param url The URL of the article
     * @return The timestamp (e.g., "20250411235700109" for Dân Trí, "4873099" for VnExpress)
     */
    private String extractTimestampFromUrl(String url) {
        Pattern pattern = Pattern.compile(ConfigLoader.getTimestampPattern());
        Matcher matcher = pattern.matcher(url);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        logger.warn("Could not extract timestamp from URL: {}", url);
        return null;
    }
}