package com.novel.repository;

import com.novel.model.Chapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataRepositoryTest {

    private DataRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DataRepository();
        repository.init(); // 手动触发 @PostConstruct 种子数据
    }

    private List<Long> orderOf(Long novelId) {
        return repository.findChaptersByNovelId(novelId).stream().map(Chapter::getId).toList();
    }

    @Test
    void reorderChapters_appliesNewOrderAndRenumbersOrderNo() {
        // 小说 1 原有章节 [1, 2, 3]，把"第三章"移到最前（模拟番外/错位章节归位）
        List<Chapter> result = repository.reorderChapters(1L, List.of(3L, 1L, 2L));

        assertEquals(List.of(3L, 1L, 2L), result.stream().map(Chapter::getId).toList());
        assertEquals(List.of(1, 2, 3), result.stream().map(Chapter::getOrderNo).toList());
        // 目录查询同样按新顺序返回
        assertEquals(List.of(3L, 1L, 2L), orderOf(1L));
    }

    @Test
    void reorderChapters_incompleteList_throwsAndKeepsOriginalOrder() {
        List<Long> before = orderOf(1L);

        // 只提交了部分章节
        assertThrows(IllegalArgumentException.class,
                () -> repository.reorderChapters(1L, List.of(2L, 1L)));

        // 关键断言：失败后原顺序完全不变，不允许半更新
        assertEquals(before, orderOf(1L));
        assertEquals(List.of(1, 2, 3),
                repository.findChaptersByNovelId(1L).stream().map(Chapter::getOrderNo).toList());
    }

    @Test
    void reorderChapters_foreignChapterId_throwsAndKeepsOriginalOrder() {
        List<Long> before = orderOf(1L);

        // 章节 4 属于小说 2，混入小说 1 的重排请求
        assertThrows(IllegalArgumentException.class,
                () -> repository.reorderChapters(1L, List.of(1L, 2L, 4L)));

        assertEquals(before, orderOf(1L));
        // 小说 2 的章节也不受影响
        assertEquals(List.of(4L, 5L), orderOf(2L));
    }

    @Test
    void reorderChapters_duplicateIds_throwsAndKeepsOriginalOrder() {
        List<Long> before = orderOf(1L);

        assertThrows(IllegalArgumentException.class,
                () -> repository.reorderChapters(1L, List.of(1L, 1L, 2L)));

        assertEquals(before, orderOf(1L));
    }

    @Test
    void reorderChapters_nullOrEmptyList_throwsAndKeepsOriginalOrder() {
        List<Long> before = orderOf(1L);

        assertThrows(IllegalArgumentException.class, () -> repository.reorderChapters(1L, null));
        assertThrows(IllegalArgumentException.class, () -> repository.reorderChapters(1L, List.of()));

        assertEquals(before, orderOf(1L));
    }

    @Test
    void prevNextChapter_followsLatestOrder() {
        Chapter first = repository.findChapterById(1L);
        Chapter second = repository.findChapterById(2L);
        Chapter third = repository.findChapterById(3L);

        // 初始顺序：1 -> 2 -> 3
        assertNull(repository.findPrevChapter(first));
        assertEquals(2L, repository.findNextChapter(first).getId());
        assertEquals(1L, repository.findPrevChapter(second).getId());
        assertEquals(3L, repository.findNextChapter(second).getId());
        assertEquals(2L, repository.findPrevChapter(third).getId());
        assertNull(repository.findNextChapter(third));

        // 重排为 3 -> 1 -> 2 后，导航必须跟随新顺序
        repository.reorderChapters(1L, List.of(3L, 1L, 2L));

        assertNull(repository.findPrevChapter(third));
        assertEquals(1L, repository.findNextChapter(third).getId());
        assertEquals(3L, repository.findPrevChapter(first).getId());
        assertEquals(2L, repository.findNextChapter(first).getId());
        assertEquals(1L, repository.findPrevChapter(second).getId());
        assertNull(repository.findNextChapter(second));
    }
}
