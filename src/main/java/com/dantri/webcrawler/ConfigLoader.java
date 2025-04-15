package com.dantri.webcrawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Loads configuration from config.json.
 */
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final JsonNode config;
    private static final Domain currentDomain;

    static {
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream("config.json")) {
            if (input == null) {
                logger.error("Unable to find config.json");
                throw new RuntimeException("Unable to find config.json");
            }
            config = mapper.readTree(input);
            String domainName = config.get("currentDomain").asText();
            currentDomain = Domain.fromName(domainName);
            logger.info("Loaded config.json successfully. Current domain: {}", currentDomain.getName());
        } catch (Exception e) {
            logger.error("Error loading config.json", e);
            throw new RuntimeException("Error loading config.json", e);
        }
    }

    public static Domain getCurrentDomain() {
        return currentDomain;
    }

    public static String getStartUrl() {
        return getDomainConfig().get("startUrl").asText();
    }

    public static String getArticleUrlPattern() {
        return getDomainConfig().get("articleUrlPattern").asText();
    }

    public static String getCategoryUrlPattern() {
        return getDomainConfig().get("categoryUrlPattern").asText();
    }

    public static List<String> getContentSelectors() {
        JsonNode selectorsNode = getDomainConfig().get("contentSelectors");
        return Arrays.asList(mapper.convertValue(selectorsNode, String[].class));
    }

    public static List<String> getDefaultContentSelectors() {
        JsonNode selectorsNode = config.get("settings").get("defaultContentSelectors");
        return Arrays.asList(mapper.convertValue(selectorsNode, String[].class));
    }

    public static int getMaxUrlsPerCrawl() {
        return config.get("settings").get("maxUrlsPerCrawl").asInt();
    }

    public static int getDefaultMaxLevel() {
        return config.get("settings").get("defaultMaxLevel").asInt();
    }

    public static int getMaxLevelWithinSixMonths() {
        return config.get("settings").get("maxLevelWithinSixMonths").asInt();
    }

    public static long getSixMonthsMillis() {
        return config.get("settings").get("sixMonthsMillis").asLong();
    }

    public static long getRequestDelayMs() {
        return config.get("settings").get("requestDelayMs").asLong();
    }

    public static long getRetryDelayMs() {
        return config.get("settings").get("retryDelayMs").asLong();
    }

    public static int getMaxRetries() {
        return config.get("settings").get("maxRetries").asInt();
    }

    public static int getCategoryPosition() {
        return getDomainConfig().get("categoryPosition").asInt();
    }

    public static boolean isNestedArray() {
        return getDomainConfig().get("isNestedArray").asBoolean();
    }

    public static String getCategoryUrlField() {
        return getDomainConfig().get("categoryUrlField").asText();
    }

    public static String getCategoryUrlSuffix() {
        return getDomainConfig().get("categoryUrlSuffix").asText();
    }

    public static String getTimestampPattern() {
        return getDomainConfig().get("timestampPattern").asText();
    }

    public static String getDateFormatHandling() {
        return getDomainConfig().get("dateFormatHandling").asText();
    }

    public static String getDefaultOffset() {
        return getDomainConfig().get("defaultOffset").asText();
    }

    public static List<Integer> getRetryStatusCodes() {
        JsonNode codesNode = config.get("settings").get("retryStatusCodes");
        return Arrays.asList(mapper.convertValue(codesNode, Integer[].class));
    }

    private static JsonNode getDomainConfig() {
        JsonNode domains = config.get("domains");
        for (JsonNode domain : domains) {
            if (domain.get("name").asText().equals(currentDomain.getName())) {
                return domain;
            }
        }
        throw new RuntimeException("Configuration for domain " + currentDomain.getName() + " not found");
    }
}