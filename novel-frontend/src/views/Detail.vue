<template>
  <div class="page-container" v-loading="loading">
    
    <!-- Dynamic Backdrop -->
    <div class="backdrop" v-if="novel" :style="{ backgroundImage: 'url(' + novel.coverUrl + ')' }"></div>
    <div class="backdrop-overlay"></div>

    <div class="container content-wrapper">
      <div v-if="novel" class="novel-header">
        <el-button @click="goBack" circle plain icon="ArrowLeft" class="back-btn"></el-button>
        
        <div class="header-inner glass-panel">
          <div class="cover-wrapper">
             <img :src="novel.coverUrl" class="cover-img" />
          </div>
          <div class="info-content">
            <h1 class="novel-title text-gradient">{{ novel.title }}</h1>
            <div class="meta-tags">
                 <span class="meta-item">{{ formatDate(novel.createdAt) }}</span>
                 <span class="meta-item">{{ chapters.length }} 章</span>
            </div>
            <p class="description">{{ novel.description }}</p>
            <el-button type="primary" size="large" round class="start-read-btn" @click="startReading">
                开始阅读
            </el-button>
          </div>
        </div>
      </div>

      <div class="chapters-section glass-panel">
        <div class="section-header">
          <h2 class="section-title">章节目录</h2>
          <div class="section-actions">
            <template v-if="!editMode">
              <el-button
                  type="primary"
                  plain
                  round
                  :icon="Sort"
                  :disabled="chapters.length === 0"
                  @click="enterEditMode"
              >
                排序管理
              </el-button>
            </template>
            <template v-else>
              <el-button round :icon="RefreshLeft" :disabled="saving" @click="cancelEdit">
                撤销更改
              </el-button>
              <el-button type="primary" round :loading="saving" @click="saveOrder">
                保存顺序
              </el-button>
            </template>
          </div>
        </div>

        <!-- Normal reading catalogue -->
        <div v-if="!editMode" class="chapter-grid">
          <router-link
              v-for="chapter in chapters"
              :key="chapter.id"
              :to="'/chapter/' + chapter.id"
              class="chapter-card"
          >
              <span class="chapter-no">{{ formatNumber(chapter.orderNo) }}</span>
              <span class="chapter-title">{{ chapter.title }}</span>
              <span class="status-dot"></span>
          </router-link>
        </div>

        <!-- Author sorting mode -->
        <div v-else class="reorder-list">
          <el-alert
              class="reorder-tip"
              type="warning"
              :closable="false"
              show-icon
              title="调整后将影响所有读者的目录顺序和“上一章 / 下一章”阅读路径"
              description="可拖动章节卡片，或使用右侧 ↑ / ↓ 按钮移动；序章、番外或错位章节可放到合适的位置。"
          />
          <div
              v-for="(chapter, index) in draftChapters"
              :key="chapter.id"
              class="reorder-item"
              :class="{ 'is-dragging': draggingIndex === index, 'is-drag-over': dragOverIndex === index }"
              draggable="true"
              @dragstart="onDragStart(index, $event)"
              @dragover.prevent="onDragOver(index, $event)"
              @dragleave="onDragLeave(index)"
              @drop.prevent="onDrop(index)"
              @dragend="onDragEnd"
          >
              <span class="drag-handle"><el-icon><Rank /></el-icon></span>
              <span class="chapter-no">{{ formatNumber(index + 1) }}</span>
              <span class="chapter-title">{{ chapter.title }}</span>
              <span class="reorder-actions">
                  <el-button
                      size="small"
                      circle
                      plain
                      :icon="Top"
                      :disabled="index === 0 || saving"
                      title="移到最前"
                      @click="moveChapter(index, 0)"
                  />
                  <el-button
                      size="small"
                      circle
                      plain
                      :icon="ArrowUp"
                      :disabled="index === 0 || saving"
                      title="上移"
                      @click="moveChapter(index, index - 1)"
                  />
                  <el-button
                      size="small"
                      circle
                      plain
                      :icon="ArrowDown"
                      :disabled="index === draftChapters.length - 1 || saving"
                      title="下移"
                      @click="moveChapter(index, index + 1)"
                  />
                  <el-button
                      size="small"
                      circle
                      plain
                      :icon="Bottom"
                      :disabled="index === draftChapters.length - 1 || saving"
                      title="移到最后"
                      @click="moveChapter(index, draftChapters.length - 1)"
                  />
              </span>
          </div>
          <p class="reorder-hint">
            共 {{ draftChapters.length }} 章 · 未保存的调整不会影响读者
          </p>
        </div>

        <el-empty v-if="!editMode && chapters.length === 0" description="暂无章节" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Sort,
  Rank,
  RefreshLeft,
  ArrowUp,
  ArrowDown,
  Top,
  Bottom
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const novel = ref(null)
const chapters = ref([])
const loading = ref(true)
const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

