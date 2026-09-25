<template>
  <div class="read-page" v-loading="loading">

     <div class="reader-container">
         <div class="content-paper" v-if="chapter">
             <h2 class="chapter-heading">{{ chapter.title }}</h2>
             <div class="text-content font-serif">
                 <p v-for="(para, idx) in paragraphs" :key="idx">{{ para }}</p>
             </div>
         </div>

         <div class="footer-controls" v-if="chapter">
             <el-button
                 class="nav-chapter-btn glass-panel"
                 :disabled="!navigation.previous"
                 @click="goToChapter(navigation.previous)"
             >
                 <el-icon class="nav-icon"><ArrowLeft /></el-icon>
                 <span class="nav-text">
                     <span class="nav-label">上一章</span>
                     <span class="nav-title">{{ navigation.previous ? navigation.previous.title : '已是第一章' }}</span>
                 </span>
             </el-button>

             <el-button class="nav-chapter-btn glass-panel toc-btn" @click="goToToc">
                 <el-icon><Menu /></el-icon>
                 <span>返回目录</span>
             </el-button>

             <el-button
                 class="nav-chapter-btn glass-panel nav-next"
                 :disabled="!navigation.next"
                 @click="goToChapter(navigation.next)"
             >
                 <span class="nav-text nav-text-right">
                     <span class="nav-label">下一章</span>
                     <span class="nav-title">{{ navigation.next ? navigation.next.title : '已是最后一章' }}</span>
                 </span>
                 <el-icon class="nav-icon"><ArrowRight /></el-icon>
             </el-button>
         </div>
     </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Menu } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const chapter = ref(null)
const loading = ref(true)
const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

// Previous / next are computed by the backend from the saved chapter order,
// so reader navigation always matches the catalogue after reordering.
const navigation = reactive({
    novelId: null,
    previous: null,
    next: null
})

const fetchChapter = async () => {
    loading.value = true
    chapter.value = null
    navigation.novelId = null
    navigation.previous = null
    navigation.next = null
    const chapterId = route.params.id

    try {
        const [chapterRes, navRes] = await Promise.all([
            axios.get(`${API_URL}/chapters/${chapterId}`),
            axios.get(`${API_URL}/chapters/${chapterId}/navigation`)
        ])
        chapter.value = chapterRes.data
        navigation.previous = navRes.data.previous
        navigation.next = navRes.data.next
        navigation.novelId = navRes.data.novelId
    } catch(err) {
        console.error(err)
        ElMessage.error('章节加载失败')
    } finally {
        loading.value = false
        window.scrollTo({ top: 0 })
    }
}

const paragraphs = computed(() => {
    if(!chapter.value || !chapter.value.content) return []
    return chapter.value.content.split('\n')
})

const goToChapter = (target) => {
    if (!target || !target.id) return
    router.push('/chapter/' + target.id)
}

const goToToc = () => {
    if (navigation.novelId) {
        router.push('/novel/' + navigation.novelId)
    } else if (window.history.length > 1) {
        router.back()
    } else {
        router.push('/')
    }
}

// Re-fetch when moving between chapters via prev/next (same route component).
watch(() => route.params.id, (newId, oldId) => {
    if (newId && newId !== oldId) {
        fetchChapter()
    }
})

onMounted(fetchChapter)
</script>

<style scoped>
.read-page {
    min-height: 100vh;
    background-color: #fcf6e5; /* Warm paper background */
    color: #374151;
    position: relative;
    padding-top: 60px;
}

.reader-container {
    max-width: 720px; /* Optimal reading width */
    margin: 0 auto;
    padding: 0 20px 80px;
}

.content-paper {
    padding: 0px 0 40px;
}

.chapter-heading {
    text-align: center;
    font-size: 2rem;
    margin-bottom: 3rem;
    color: #111827;
    font-family: 'Merriweather', serif;
}

.text-content {
    font-size: 1.25rem;
    line-height: 2;
    color: #374151;
}

.text-content p {
    margin-bottom: 2em;
    text-align: justify;
}

.footer-controls {
    margin-top: 60px;
    display: flex;
    justify-content: space-between;
    align-items: stretch;
    gap: 15px;
}

.nav-chapter-btn {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 16px 22px;
    background: transparent;
    color: #4b5563;
    border: 1px solid rgba(0, 0, 0, 0.1);
    border-radius: 12px;
    height: auto;
    max-width: 38%;
    transition: all 0.3s;
}

.nav-chapter-btn:not(:disabled):hover {
    background: rgba(99, 102, 241, 0.08);
    border-color: var(--primary-color);
    color: var(--primary-color);
}

.nav-chapter-btn.is-disabled,
.nav-chapter-btn:disabled {
    opacity: 0.45;
    cursor: not-allowed;
}

.nav-icon {
    font-size: 1.1rem;
    flex-shrink: 0;
}

.nav-text {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    overflow: hidden;
}

.nav-text-right {
    align-items: flex-end;
}

.nav-label {
    font-size: 0.75rem;
    color: #9ca3af;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.nav-title {
    font-size: 0.9rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 200px;
}

.toc-btn {
    flex-direction: column;
    gap: 6px;
    font-size: 0.85rem;
    padding: 16px 28px;
    max-width: none;
}

@media (max-width: 600px) {
    .footer-controls {
        flex-wrap: wrap;
    }

    .nav-chapter-btn {
        max-width: none;
        flex: 1 1 45%;
    }

    .toc-btn {
        order: 3;
        flex: 1 1 100%;
    }

    .nav-title {
        max-width: 120px;
    }
}
</style>
