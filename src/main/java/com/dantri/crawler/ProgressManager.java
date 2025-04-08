package com.dantri.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Quản lý tiến độ thu thập bài báo, lưu danh mục đã hoàn thành và danh mục đang xử lý
 */
public class ProgressManager {
    private static final Logger logger = LoggerFactory.getLogger(ProgressManager.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String PROGRESS_FILE = "progress.json";

    private List<String> completedCategories;
    private String currentCategory;

    public ProgressManager() {
        this.completedCategories = new ArrayList<>();
        this.currentCategory = null;
        loadProgress();
    }

    // Đọc completedCategories và currentCategory từ progress.json
    private void loadProgress() {
        try {
            File file = new File(PROGRESS_FILE);
            if (file.exists()) {
                JsonNode rootNode = mapper.readTree(file);
                ArrayNode completedArray = (ArrayNode) rootNode.get("completedCategories");
                if (completedArray != null) {
                    for (JsonNode node : completedArray) {
                        completedCategories.add(node.asText());
                    }
                }
                JsonNode currentNode = rootNode.get("currentCategory");
                if (currentNode != null) {
                    currentCategory = currentNode.asText();
                }
                logger.info("Loaded progress: {} categories completed, current category: {}", completedCategories.size(), currentCategory);
            } else {
                logger.info("No progress file found, starting fresh");
            }
        } catch (Exception e) {
            logger.error("Error loading progress", e);
        }
    }


     // Lưu completedCategories và currentCategory vào progress.json
    private void saveProgress() {
        try {
            ObjectNode rootNode = mapper.createObjectNode();
            ArrayNode completedArray = mapper.createArrayNode();
            for (String category : completedCategories) {
                completedArray.add(category);
            }
            rootNode.set("completedCategories", completedArray);
            if (currentCategory != null) {
                rootNode.put("currentCategory", currentCategory);
            }
            mapper.writeValue(new File(PROGRESS_FILE), rootNode);
            logger.info("Saved progress: {} categories completed, current category: {}", completedCategories.size(), currentCategory);
        } catch (Exception e) {
            logger.error("Error saving progress", e);
        }
    }

    public boolean isCategoryCompleted(String categoryUrl) {
        return completedCategories.contains(categoryUrl);
    }

    public void markCategoryCompleted(String categoryUrl) {
        if (!completedCategories.contains(categoryUrl)) {
            completedCategories.add(categoryUrl);
            currentCategory = null;
            saveProgress();
        }
    }

    public void setCurrentCategory(String categoryUrl) {
        this.currentCategory = categoryUrl;
        saveProgress();
    }

    public String getCurrentCategory() {
        return currentCategory;
    }

    public boolean areAllCategoriesCompleted(List<String> allCategories) {
        return new HashSet<>(completedCategories).containsAll(allCategories);
    }
}