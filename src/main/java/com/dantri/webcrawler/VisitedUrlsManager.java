package com.dantri.webcrawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Quản lý các Url bài bào đã thăm và lưu lại
 */
public class VisitedUrlsManager {
    private static final Logger logger = LoggerFactory.getLogger(VisitedUrlsManager.class);
    private static final String VISITED_URLS_FILE = "data/visited_urls.txt";
    private final Set<String> visitedUrls;

    public VisitedUrlsManager() {
        visitedUrls = new HashSet<>();
        loadVisitedUrls();
    }

    /**
     * Loads visited URLs from the file.
     */
    private void loadVisitedUrls() {
        File file = new File(VISITED_URLS_FILE);
        if (!file.exists()) {
            logger.info("Visited URLs file does not exist, starting with an empty set.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    visitedUrls.add(line);
                }
            }
            logger.info("Loaded {} visited URLs from file.", visitedUrls.size());
        } catch (IOException e) {
            logger.error("Error loading visited URLs from file: {}", VISITED_URLS_FILE, e);
        }
    }

    public void addVisitedUrl(String url) {
        if (visitedUrls.add(url)) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(VISITED_URLS_FILE, true))) {
                writer.write(url);
                writer.newLine();
            } catch (IOException e) {
                logger.error("Error writing URL to visited URLs file: {}", url, e);
            }
        }
    }

    public boolean isVisited(String url) {
        return visitedUrls.contains(url);
    }
}