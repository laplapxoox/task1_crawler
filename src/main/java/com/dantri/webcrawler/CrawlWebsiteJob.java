package com.dantri.webcrawler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Quartz Job lập lịch chạy WebCrawler mỗi 5 phút
 */
@DisallowConcurrentExecution
public class CrawlWebsiteJob implements Job {
    private static final Logger logger = LoggerFactory.getLogger(CrawlWebsiteJob.class);
    private static final ReentrantLock lock = new ReentrantLock();

    @Override
    public void execute(JobExecutionContext context) {
        if(!lock.tryLock()) {
            logger.info("Another crawl job is already running, skipping this execution.");
            return;
        }

        try {
            logger.info("Starting scheduled crawl job...");

            WebCrawler crawler = (WebCrawler) context.getJobDetail().getJobDataMap().get("webCrawler");
            if (crawler == null) {
                logger.error("WebCrawler not found in JobDataMap.");
                return;
            }

            crawler.crawl();

            logger.info("Finished scheduled crawl job.");
        } finally {
            lock.unlock();
        }
    }
}