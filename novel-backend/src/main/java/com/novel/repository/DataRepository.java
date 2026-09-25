package com.novel.repository;

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
                                "第一章：Hello World", 1, "他醒来时，发现眼前只有绿色的代码流...", LocalDateTime.now()));
                chapters.put(chapterIdGenerator.get(), new Chapter(chapterIdGenerator.getAndIncrement(), novel1.getId(),
                                "第二章：变量声明", 2, "“你是谁？”面前的机器人冷冷地问道。“Define me.”他回答。", LocalDateTime.now()));
                chapters.put(chapterIdGenerator.get(), new Chapter(chapterIdGenerator.getAndIncrement(), novel1.getId(),
                                "第三章：循环陷阱", 3, "时间仿佛陷入了死循环，他必须找到 break 的条件。", LocalDateTime.now()));

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
         * 按 orderNo 查找指定章节的上一章（阅读页导航用，跟随最新排序）。
         */
        public Chapter findPrevChapter(Chapter chapter) {
                return chapters.values().stream()
                                .filter(c -> c.getNovelId().equals(chapter.getNovelId()))
                                .filter(c -> c.getOrderNo() < chapter.getOrderNo())
                                .max(Comparator.comparing(Chapter::getOrderNo))
                                .orElse(null);
        }

        /**
         * 按 orderNo 查找指定章节的下一章（阅读页导航用，跟随最新排序）。
         */
        public Chapter findNextChapter(Chapter chapter) {
                return chapters.values().stream()
                                .filter(c -> c.getNovelId().equals(chapter.getNovelId()))
                                .filter(c -> c.getOrderNo() > chapter.getOrderNo())
                                .min(Comparator.comparing(Chapter::getOrderNo))
                                .orElse(null);
        }

        /**
         * 原子化重排某本小说的章节顺序。
         *
         * <p>
         * 先在内存中完成全部校验，校验通过后才统一写入新的 orderNo；
         * 任何校验失败都会在修改数据之前抛出异常，保证原顺序不被破坏，
         * 不会出现"改了一半"的中间状态。
         * </p>
         *
         * @param novelId    小说 ID
         * @param chapterIds 期望的完整章节 ID 列表（按新顺序排列，必须覆盖该小说全部章节）
         * @return 重排后的章节列表（按 orderNo 升序）
         * @throws IllegalArgumentException 当提交列表与现有章节不一致时
         */
        public synchronized List<Chapter> reorderChapters(Long novelId, List<Long> chapterIds) {
                List<Chapter> existing = findChaptersByNovelId(novelId);

                // —— 校验阶段：失败时直接抛出，此时任何数据都尚未被修改 ——
                if (chapterIds == null || chapterIds.size() != existing.size()) {
                        throw new IllegalArgumentException("章节列表不完整，请刷新目录后重试");
                }
                Set<Long> incomingIds = new HashSet<>(chapterIds);
                if (incomingIds.size() != chapterIds.size()) {
                        throw new IllegalArgumentException("章节列表中存在重复章节");
                }
                Map<Long, Chapter> existingById = existing.stream()
                                .collect(Collectors.toMap(Chapter::getId, c -> c));
                for (Long id : chapterIds) {
                        if (id == null || !existingById.containsKey(id)) {
                                throw new IllegalArgumentException("章节列表与当前小说不匹配，请刷新目录后重试");
                        }
                }

                // —— 应用阶段：校验全部通过后才写入，保证要么全部成功、要么完全不变 ——
                for (int i = 0; i < chapterIds.size(); i++) {
                        existingById.get(chapterIds.get(i)).setOrderNo(i + 1);
                }
                return findChaptersByNovelId(novelId);
        }
}