// ---- Author chapter sorting state ----
const editMode = ref(false)
const saving = ref(false)
// Draft list manipulated during editing; chapters.value stays untouched until save succeeds.
const draftChapters = ref([])
const draggingIndex = ref(null)
const dragOverIndex = ref(null)

const fetchDetail = async () => {
  try {
    const res = await axios.get(`${API_URL}/novels/${route.params.id}`)
    novel.value = res.data.novel
    chapters.value = res.data.chapters
  } catch (err) {
    console.error(err)
    ElMessage.error('加载书籍信息失败')
  } finally {
    loading.value = false
  }
}

const goBack = () => {
    router.back()
}

const startReading = () => {
    if (chapters.value.length > 0) {
        router.push('/chapter/' + chapters.value[0].id)
    }
}

// ---- Sorting mode ----
const enterEditMode = async () => {
  if (chapters.value.length < 2) {
    ElMessage.info('至少需要两个章节才能调整顺序')
    return
  }
  try {
    // Warn the author BEFORE any move that reader order will be affected.
    await ElMessageBox.confirm(
      '调整章节顺序后，将影响所有读者的目录展示顺序，以及阅读时的“上一章 / 下一章”路径。确定要开始调整吗？',
      '章节排序将影响读者阅读顺序',
      {
        confirmButtonText: '我知道了，开始调整',
        cancelButtonText: '取消',
        type: 'warning',
        roundButton: true
      }
    )
  } catch (action) {
    return // user cancelled
  }
  draftChapters.value = chapters.value.map(chapter => ({ ...chapter }))
  editMode.value = true
}

const cancelEdit = () => {
  // Discard local draft entirely; saved order is never touched here.
  draftChapters.value = []
  editMode.value = false
}

// Move the chapter at `from` to position `to` using an up/down (or top/bottom) button.
const moveChapter = (from, to) => {
  if (saving.value || from === to) return
  if (from < 0 || from >= draftChapters.value.length) return
  if (to < 0 || to >= draftChapters.value.length) return

  const list = draftChapters.value.slice()
  const [moved] = list.splice(from, 1)
  list.splice(to, 0, moved)
  draftChapters.value = list
}

// ---- Native HTML5 drag & drop ----
const onDragStart = (index, event) => {
  if (saving.value) {
    event.preventDefault()
    return
  }
  draggingIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(index))
  }
}

const onDragOver = (index, event) => {
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dragOverIndex.value = index
}

const onDragLeave = (index) => {
  if (dragOverIndex.value === index) {
    dragOverIndex.value = null
  }
}

const onDrop = (targetIndex) => {
  const sourceIndex = draggingIndex.value
  dragOverIndex.value = null
  draggingIndex.value = null
  if (sourceIndex === null || sourceIndex === targetIndex) return
  moveChapter(sourceIndex, targetIndex)
}

const onDragEnd = () => {
  draggingIndex.value = null
  dragOverIndex.value = null
}

const isOrderChanged = () => {
  if (draftChapters.value.length !== chapters.value.length) return true
  return draftChapters.value.some((chapter, index) => chapter.id !== chapters.value[index].id)
}

const saveOrder = async () => {
  if (saving.value) return
  if (!isOrderChanged()) {
    ElMessage.info('顺序未发生变化')
    editMode.value = false
    draftChapters.value = []
    return
  }

  // Keep a snapshot so a failed save restores the pre-edit order in the UI,
  // never leaving a half-updated list.
  const savedChapters = chapters.value.slice()
  saving.value = true
  try {
    const orderedIds = draftChapters.value.map(chapter => chapter.id)
    const res = await axios.put(`${API_URL}/novels/${route.params.id}/chapters/reorder`, {
      chapterIds: orderedIds
    })
    // Only commit after the server confirms; adopt the server's canonical list.
    chapters.value = res.data.chapters
    editMode.value = false
    draftChapters.value = []
    ElMessage.success('章节顺序已保存，目录与阅读顺序已同步更新')
  } catch (err) {
    console.error(err)
    const serverMessage = err?.response?.data?.message
    // Rollback: restore the original saved order and discard the failed draft.
    chapters.value = savedChapters
    draftChapters.value = savedChapters.map(chapter => ({ ...chapter }))
    ElMessage.error(serverMessage || '保存失败，已恢复原章节顺序')
  } finally {
    saving.value = false
  }
}

const formatDate = (val) => {
    if(!val) return ''
    return new Date(val).toLocaleDateString('zh-CN')
}

const formatNumber = (num) => {
    return num.toString().padStart(2, '0')
}

onMounted(fetchDetail)
</script>

