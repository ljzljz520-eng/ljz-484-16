package com.novel.dto;

import java.util.List;

/**
 * Request body for reordering a novel's chapters.
 * chapterIds is the complete list of chapter IDs in the desired reading order.
 */
public class ReorderChaptersRequest {
    private List<Long> chapterIds;

    public List<Long> getChapterIds() {
        return chapterIds;
    }

    public void setChapterIds(List<Long> chapterIds) {
        this.chapterIds = chapterIds;
    }
}
