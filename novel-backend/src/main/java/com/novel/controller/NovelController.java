package com.novel.controller;

import com.novel.dto.ChapterNavItem;
import com.novel.dto.ReorderChaptersRequest;
import com.novel.exception.ResourceNotFoundException;
import com.novel.model.Chapter;
import com.novel.model.Novel;
import com.novel.repository.DataRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

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
            throw new ResourceNotFoundException("Novel not found");
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

    @GetMapping("/chapters/{id}")
    @Operation(summary = "Get Chapter Content")
    public Chapter getChapter(@PathVariable Long id) {
        Chapter chapter = dataRepository.findChapterById(id);
        if (chapter == null) {
            throw new ResourceNotFoundException("Chapter not found");
        }
        return chapter;
    }

    @PutMapping("/novels/{id}/chapters/reorder")
    @Operation(summary = "Reorder Chapters (author)")
    public Map<String, Object> reorderChapters(@PathVariable Long id,
                                               @RequestBody ReorderChaptersRequest request) {
        List<Long> chapterIds = request == null ? null : request.getChapterIds();
        List<Chapter> chapters = dataRepository.reorderChapters(id, chapterIds);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "章节顺序已保存");
        response.put("chapters", chapters);
        return response;
    }

    @GetMapping("/chapters/{id}/navigation")
    @Operation(summary = "Get Previous/Next Chapter Navigation")
    public Map<String, Object> getChapterNavigation(@PathVariable Long id) {
        Chapter chapter = dataRepository.findChapterById(id);
        if (chapter == null) {
            throw new ResourceNotFoundException("Chapter not found");
        }

        Chapter[] neighbors = dataRepository.findNeighborChapters(id);
        Chapter previous = neighbors == null ? null : neighbors[0];
        Chapter next = neighbors == null ? null : neighbors[1];

        Map<String, Object> response = new HashMap<>();
        response.put("current", new ChapterNavItem(chapter));
        response.put("previous", previous == null ? null : new ChapterNavItem(previous));
        response.put("next", next == null ? null : new ChapterNavItem(next));
        response.put("novelId", chapter.getNovelId());
        return response;
    }
}
