package com.novel.repository;

import com.novel.exception.BadRequestException;
import com.novel.exception.ResourceNotFoundException;
import com.novel.model.Chapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DataRepositoryReorderTest {

    private DataRepository repository;
    private Long novelId;

    @BeforeEach
    void setUp() {
        repository = new DataRepository();
        repository.init();
        novelId = repository.findAllNovels(null, 1, 100).stream()
                .filter(n -> "星际穿越之编程大师".equals(n.getTitle()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    @DisplayName("reorderChapters rewrites order numbers following the submitted ID order")
    void reorderAppliesNewOrder() {
        List<Chapter> original = repository.findChaptersByNovelId(novelId);
        // Seeded order: 序章, 第一章, 第二章, 番外, 第三章
        int sideStoryIndex = (int) original.stream()
                .map(Chapter::getTitle)
                .takeWhile(title -> !title.startsWith("番外"))
                .count();
        assertEquals(3, sideStoryIndex);

        // Move the misplaced side story (番外) to the end.
        List<Long> desiredIds = new ArrayList<>(original.stream().map(Chapter::getId).toList());
        Long sideStoryId = desiredIds.remove(sideStoryIndex);
        desiredIds.add(sideStoryId);

        List<Chapter> reordered = repository.reorderChapters(novelId, desiredIds);

        List<String> newTitles = reordered.stream().map(Chapter::getTitle).toList();
        assertEquals(List.of("序章：绿色的黎明", "第一章：Hello World", "第二章：变量声明",
                "第三章：循环陷阱", "番外：穿越前的世界"), newTitles);
        // Order numbers are contiguous and start at 1.
        for (int i = 0; i < reordered.size(); i++) {
            assertEquals(i + 1, reordered.get(i).getOrderNo());
        }
    }

    @Test
    @DisplayName("catalogue order changes consistently with the saved reorder")
    void catalogueFollowsNewOrder() {
        List<Chapter> original = repository.findChaptersByNovelId(novelId);
        List<Long> reversed = new ArrayList<>(original.stream().map(Chapter::getId).toList());
        java.util.Collections.reverse(reversed);

        repository.reorderChapters(novelId, reversed);

        List<Long> catalogueIds = repository.findChaptersByNovelId(novelId).stream()
                .map(Chapter::getId).toList();
        assertEquals(reversed, catalogueIds);
    }

    @Test
    @DisplayName("prev/next navigation follows the newly saved order")
    void navigationFollowsNewOrder() {
        List<Chapter> original = repository.findChaptersByNovelId(novelId);
        List<Long> reversed = new ArrayList<>(original.stream().map(Chapter::getId).toList());
        java.util.Collections.reverse(reversed);
        repository.reorderChapters(novelId, reversed);

        Chapter[] neighbors = repository.findNeighborChapters(original.get(2).getId());
        assertEquals(original.get(3).getId(), neighbors[0].getId()); // previous in reversed order
        assertEquals(original.get(1).getId(), neighbors[1].getId()); // next in reversed order
    }

    @Test
    @DisplayName("edge chapters have null neighbours")
    void edgeChaptersHaveNoNeighbour() {
        List<Chapter> ordered = repository.findChaptersByNovelId(novelId);
        Chapter[] firstNeighbors = repository.findNeighborChapters(ordered.get(0).getId());
        assertNull(firstNeighbors[0]);
        assertNotNull(firstNeighbors[1]);

        Chapter[] lastNeighbors = repository.findNeighborChapters(
                ordered.get(ordered.size() - 1).getId());
        assertNotNull(lastNeighbors[0]);
        assertNull(lastNeighbors[1]);
    }

    @Test
    @DisplayName("invalid novel id is rejected before any mutation")
    void unknownNovelRejected() {
        assertThrows(ResourceNotFoundException.class,
                () -> repository.reorderChapters(999999L, List.of(1L, 2L)));
    }

    @Test
    @DisplayName("missing, duplicate or foreign chapter ids are rejected without mutation")
    void invalidChapterSetRejectedAndOrderPreserved() {
        List<Chapter> before = repository.findChaptersByNovelId(novelId);
        Map<Long, Integer> orderBefore = before.stream()
                .collect(Collectors.toMap(Chapter::getId, Chapter::getOrderNo));

        List<Long> ids = before.stream().map(Chapter::getId).collect(Collectors.toCollection(ArrayList::new));

        // wrong size
        assertThrows(BadRequestException.class,
                () -> repository.reorderChapters(novelId, ids.subList(0, 1)));

        // duplicate id (replace the last element with a copy of the first)
        List<Long> duplicated = new ArrayList<>(ids);
        duplicated.set(duplicated.size() - 1, duplicated.get(0));
        assertThrows(BadRequestException.class,
                () -> repository.reorderChapters(novelId, duplicated));

        // foreign chapter id (belongs to another seeded novel)
        Long otherNovelId = repository.findAllNovels(null, 1, 100).stream()
                .filter(n -> !n.getId().equals(novelId))
                .findFirst()
                .orElseThrow()
                .getId();
        Long foreignId = repository.findChaptersByNovelId(otherNovelId).stream()
                .map(Chapter::getId)
                .findFirst()
                .orElseThrow();
        List<Long> withForeign = new ArrayList<>(ids);
        withForeign.set(0, foreignId);
        assertThrows(BadRequestException.class,
                () -> repository.reorderChapters(novelId, withForeign));

        // original order must be intact after every failed attempt
        List<Chapter> after = repository.findChaptersByNovelId(novelId);
        assertEquals(before.size(), after.size());
        for (Chapter chapter : after) {
            assertEquals(orderBefore.get(chapter.getId()), chapter.getOrderNo());
        }
    }
}
