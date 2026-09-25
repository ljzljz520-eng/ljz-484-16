# 设计文档 (Design)

## 1. 设计理念
本项目遵循“现代、简洁、高级”的设计原则。采用浅色系主题，配合毛玻璃效果 (Glassmorphism) 和优雅的微交互。

## 2. 视觉规范
- **色彩系统**:
  - 主背景色: `#f8fafc` (Slate 50)
  - 强调色: `#6366f1` (Indigo 600)
  - 文字主色: `#1e293b` (Slate 800)
  - 文字辅助色: `#64748b` (Slate 500)
- **字体**:
  - 系统 UI: Inter, sans-serif
  - 阅读正文: Merriweather, serif (提升长文阅读体验)
- **组件样式**:
  - 圆角: 16px (大圆角设计，增强现代感)
  - 阴影: 柔和的扩散阴影，增加层次感。

## 3. 关键页面设计
- **首页**: 沉浸式英雄区 (Hero Section)，双色渐变标题，列表卡片采用悬浮提升效果。
- **详情页**: 动态背景模糊设计，根据小说封面自动生成氛围背景。
- **阅读页**: 经典的“护眼纸质”配色 (`#fcf6e5`)，无干扰布局。

## 4. 接口设计 (RESTful API)
- `GET /api/novels`: 获取小说列表（支持分页与搜索）。
- `GET /api/novels/{id}`: 获取小说详细信息及章节目录。
- `GET /api/novels/{id}/chapters`: 获取小说章节目录（按 orderNo 升序）。
- `PUT /api/novels/{id}/chapters/order`: 调整章节顺序。请求体 `{"chapterIds": [3,1,2]}` 必须覆盖该小说全部章节；服务端先校验后写入，**原子生效**——校验失败（400）时原顺序完全不变，杜绝半更新。
- `GET /api/chapters/{id}`: 获取章节正文，并返回 `prevChapterId` / `nextChapterId`（基于最新 orderNo 计算），阅读页「上一章 / 下一章」导航随排序调整自动跟随。

## 5. 数据模型
- **Novel (小说)**: ID, Title, Description, CoverUrl, CreatedAt.
- **Chapter (章节)**: ID, NovelId, Title, OrderNo, Content, CreatedAt.

## 6. 章节排序交互设计
- 详情页目录提供「调整顺序」模式：作者通过上移/下移把番外、序章或错位章节归位，所有调整仅在本地暂存。
- 排序模式下常驻警示条，保存前弹出确认框，明确提示"会影响读者的阅读顺序"。
- 保存成功以服务端返回为准；保存失败时前端整体回滚到进入排序时的快照顺序，章节列表不会出现半更新状态。
