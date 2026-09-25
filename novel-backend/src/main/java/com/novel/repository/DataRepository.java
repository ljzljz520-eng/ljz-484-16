package com.novel.repository;

import com.novel.exception.BadRequestException;
import com.novel.exception.ResourceNotFoundException;
import com.novel.model.Chapter;
import com.novel.model.Novel;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class DataRepository {
        private final Map<Long, Novel> novels = new ConcurrentHashMap<>();
        private final Map<Long, Chapter> chapters = new ConcurrentHashMap<>();
        private final AtomicLong novelIdGenerator = new AtomicLong(1);
        private final AtomicLong chapterIdGenerator = new AtomicLong(1);

        /**
         * Per-novel locks so two concurrent reorders of the same novel cannot
         * interleave their writes. Different novels can still be reordered in parallel.
         */
        private final Map<Long, Object> reorderLocks = new ConcurrentHashMap<>();

        @PostConstruct
        public void init() {
                // Seeding Data
                Novel novel1 = new Novel(novelIdGenerator.getAndIncrement(),
                                "星际穿越之编程大师",
                                "讲述一位程序员意外穿越到未来，用代码拯救宇宙的故事。",
                                "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?q=80&w=800&auto=format&fit=crop",
                                LocalDateTime.now());
                novels.put(novel1.getId(), novel1);

                chapters.put(chapterIdGenerator.get(), new Chapter(chapterIdGenerator.getAndIncrement(), novel1.getId(),
                                "序章：绿色的黎明", 0, "在一切开始之前，屏幕上只有一行不断闪烁的光标。", LocalDateTime.now()));
                chapters.put(chapterIdGenerator.get(), new Chapter(chapterIdGenerator.getAndIncrement(), novel1.getId(),
                                "第一章：Hello World", 1, "他醒来时，发现眼前只有绿色的代码流...", LocalDateTime.now()));
                chapters.put(chapterIdGenerator.get(), new Chapter(chapterIdGenerator.getAndIncrement(), novel1.getId(),
                                "第二章：变量声明", 2, "“你是谁？”面前的机器人冷冷地问道。“Define me.”他回答。", LocalDateTime.now()));
                // A side story (番外) initially misplaced in the middle of the main storyline;
                // authors can move it to the end via chapter reordering.
                chapters.put(chapterIdGenerator.get(), new Chapter(chapterIdGenerator.getAndIncrement(), novel1.getId(),
                                "番外：穿越前的世界", 3, "那还是一个普通的周五晚上，他正准备下班……", LocalDateTime.now()));
                chapters.put(chapterIdGenerator.get(), new Chapter(chapterIdGenerator.getAndIncrement(), novel1.getId(),
                                "第三章：循环陷阱", 4, "时间仿佛陷入了死循环，他必须找到 break 的条件。", LocalDateTime.now()));

                Novel novel2 = new Novel(novelIdGenerator.getAndIncrement(),
                                "灵气复苏时代的架构师",
                                "灵气复苏，万物进化。他发现修仙法门竟然符合微服务架构原理。",
                                "https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=600&auto=format&fit=crop",
                                LocalDateTime.now());
                novels.put(novel2.getId(), novel2);

                chapters.put(chapterIdGenerator.get(), new Chapter(chapterIdGenerator.getAndIncrement(), novel2.getId(),
                                "第一章：单体应用破碎", 1, "天地巨变，世界原本的秩序（Monolith）崩塌了。", LocalDateTime.now()));
                chapters.put(chapterIdGenerator.get(), new Chapter(chapterIdGenerator.getAndIncrement(), novel2.getId(),
                                "第二章：服务发现", 2, "他感应到了周围的灵气节点，就像注册中心里的服务一样清晰。", LocalDateTime.now()));

                Novel novel3 = new Novel(novelIdGenerator.getAndIncrement(),
                                "只有我知道剧情的测试员",
                                "作为世界系统的唯一QA，他能看到由于Bug导致的隐藏剧情。",
                                "https://images.unsplash.com/photo-1555949963-ff9fe0c870eb?q=80&w=800&auto=format&fit=crop",
                                LocalDateTime.now());
                novels.put(novel3.getId(), novel3);
        }

        public List<Novel> findAllNovels(String keyword, int page, int size) {
                return novels.values().stream()
                                .filter(n -> keyword == null || keyword.isEmpty() || n.getTitle().contains(keyword)
                                                || n.getDescription().contains(keyword))
                                .sorted(Comparator.comparing(Novel::getId).reversed())
                                .skip((long) (page - 1) * size)
                                .limit(size)
                                .collect(Collectors.toList());
        }

        public long countNovels(String keyword) {
                return novels.values().stream()
                                .filter(n -> keyword == null || keyword.isEmpty() || n.getTitle().contains(keyword)
                                                || n.getDescription().contains(keyword))
                                .count();
        }

        public Novel findNovelById(Long id) {
                return novels.get(id);
        }

        public List<Chapter> findChaptersByNovelId(Long novelId) {
                return chapters.values().stream()
                                .filter(c -> c.getNovelId().equals(novelId))
                                .sorted(Comparator.comparing(Chapter::getOrderNo))
                                .collect(Collectors.toList());
        }

        public Chapter findChapterById(Long id) {
                return chapters.get(id);
        }

        /**
         * Atomically reorder all chapters of a novel.
         *
         * The {@code orderedIds} must contain exactly the set of the novel's chapter IDs,
         * each appearing once. Order numbers are rewritten as contiguous values starting
         * from 1, so the catalogue and the reading prev/next navigation (which both rely
         * on orderNo) change consistently.
         *
         * The method either fully succeeds or fully fails: every validation runs before
         * any mutation, and if anything goes wrong while applying the new order, the
         * original order numbers are restored. A half-updated chapter list can never be
         * observed.
         *
         * @return the chapters in the new order after a successful update
         */
        public List<Chapter> reorderChapters(Long novelId, List<Long> orderedIds) {
                if (novels.get(novelId) == null) {
                        throw new ResourceNotFoundException("小说不存在");
                }
                if (orderedIds == null || orderedIds.isEmpty()) {
                        throw new BadRequestException("章节顺序不能为空");
                }

                List<Chapter> novelChapters = findChaptersByNovelId(novelId);

                // ---- Phase 1: validate everything BEFORE touching any data ----
                if (orderedIds.size() != novelChapters.size()) {
                        throw new BadRequestException("章节数量不匹配，请提供该书全部章节的完整顺序");
                }

                Map<Long, Chapter> chaptersById = new HashMap<>();
                for (Chapter chapter : novelChapters) {
                        chaptersById.put(chapter.getId(), chapter);
                }

                Set<Long> seen = new HashSet<>();
                for (Long chapterId : orderedIds) {
                        if (chapterId == null) {
                                throw new BadRequestException("章节 ID 不能为空");
                        }
                        if (!seen.add(chapterId)) {
                                throw new BadRequestException("章节 ID 重复：" + chapterId);
                        }
                        if (!chaptersById.containsKey(chapterId)) {
                                throw new BadRequestException("章节不属于当前小说或不存在：" + chapterId);
                        }
                }
                if (seen.size() != novelChapters.size()) {
                        throw new BadRequestException("章节集合不完整，无法调整顺序");
                }

                Object lock = reorderLocks.computeIfAbsent(novelId, key -> new Object());
                synchronized (lock) {
                        // ---- Phase 2: snapshot the current order for rollback ----
                        Map<Long, Integer> originalOrder = new HashMap<>();
                        for (Chapter chapter : novelChapters) {
                                originalOrder.put(chapter.getId(), chapter.getOrderNo());
                        }

                        try {
                                // ---- Phase 3: apply the new contiguous order ----
                                for (int i = 0; i < orderedIds.size(); i++) {
                                        Chapter chapter = chaptersById.get(orderedIds.get(i));
                                        if (chapter == null) {
                                                throw new IllegalStateException("章节在保存过程中丢失");
                                        }
                                        chapter.setOrderNo(i + 1);
                                }
                        } catch (RuntimeException ex) {
                                // Rollback so a failed save never leaves a partially updated list.
                                for (Map.Entry<Long, Integer> entry : originalOrder.entrySet()) {
                                        Chapter chapter = chapters.get(entry.getKey());
                                        if (chapter != null) {
                                                chapter.setOrderNo(entry.getValue());
                                        }
                                }
                                throw ex;
                        }
                }

                return findChaptersByNovelId(novelId);
        }

        /**
         * Find the previous and next chapters of {@code chapterId} according to orderNo.
         * Null is returned for a neighbour when the given chapter sits at an edge of
         * the book. Reading navigation therefore always follows the latest saved order.
         */
        public Chapter[] findNeighborChapters(Long chapterId) {
                Chapter current = chapters.get(chapterId);
                if (current == null) {
                        return null;
                }

                Chapter previous = null;
                Chapter next = null;
                for (Chapter candidate : chapters.values()) {
                        if (!candidate.getNovelId().equals(current.getNovelId())
                                        || candidate.getId().equals(chapterId)) {
                                continue;
                        }
                        if (candidate.getOrderNo() != null && current.getOrderNo() != null) {
                                if (candidate.getOrderNo() < current.getOrderNo()
                                                && (previous == null
                                                                || candidate.getOrderNo() > previous.getOrderNo())) {
                                        previous = candidate;
                                }
                                if (candidate.getOrderNo() > current.getOrderNo()
                                                && (next == null
                                                                || candidate.getOrderNo() < next.getOrderNo())) {
                                        next = candidate;
                                }
                        }
                }
                return new Chapter[] { previous, next };
        }
}
