package com.dantri.crawler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Sử dụng Quartz để chạy định kỳ kiểm tra bài viết mới
 */
@DisallowConcurrentExecution
public class CrawlNewArticlesJob implements Job {
    private static final Logger logger = LoggerFactory.getLogger(CrawlNewArticlesJob.class);
    private final CategoryCollector categoryCollector;
    private final UrlCollector urlCollector;
    private final ArticleParser articleParser;
    private final ArticleStorage articleStorage;

    public CrawlNewArticlesJob() {
        this.categoryCollector = new CategoryCollector();
        this.urlCollector = new UrlCollector();
        this.articleParser = new ArticleParser();
        this.articleStorage = new ArticleStorage();
    }

    @Override
    public void execute(JobExecutionContext context) {
        logger.info("Checking for new articles...");

        List<String> categoryUrls = categoryCollector.collectCategories();

        Date latestPublishTime = articleStorage.getLatestPublishTime();
        Date lastCrawlTime = articleStorage.getLastCrawlTime();
        if (latestPublishTime == null) {
            logger.warn("No articles found in storage, please run initial crawl first");
            return;
        }

        // Kiểm tra bài viết mới từ các danh mục
        for (String categoryUrl : categoryUrls) {
            logger.info("Checking new articles in category: {}", categoryUrl);

            try {
                // Tìm trang phân trang đầu tiên có bài viết cũ hơn lastCrawlTime
                int startPage = 1;
                if (lastCrawlTime != null) {
                    startPage = findStartPage(categoryUrl, lastCrawlTime);
                }

                // Duyệt ngược từ startPage về trang 1
                for (int page = startPage; page >= 1; page--) {
                    Set<String> articleUrls = urlCollector.collectUrlsFromPage(categoryUrl, page);
                    for (String url : articleUrls) {
                        Article article = articleParser.parseArticle(url);
                        if (article != null && article.getPublishTime().after(latestPublishTime)) {
                            logger.info("Found new article: {}", article.getTitle());
                            articleStorage.saveArticle(article);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error checking new articles in category: {}", categoryUrl, e);
            }
        }

        // Cập nhật thời gian lần cuối chạy
        articleStorage.updateLastCrawlTime();
        logger.info("Finished checking for new articles");
    }


     // Tìm trang phân trang đầu tiên có bài viết cũ hơn lastCrawlTime
    private int findStartPage(String categoryUrl, Date lastCrawlTime) {
        int page = 1;
        while (true) {
            Set<String> articleUrls = urlCollector.collectUrlsFromPage(categoryUrl, page);
            if (articleUrls.isEmpty()) {
                logger.info("No more articles found on page {}, starting from page {}", page, page - 1);
                return Math.max(1, page - 1);
            }

            boolean hasOlderArticle = false;
            for (String url : articleUrls) {
                try {
                    String dateStr = url.substring(url.lastIndexOf("-") + 1, url.lastIndexOf("-") + 9);
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                    Date articleDate = dateFormat.parse(dateStr);
                    if (articleDate.before(lastCrawlTime)) {
                        hasOlderArticle = true;
                        break;
                    }
                } catch (Exception e) {
                    logger.error("Error parsing date from URL: {}", url, e);
                }
            }

            if (hasOlderArticle) {
                logger.info("Found older article on page {}, starting from this page", page);
                return page;
            }

            page++;
        }
    }
}