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

            SchedulerFactory schedulerFactory = new StdSchedulerFactory();
            Scheduler scheduler = schedulerFactory.getScheduler();

            JobDetail job = JobBuilder.newJob(CrawlWebsiteJob.class)
                    .withIdentity("crawlJob", "default")
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

            logger.info("Scheduler started. Crawling will run every 5 minutes.");
        } catch (Exception e) {
            logger.error("Error starting the crawler", e);
        }
    }
}