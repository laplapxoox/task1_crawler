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
    private static final String METADATA_FILE = "data/metadata.json";
    private static final SimpleDateFormat YEAR_FORMAT = new SimpleDateFormat("yyyy");
    private static final SimpleDateFormat MONTH_FORMAT = new SimpleDateFormat("MM");
    private static final Pattern URL_TIMESTAMP_PATTERN = Pattern.compile(".*-(\\d{17})\\.htm");
    private static final Pattern URL_CATEGORY_PATTERN = Pattern.compile("https://dantri\\.com\\.vn/([^/]+)/.*");
    private Date latestPublishTime; // Thời gian xuất bản mới nhất của bài viết đã lưu
    private Date oldestPublishTime; // Thời gian xuất bản cũ nhất của bài viết đã lưu

    public ArticleStorage() {
        loadMetadata();
    }

    public boolean saveArticle(Article article) {
        try {
            String category = extractCategory(article.getUrl());
            if (category == null) {
                logger.warn("Could not determine category for URL: {}", article.getUrl());
                return false;
            }

            Date publishTime = article.getPublishTime();
            if (publishTime == null) {
                logger.warn("Publish time is null for article: {}", article.getUrl());
                return false;
            }

            String timestamp = extractTimestampFromUrl(article.getUrl());
            if (timestamp == null) {
                logger.warn("Could not extract timestamp from URL: {}", article.getUrl());
                return false;
            }

            String year = YEAR_FORMAT.format(publishTime);
            String month = MONTH_FORMAT.format(publishTime);

            String dirPath = String.format("%s/%s/%s/%s", BASE_DIR, category, year, month);
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filePath = String.format("%s/%s.csv", dirPath, timestamp);
            File file = new File(filePath);
            if (file.exists()) {
                logger.debug("Article already exists, skipping: {}", article.getUrl());
                return false;
            }

            try (CSVWriter writer = new CSVWriter(new FileWriter(filePath))) {
                String[] header = {"url", "title", "description", "content", "publishTime", "author"};
                writer.writeNext(header);

                String[] data = {
                        article.getUrl(),
                        article.getTitle(),
                        article.getDescription(),
                        article.getContent(),
                        article.getPublishTime().toString(),
                        article.getAuthor()
                };
                writer.writeNext(data);
            }

            updateMetadata(article.getPublishTime());
            return true;
        } catch (Exception e) {
            logger.error("Error saving article: {}", article.getUrl(), e);
            return false;
        }
    }

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


    private void loadMetadata() {
        File metadataFile = new File(METADATA_FILE);
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
            logger.error("Error loading metadata", e);
            latestPublishTime = null;
            oldestPublishTime = null;
        }
    }

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
            try (FileWriter writer = new FileWriter(METADATA_FILE)) {
                mapper.writeValue(writer, metadata);
            }
        } catch (Exception e) {
            logger.error("Error saving metadata", e);
        }
    }

    public Date getLatestPublishTime() {
        return latestPublishTime;
    }

    public Date getOldestPublishTime() {
        return oldestPublishTime;
    }

    private String extractCategory(String url) {
        Matcher matcher = URL_CATEGORY_PATTERN.matcher(url);
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