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
                 :disabled="!prevChapterId"
                 @click="goToChapter(prevChapterId)"
             >上一章</el-button>
             <el-button class="nav-chapter-btn glass-panel" @click="goToToc">返回目录</el-button>
             <el-button
                 class="nav-chapter-btn glass-panel"
                 :disabled="!nextChapterId"
                 @click="goToChapter(nextChapterId)"
             >下一章</el-button>
         </div>
     </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { ArrowLeft, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const chapter = ref(null)
const prevChapterId = ref(null)
const nextChapterId = ref(null)
const loading = ref(true)
const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

const fetchChapter = async (id) => {
    loading.value = true
    try {
        const res = await axios.get(`${API_URL}/chapters/${id}`)
        // 后端基于最新章节顺序返回上一章/下一章，重排后导航自动跟随
        chapter.value = res.data.chapter
        prevChapterId.value = res.data.prevChapterId
        nextChapterId.value = res.data.nextChapterId
    } catch(err) {
        console.error(err)
    } finally {
        loading.value = false
    }
}

const paragraphs = computed(() => {
    if(!chapter.value || !chapter.value.content) return []
    return chapter.value.content.split('\n')
})

const goToChapter = (id) => {
    if (id) {
        router.push('/chapter/' + id)
    }
}

const goToToc = () => {
    if (chapter.value && chapter.value.novelId) {
        router.push('/novel/' + chapter.value.novelId)
    } else {
        router.push('/')
    }
}

// 上一章/下一章跳转时组件被复用，监听路由参数重新加载并回到顶部
watch(() => route.params.id, (newId, oldId) => {
    if (newId && newId !== oldId && route.name === 'Read') {
        fetchChapter(newId)
        window.scrollTo({ top: 0 })
    }
})

onMounted(() => fetchChapter(route.params.id))
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
    justify-content: center;
    gap: 16px;
    flex-wrap: wrap;
}

.footer-controls .el-button + .el-button {
    margin-left: 0; /* 覆盖 Element Plus 默认按钮间距，统一由 gap 控制 */
}

.nav-chapter-btn {
    padding: 20px 32px;
    background: transparent;
    color: #4b5563;
    border: 1px solid rgba(0, 0, 0, 0.1);
    transition: all 0.3s;
}

.nav-chapter-btn:hover:not(:disabled) {
    background: rgba(0, 0, 0, 0.05);
    border-color: var(--primary-color);
    color: var(--primary-color);
}

.nav-chapter-btn:disabled {
    opacity: 0.45;
    cursor: not-allowed;
}
</style>
