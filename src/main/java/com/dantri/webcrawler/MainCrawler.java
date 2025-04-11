package com.dantri.webcrawler;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainCrawler {
    private static final Logger logger = LoggerFactory.getLogger(MainCrawler.class);

    public static void main(String[] args) {
        try {
            LinkExtractor linkExtractor = new LinkExtractor();
            ArticleParser articleParser = new ArticleParser();
            ArticleStorage articleStorage = new ArticleStorage();
            VisitedUrlsManager visitedUrlsManager = new VisitedUrlsManager();
            WebCrawler webCrawler = new WebCrawler(linkExtractor, articleParser, articleStorage, visitedUrlsManager);

            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

            JobDetail job = JobBuilder.newJob(CrawlWebsiteJob.class)
                    .withIdentity("crawlWebsiteJob", "default")
                    .build();

            job.getJobDataMap().put("webCrawler", webCrawler);

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("crawlTrigger", "default")
                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMinutes(5)
                            .repeatForever())
                    .build();

            scheduler.scheduleJob(job, trigger);
            scheduler.start();

            logger.info("Web crawler started");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    scheduler.shutdown(true);
                    logger.info("Scheduler shut down successfully.");
                } catch (SchedulerException e) {
                    logger.error("Error shutting down scheduler", e);
                }
            }));
        } catch (SchedulerException e) {
            logger.error("Error starting web crawler", e);
        }
    }
}