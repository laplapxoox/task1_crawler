package com.dantri.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lọc bài viết dựa trên ngày tháng trích xuất từ URL.
 */
public class DateFilter {
    private static final Logger logger = LoggerFactory.getLogger(DateFilter.class);
    private static final Pattern URL_DATE_PATTERN = Pattern.compile(".*-(\\d{8})\\d+\\.htm");

    public static boolean isUrlWithinSixMonths(String url) {
        try {
            Matcher matcher = URL_DATE_PATTERN.matcher(url);
            if (matcher.matches()) {
                String dateStr = matcher.group(1);
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                Date date = dateFormat.parse(dateStr);

                // Tính ngày cách đây 6 tháng
                long sixMonthsAgoMillis = System.currentTimeMillis() - (6L * 30 * 24 * 60 * 60 * 1000); // 6 tháng
                Date sixMonthsAgo = new Date(sixMonthsAgoMillis);

                // So sánh
                Date currentDate = new Date();
                boolean isWithinRange = date.after(sixMonthsAgo) && date.before(currentDate);
                return isWithinRange;
            }
        } catch (Exception e) {
            logger.error("Error parsing date from URL: {}", url, e);
        }
        return false;
    }
}