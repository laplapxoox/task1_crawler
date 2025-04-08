package com.dantri.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quản lý lưu bài viết vào file CSV, tổ chức theo danh mục, năm, tháng.
 */
public class ArticleStorage {
    private static final Logger logger = LoggerFactory.getLogger(ArticleStorage.class);
    private static final String BASE_DIR = "data";
    private static final String METADATA_FILE = "data/metadata.json";
    private static final SimpleDateFormat YEAR_FORMAT = new SimpleDateFormat("yyyy");
    private static final SimpleDateFormat MONTH_FORMAT = new SimpleDateFormat("MM");
    private static final ObjectMapper mapper = new ObjectMapper();

    private Date latestPublishTime;
    private Date lastCrawlTime;

    public ArticleStorage() {
        loadMetadata();
    }

    public boolean saveArticle(Article article) {
        try {
            // Xác định danh mục từ URL
            String category = extractCategory(article.getUrl());
            if (category == null) {
                logger.warn("Could not determine category for URL: {}", article.getUrl());
                return false;
            }

            // Xác định năm và tháng từ publishTime
            String year = YEAR_FORMAT.format(article.getPublishTime());
            String month = MONTH_FORMAT.format(article.getPublishTime());

            // Tạo thư mục nếu chưa có
            String dirPath = String.format("%s/%s/%s/%s", BASE_DIR, category, year, month);
            File directory = new File(dirPath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Kiểm tra file đã tồn tại chưa
            String fileName = extractTimestampFromUrl(article.getUrl()) + ".csv";
            String filePath = dirPath + "/" + fileName;
            File file = new File(filePath);
            if (file.exists()) {
                logger.debug("Article already exists, skipping: {}", article.getUrl());
                return false;
            }

            // Ghi bài viết vào file CSV
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

            // Cập nhật thời gian mới nhất
            if (latestPublishTime == null || article.getPublishTime().after(latestPublishTime)) {
                latestPublishTime = article.getPublishTime();
                saveMetadata();
            }

            return true;
        } catch (Exception e) {
            logger.error("Error saving article: {}", article.getUrl(), e);
            return false;
        }
    }

    /**
     * Updates the last crawl time.
     */
    public void updateLastCrawlTime() {
        this.lastCrawlTime = new Date();
        saveMetadata();
    }


     // Đọc latestPublishTime và lastCrawlTime từ metadata.json
    private void loadMetadata() {
        try {
            File file = new File(METADATA_FILE);
            if (file.exists()) {
                JsonNode rootNode = mapper.readTree(file);
                String latestTimeStr = rootNode.get("latestPublishTime").asText();
                latestPublishTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(latestTimeStr);
                logger.info("Loaded latest publish time: {}", latestPublishTime);

                String lastCrawlTimeStr = rootNode.get("lastCrawlTime") != null ? rootNode.get("lastCrawlTime").asText() : null;
                if (lastCrawlTimeStr != null) {
                    lastCrawlTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(lastCrawlTimeStr);
                    logger.info("Loaded last crawl time: {}", lastCrawlTime);
                }
            } else {
                logger.info("No metadata file found, starting fresh");
            }
        } catch (Exception e) {
            logger.error("Error loading metadata", e);
        }
    }

    // Lưu latestPublishTime và lastCrawlTime vào metadata.json
    private void saveMetadata() {
        try {
            File file = new File(METADATA_FILE);
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            JsonNode rootNode = mapper.createObjectNode();
            ((ObjectNode) rootNode).put("latestPublishTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(latestPublishTime));
            if (lastCrawlTime != null) {
                ((ObjectNode) rootNode).put("lastCrawlTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(lastCrawlTime));
            }
            mapper.writeValue(file, rootNode);
            logger.info("Saved metadata - latest publish time: {}, last crawl time: {}", latestPublishTime, lastCrawlTime);
        } catch (Exception e) {
            logger.error("Error saving metadata", e);
        }
    }

    public Date getLatestPublishTime() {
        return latestPublishTime;
    }

    public Date getLastCrawlTime() {
        return lastCrawlTime;
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
        Pattern pattern = Pattern.compile(".*-(\\d+)\\.htm");
        Matcher matcher = pattern.matcher(url);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }
}