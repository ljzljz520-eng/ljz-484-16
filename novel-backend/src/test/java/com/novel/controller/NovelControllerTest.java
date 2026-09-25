package com.novel.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NovelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reorderChapters_returnsNewOrder() throws Exception {
        mockMvc.perform(put("/api/novels/1/chapters/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chapterIds\":[3,1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapters[*].id", contains(3, 1, 2)))
                .andExpect(jsonPath("$.chapters[*].orderNo", contains(1, 2, 3)));

        // 目录接口跟随新顺序
        mockMvc.perform(get("/api/novels/1/chapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(3, 1, 2)));
    }

    @Test
    void reorderChapters_invalidList_returns400AndKeepsOriginalOrder() throws Exception {
        // 缺少章节 3，且混入了不属于小说 1 的章节 4
        mockMvc.perform(put("/api/novels/1/chapters/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chapterIds\":[2,1,4]}"))
                .andExpect(status().isBadRequest());

        // 关键断言：保存失败后目录保持原顺序，没有半更新
        mockMvc.perform(get("/api/novels/1/chapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(1, 2, 3)))
                .andExpect(jsonPath("$[*].orderNo", contains(1, 2, 3)));
    }

    @Test
    void reorderChapters_missingBody_returns400() throws Exception {
        mockMvc.perform(put("/api/novels/1/chapters/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/novels/1/chapters"))
                .andExpect(jsonPath("$[*].id", contains(1, 2, 3)));
    }

    @Test
    void reorderChapters_unknownNovel_returns404() throws Exception {
        mockMvc.perform(put("/api/novels/999/chapters/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chapterIds\":[1,2,3]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getChapter_exposesPrevNextNavigation() throws Exception {
        // 初始顺序 1 -> 2 -> 3
        mockMvc.perform(get("/api/chapters/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapter.id").value(1))
                .andExpect(jsonPath("$.prevChapterId").doesNotExist())
                .andExpect(jsonPath("$.nextChapterId").value(2));

        mockMvc.perform(get("/api/chapters/3"))
                .andExpect(jsonPath("$.prevChapterId").value(2))
                .andExpect(jsonPath("$.nextChapterId").doesNotExist());
    }

    @Test
    void prevNextNavigation_followsReorderedSequence() throws Exception {
        // 重排为 3 -> 1 -> 2（模拟把错位章节移到正确位置）
        mockMvc.perform(put("/api/novels/1/chapters/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chapterIds\":[3,1,2]}"))
                .andExpect(status().isOk());

        // 上一章/下一章必须按新顺序计算
        mockMvc.perform(get("/api/chapters/3"))
                .andExpect(jsonPath("$.prevChapterId").doesNotExist())
                .andExpect(jsonPath("$.nextChapterId").value(1));

        mockMvc.perform(get("/api/chapters/1"))
                .andExpect(jsonPath("$.prevChapterId").value(3))
                .andExpect(jsonPath("$.nextChapterId").value(2));

        mockMvc.perform(get("/api/chapters/2"))
                .andExpect(jsonPath("$.prevChapterId").value(1))
                .andExpect(jsonPath("$.nextChapterId").doesNotExist());
    }

    @Test
    void getChapter_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/chapters/999"))
                .andExpect(status().isNotFound());
    }
}
