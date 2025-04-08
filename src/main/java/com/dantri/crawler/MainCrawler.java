package com.dantri.crawler;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class MainCrawler {
    private static final Logger logger = LoggerFactory.getLogger(MainCrawler.class);
    private final CategoryCollector categoryCollector;
    private final UrlCollector urlCollector;
    private final ArticleParser articleParser;
    private final ArticleStorage articleStorage;
    private final ProgressManager progressManager;

    public MainCrawler() {
        this.categoryCollector = new CategoryCollector();
        this.urlCollector = new UrlCollector();
        this.articleParser = new ArticleParser();
        this.articleStorage = new ArticleStorage();
        this.progressManager = new ProgressManager();
    }

    public void start() {
        logger.info("Starting MainCrawler");

        // Thu thập các danh mục
        List<String> categoryUrls = categoryCollector.collectCategories();

        // Kiểm tra xem tất cả danh mục đã hoàn thành chưa
        if (progressManager.areAllCategoriesCompleted(categoryUrls)) {
            logger.info("All categories have been processed. Switching to monitoring mode...");
            startMonitoringMode();
            return;
        }

        // Xác định điểm bắt đầu
        String currentCategory = progressManager.getCurrentCategory();
        boolean startProcessing = (currentCategory == null);

        for (String categoryUrl : categoryUrls) {
            if (progressManager.isCategoryCompleted(categoryUrl)) {
                logger.info("Skipping completed category: {}", categoryUrl);
                continue;
            }

            // Nếu chưa bắt đầu xử lý, kiểm tra xem có phải danh mục đang dở không
            if (!startProcessing && !categoryUrl.equals(currentCategory)) {
                logger.info("Skipping category, waiting for current category: {}", categoryUrl);
                continue;
            }
            startProcessing = true;

            logger.info("Processing category: {}", categoryUrl);

            progressManager.setCurrentCategory(categoryUrl);

            Set<String> articleUrls = urlCollector.collectUrls(categoryUrl);

            // Phân tích và lưu bài viết
            for (String url : articleUrls) {
                Article article = articleParser.parseArticle(url);
                if (article != null) {
                    articleStorage.saveArticle(article);
                }
            }

            progressManager.markCategoryCompleted(categoryUrl);
        }

        logger.info("Initial crawling completed. Switching to monitoring mode...");
        startMonitoringMode();
    }

    private void startMonitoringMode() {
        try {
            // Khởi tạo Quartz Scheduler
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

            JobDetail job = JobBuilder.newJob(CrawlNewArticlesJob.class)
                    .withIdentity("crawlNewArticlesJob", "group1")
                    .build();

            // Lập lịch chạy mỗi 5 phút
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("crawlNewArticlesTrigger", "group1")
                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMinutes(5)
                            .repeatForever())
                    .build();

            scheduler.scheduleJob(job, trigger);
            scheduler.start();

            logger.info("Started monitoring mode. Checking for new articles every 5 minutes...");

            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Error starting monitoring mode", e);
        }
    }

    public static void main(String[] args) {
        MainCrawler crawler = new MainCrawler();
        crawler.start();
    }
}