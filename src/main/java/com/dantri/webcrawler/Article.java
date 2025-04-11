package com.dantri.webcrawler;

import java.util.Date;

public class Article {
    private String url;
    private String title;
    private String description;
    private String content;
    private Date publishTime;
    private String author;

    public Article() {
    }

    public Article(String url, String title, String description, String content, Date publishTime, String author) {
        this.url = url;
        this.title = title;
        this.description = description;
        this.content = content;
        this.publishTime = publishTime;
        this.author = author;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(Date publishTime) {
        this.publishTime = publishTime;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @Override
    public String toString() {
        return "Article{" +
                "url='" + url + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", content='" + content + '\'' +
                ", publishTime=" + publishTime +
                ", author='" + author + '\'' +
                '}';
    }
}