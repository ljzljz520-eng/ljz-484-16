<template>
  <div class="page-container" v-loading="loading">
    
    <!-- Dynamic Backdrop -->
    <div class="backdrop" v-if="novel" :style="{ backgroundImage: 'url(' + novel.coverUrl + ')' }"></div>
    <div class="backdrop-overlay"></div>

    <div class="container content-wrapper">
      <div v-if="novel" class="novel-header">
        <el-button @click="goBack" circle plain :icon="ArrowLeft" class="back-btn"></el-button>
        
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
          <el-button
              v-if="!reorderMode && chapters.length > 1"
              plain
              round
              :icon="Sort"
              @click="enterReorderMode"
          >
              调整顺序
          </el-button>
        </div>

        <el-alert
            v-if="reorderMode"
            type="warning"
            :closable="false"
            show-icon
            class="reorder-tip"
            title="排序调整将影响读者的阅读顺序：目录展示与阅读页「上一章 / 下一章」都会按新顺序生效。"
        />

        <!-- 普通模式：目录网格 -->
        <div v-if="!reorderMode" class="chapter-grid">
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

        <!-- 排序模式：上移/下移调整（番外、序章、错位章节归位） -->
        <div v-else class="reorder-list">
          <div
              v-for="(chapter, index) in chapters"
              :key="chapter.id"
              class="reorder-item"
          >
              <span class="chapter-no">{{ formatNumber(index + 1) }}</span>
              <span class="chapter-title">{{ chapter.title }}</span>
              <div class="reorder-actions">
                  <el-button
                      circle
                      plain
                      :icon="Top"
                      title="上移"
                      :disabled="index === 0 || saving"
                      @click="moveChapter(index, -1)"
                  ></el-button>
                  <el-button
                      circle
                      plain
                      :icon="Bottom"
                      title="下移"
                      :disabled="index === chapters.length - 1 || saving"
                      @click="moveChapter(index, 1)"
                  ></el-button>
              </div>
          </div>
          <div class="reorder-footer">
              <el-button round :disabled="saving" @click="cancelReorder">取消</el-button>
              <el-button
                  type="primary"
                  round
                  :loading="saving"
                  :disabled="!orderChanged"
                  @click="confirmSaveOrder"
              >
                  保存顺序
              </el-button>
          </div>
        </div>

         <el-empty v-if="chapters.length === 0" description="暂无章节" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Sort, Top, Bottom } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const novel = ref(null)
const chapters = ref([])
const loading = ref(true)
const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

// —— 章节排序模式状态 ——
const reorderMode = ref(false)
const saving = ref(false)
let originalOrder = [] // 进入排序模式时的章节 id 快照，用于取消/失败时整体恢复

const fetchDetail = async () => {
  try {
    const res = await axios.get(`${API_URL}/novels/${route.params.id}`)
    novel.value = res.data.novel
    chapters.value = res.data.chapters
  } catch (err) {
    console.error(err)
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

// 进入排序模式：先快照当前顺序，之后所有移动都只是本地暂存，保存成功才生效
const enterReorderMode = () => {
    originalOrder = chapters.value.map(c => c.id)
    reorderMode.value = true
}

// 上移/下移章节（direction: -1 上移, +1 下移）
const moveChapter = (index, direction) => {
    const target = index + direction
    if (target < 0 || target >= chapters.value.length) return
    const list = [...chapters.value]
    const [item] = list.splice(index, 1)
    list.splice(target, 0, item)
    chapters.value = list
}

// 当前顺序是否与快照不同（未改动时禁用保存按钮）
const orderChanged = computed(() =>
    chapters.value.length !== originalOrder.length ||
    chapters.value.some((c, i) => c.id !== originalOrder[i])
)

// 整体恢复为快照顺序 —— 要么全恢复，要么重新拉取，绝不留半更新状态
const restoreOriginalOrder = () => {
    const byId = new Map(chapters.value.map(c => [c.id, c]))
    const restored = originalOrder.map(id => byId.get(id)).filter(Boolean)
    if (restored.length !== originalOrder.length) {
        // 防御：本地数据与快照不一致时，直接以服务端数据为准
        fetchDetail()
        return
    }
    chapters.value = restored
}

const cancelReorder = () => {
    restoreOriginalOrder()
    reorderMode.value = false
}

const confirmSaveOrder = async () => {
    // 移动生效前明确提示：会影响读者阅读顺序
    try {
        await ElMessageBox.confirm(
            '调整章节顺序会影响读者的阅读顺序：目录将按新顺序展示，阅读页「上一章 / 下一章」也会按新顺序跳转。确定保存吗？',
            '保存章节顺序',
            {
                confirmButtonText: '保存顺序',
                cancelButtonText: '再想想',
                type: 'warning'
            }
        )
    } catch {
        return // 作者取消，不做任何修改
    }

    saving.value = true
    try {
        const res = await axios.put(`${API_URL}/novels/${route.params.id}/chapters/order`, {
            chapterIds: chapters.value.map(c => c.id)
        })
        // 以服务端返回为准（含重新编号的 orderNo），目录与阅读导航随之更新
        chapters.value = res.data.chapters
        reorderMode.value = false
        ElMessage.success('章节顺序已保存')
    } catch (err) {
        console.error(err)
        // 保存失败：整体回滚到原顺序，章节列表不允许半更新
        restoreOriginalOrder()
        ElMessage.error('保存失败，已恢复原有章节顺序')
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

.section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 25px;
}

.section-title {
    font-size: 1.5rem;
    padding-left: 10px;
    border-left: 4px solid var(--primary-color);
    color: var(--slate-800);
    margin-bottom: 0;
}

.reorder-tip {
    margin-bottom: 20px;
    border-radius: 8px;
}

.reorder-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.reorder-item {
    display: flex;
    align-items: center;
    padding: 14px 20px;
    background: var(--slate-50);
    border: 1px solid var(--border-color);
    border-radius: 8px;
    transition: border-color 0.2s, box-shadow 0.2s;
}

.reorder-item:hover {
    border-color: var(--primary-color);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.reorder-actions {
    display: flex;
    gap: 8px;
}

.reorder-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 20px;
    padding-top: 20px;
    border-top: 1px dashed var(--border-color);
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
