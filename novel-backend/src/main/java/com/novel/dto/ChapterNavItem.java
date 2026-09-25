package com.novel.dto;

import com.novel.model.Chapter;

/**
 * Lightweight chapter information used for reading navigation
 * (previous / next chapter). Excludes the content payload.
 */
public class ChapterNavItem {
    private Long id;
    private String title;
    private Integer orderNo;

    public ChapterNavItem() {
    }

    public ChapterNavItem(Chapter chapter) {
        this.id = chapter.getId();
        this.title = chapter.getTitle();
        this.orderNo = chapter.getOrderNo();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(Integer orderNo) {
        this.orderNo = orderNo;
    }
}
