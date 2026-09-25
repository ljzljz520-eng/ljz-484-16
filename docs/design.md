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
- `GET /api/chapters/{id}`: 获取具体章节正文内容。
- `PUT /api/novels/{id}/chapters/reorder`: 作者调整章节顺序（请求体为按新阅读顺序排列的完整章节 ID 列表）。整体校验通过后一次性重写 `orderNo`，任一环节失败则回滚为原顺序，不会出现半更新列表。
- `GET /api/chapters/{id}/navigation`: 获取当前章节的上一章 / 下一章（依据最新保存的 `orderNo`），阅读导航随排序结果同步变化。

## 5. 数据模型
- **Novel (小说)**: ID, Title, Description, CoverUrl, CreatedAt.
- **Chapter (章节)**: ID, NovelId, Title, OrderNo, Content, CreatedAt.
