package com.novel.controller;

import com.novel.model.Chapter;
import com.novel.model.Novel;
import com.novel.repository.DataRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow frontend to access
@Tag(name = "Novel System API")
public class NovelController {

    private final DataRepository dataRepository;

    public NovelController(DataRepository dataRepository) {
        this.dataRepository = dataRepository;
    }

    @GetMapping("/novels")
    @Operation(summary = "Get Novel List")
    public Map<String, Object> getNovels(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        List<Novel> list = dataRepository.findAllNovels(keyword, page, size);
        long total = dataRepository.countNovels(keyword);

        Map<String, Object> response = new HashMap<>();
        response.put("data", list);
        response.put("total", total);
        response.put("page", page);
        response.put("size", size);
        return response;
    }

    @GetMapping("/novels/{id}")
    @Operation(summary = "Get Novel Details (with chapters)")
    public Map<String, Object> getNovelDetail(@PathVariable Long id) {
        Novel novel = dataRepository.findNovelById(id);
        if (novel == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Novel not found");
        }
        List<Chapter> chapters = dataRepository.findChaptersByNovelId(id);

        Map<String, Object> response = new HashMap<>();
        response.put("novel", novel);
        response.put("chapters", chapters); // Include chapters as requested ("merge directory into detail")
        return response;
    }

    @GetMapping("/novels/{id}/chapters")
    @Operation(summary = "Get Chapters for a Novel")
    public List<Chapter> getChapters(@PathVariable Long id) {
        return dataRepository.findChaptersByNovelId(id);
    }

    @PutMapping("/novels/{id}/chapters/order")
    @Operation(summary = "Reorder Chapters (atomic: all-or-nothing)")
    public Map<String, Object> reorderChapters(@PathVariable Long id,
            @RequestBody Map<String, List<Long>> body) {
        Novel novel = dataRepository.findNovelById(id);
        if (novel == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Novel not found");
        }
        List<Long> chapterIds = body != null ? body.get("chapterIds") : null;

        List<Chapter> reordered;
        try {
            // Repository 内部先校验后写入：失败时抛出异常且原顺序保持不变
            reordered = dataRepository.reorderChapters(id, chapterIds);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("chapters", reordered);
        return response;
    }

    @GetMapping("/chapters/{id}")
    @Operation(summary = "Get Chapter Content (with prev/next navigation)")
    public Map<String, Object> getChapter(@PathVariable Long id) {
        Chapter chapter = dataRepository.findChapterById(id);
        if (chapter == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapter not found");
        }
        // 上一章/下一章基于最新 orderNo 计算，章节重排后导航自动跟随
        Chapter prev = dataRepository.findPrevChapter(chapter);
        Chapter next = dataRepository.findNextChapter(chapter);

        Map<String, Object> response = new HashMap<>();
        response.put("chapter", chapter);
        response.put("prevChapterId", prev != null ? prev.getId() : null);
        response.put("nextChapterId", next != null ? next.getId() : null);
        return response;
    }
}