<style scoped>
.page-container {
    min-height: 100vh;
    position: relative;
    padding-bottom: 60px;
}

.backdrop {
    position: absolute;
    top: 0; left: 0; right: 0;
    height: 60vh;
    background-size: cover;
    background-position: center;
    z-index: 0;
    filter: blur(20px);
    opacity: 0.3;
}

.backdrop-overlay {
    position: absolute;
    top: 0; left: 0; right: 0;
    height: 70vh;
    background: linear-gradient(to bottom, rgba(255,255,255,0.2), var(--bg-color));
    z-index: 1;
}

.content-wrapper {
    position: relative;
    z-index: 2;
    padding-top: 40px;
}

.back-btn {
    margin-bottom: 20px;
    background: white;
    border: 1px solid rgba(0,0,0,0.1);
    color: var(--text-main);
    box-shadow: 0 2px 10px rgba(0,0,0,0.05);
}

.novel-header {
    margin-bottom: 40px;
}

.header-inner {
    display: flex;
    gap: 40px;
    padding: 40px;
    align-items: flex-start;
    background: rgba(255,255,255,0.8);
    backdrop-filter: blur(20px);
}

.cover-img {
    width: 220px;
    border-radius: 8px;
    box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
}

.info-content {
    flex: 1;
}

.novel-title {
    font-size: 2.5rem;
    margin-bottom: 15px;
}

.meta-tags {
    display: flex;
    gap: 20px;
    color: var(--text-sub);
    font-size: 0.9rem;
    margin-bottom: 25px;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.description {
    line-height: 1.8;
    color: var(--slate-600);
    font-size: 1.1rem;
    margin-bottom: 30px;
    max-width: 800px;
}

.start-read-btn {
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    border: none;
    padding: 24px 40px;
    font-weight: 600;
    font-size: 1.1rem;
    box-shadow: 0 4px 15px rgba(99, 102, 241, 0.3);
}

.start-read-btn:hover {
    filter: brightness(1.1);
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
}

.section-title {
    font-size: 1.5rem;
    margin-bottom: 25px;
    padding-left: 10px;
    border-left: 4px solid var(--primary-color);
    color: var(--slate-800);
}

.section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
    margin-bottom: 25px;
}

.section-header .section-title {
    margin-bottom: 0;
}

.section-actions {
    display: flex;
    gap: 10px;
}

.reorder-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.reorder-tip {
    margin-bottom: 15px;
    border-radius: 8px;
}

.reorder-item {
    display: flex;
    align-items: center;
    gap: 15px;
    padding: 14px 18px;
    background: var(--slate-50);
    border: 1px solid var(--border-color);
    border-radius: 8px;
    cursor: grab;
    transition: all 0.15s ease;
}

.reorder-item:hover {
    background: white;
    border-color: var(--primary-color);
    box-shadow: 0 4px 12px rgba(0,0,0,0.06);
}

.reorder-item:active {
    cursor: grabbing;
}

.reorder-item.is-dragging {
    opacity: 0.5;
}

.reorder-item.is-drag-over {
    border-color: var(--primary-color);
    border-style: dashed;
    transform: scale(1.01);
}

.drag-handle {
    display: flex;
    align-items: center;
    color: var(--slate-400);
    font-size: 1.1rem;
}

.reorder-item .chapter-no {
    margin-right: 0;
}

.reorder-item .chapter-title {
    flex: 1;
    font-weight: 500;
}

.reorder-actions {
    display: flex;
    gap: 6px;
}

.reorder-hint {
    margin-top: 8px;
    color: var(--text-sub);
    font-size: 0.85rem;
    text-align: right;
}

.chapters-section {
    padding: 40px;
    background: white;
}

.chapter-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 15px;
}

.chapter-card {
    display: flex;
    align-items: center;
    padding: 20px;
    background: var(--slate-50);
    border: 1px solid var(--border-color);
    border-radius: 8px;
    transition: all 0.2s;
}

.chapter-card:hover {
    background: white;
    border-color: var(--primary-color);
    transform: translateX(5px);
    box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

.chapter-no {
    font-family: 'Space Mono', monospace;
    color: var(--slate-400);
    margin-right: 15px;
    font-size: 0.9rem;
}

.chapter-title {
    flex: 1;
    font-weight: 500;
}

.status-dot {
    width: 6px;
    height: 6px;
    background-color: var(--primary-color);
    border-radius: 50%;
    opacity: 0;
    transition: opacity 0.2s;
}

.chapter-card:hover .status-dot {
    opacity: 1;
}

@media (max-width: 768px) {
    .header-inner {
        flex-direction: column;
        align-items: center;
        text-align: center;
    }
    
    .meta-tags {
        justify-content: center;
    }
    
    .start-read-btn {
        width: 100%;
    }
}
</style>
