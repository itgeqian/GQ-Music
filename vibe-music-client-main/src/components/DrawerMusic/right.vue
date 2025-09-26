<script setup lang="ts">
import type { SongDetail } from '@/api/interface'
import { ref, inject, type Ref, computed, watch } from 'vue'
import { formatNumber } from '@/utils'
import { likeComment, cancelLikeComment, addSongComment, deleteComment, getSongComments } from '@/api/system'
import { CommentThread } from '@/components/CommentThread'
import EmojiPicker from '@/components/EmojiPicker.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import { ElMessage } from 'element-plus'
import { UserStore } from '@/stores/modules/user'
import LyricPanel from '@/components/LyricPanel.vue'
import { useAudioPlayer } from '@/hooks/useAudioPlayer'
import { useRouter } from 'vue-router'

const songDetail = inject<Ref<SongDetail | null>>('songDetail')
const userStore = UserStore()
const audio = useAudioPlayer()
const router = useRouter()

// 获取当前用户名/用户ID
const currentUsername = computed(() => userStore.userInfo?.username || '')
function getUid(): number | null {
  return (userStore as any)?.userInfo?.id ?? (userStore as any)?.userInfo?.userId ?? null
}

// 评论相关
const commentContent = ref('')
const commentImageUrl = ref('')
const maxLength = 180
const imageUploadRef = ref()

// 对评论进行排序，最新的显示在前面
const comments = computed(() => {
  if (!songDetail.value?.comments) return []
  return [...songDetail.value.comments].sort((a, b) => b.commentId - a.commentId)
})

// 发布评论
const handleComment = async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录'); try { window.dispatchEvent(new CustomEvent('auth:show')) } catch {}
    return
  }

  if (!commentContent.value.trim() && !commentImageUrl.value) {
    ElMessage.warning('请输入评论内容或上传图片')
    return
  }
  
  try {
    const songId = songDetail.value?.songId
    if (!songId) return
    
    const content = commentContent.value.trim()
    const res = await addSongComment({
      songId,
      content,
      imgPath: commentImageUrl.value || null
    })
    
    if (res.code === 0) {
      ElMessage.success('评论发布成功')
      commentContent.value = ''
      commentImageUrl.value = ''
      // 清除ImageUpload组件中的图片
      if (imageUploadRef.value) {
        imageUploadRef.value.imageUrl = ''
      }
      await refreshComments(songId)
    } else {
      ElMessage.error('评论发布失败')
    }
  } catch (error) {
    ElMessage.error('评论发布失败')
  }
}

// 表情选择处理
const handleEmojiSelect = (emoji: string) => {
  commentContent.value = commentContent.value + emoji
}

// 图片上传处理
const handleImageUpload = (imageUrl: string) => {
  commentImageUrl.value = imageUrl
}

// 图片删除处理
const handleImageRemove = () => {
  commentImageUrl.value = ''
  // 同时清除ImageUpload组件中的图片
  if (imageUploadRef.value) {
    imageUploadRef.value.imageUrl = ''
  }
}

// 获取当前图片URL（用于显示预览）
const currentImageUrl = computed(() => {
  return commentImageUrl.value || (imageUploadRef.value?.imageUrl || '')
})

// 统一获取当前歌曲ID（模板中避免直接 .value）
function getSid(): number | null {
  return (songDetail as any)?.value?.songId ?? (songDetail as any)?.songId ?? null
}

// gotoProfileFromDrawer 已覆盖场景

// 关闭抽屉后再跳转，避免抽屉残留
function gotoProfileFromDrawer(uid: number) {
  try { (window as any).dispatchEvent(new CustomEvent('drawer:close')) } catch {}
  // 下一轮事件循环中再跳转，避免同帧渲染冲突
  ;(globalThis.setTimeout || window.setTimeout)(() => router.push(`/profile/${uid}`), 0)
}

// ================== 评论数据刷新与归一 ==================
function normalizeComments(list: any[]): any[] {
  if (!Array.isArray(list)) return []
  const byId: Record<number, any> = {}
  const roots: any[] = []
  list.forEach((it:any)=>{
    it.pCommentId = it.pCommentId ?? it.pcommentId ?? 0
    it.children = Array.isArray(it.children) ? it.children : []
    byId[it.commentId] = it
  })
  list.forEach((it:any)=>{
    const pid = Number(it.pCommentId||0)
    if (pid>0 && byId[pid]) byId[pid].children.push(it)
    else if (pid===0) roots.push(it)
  })
  return roots
}

async function refreshComments(sid: number) {
  try {
    const res: any = await getSongComments(sid)
    if (res?.code === 0) {
      const list = Array.isArray(res.data) ? res.data : []
      const norm = list.map((it:any)=>({
        ...it,
        pCommentId: it.pCommentId ?? it.pcommentId ?? 0,
        children: Array.isArray(it.children) ? it.children : []
      }))
      const hasChildren = norm.some((it:any)=>Array.isArray(it.children) && it.children.length)
      const hasFlatChild = norm.some((it:any)=>Number(it.pCommentId||0) > 0)
      const tree = hasChildren ? norm : (hasFlatChild ? normalizeComments(norm) : norm)
      if ((songDetail as any)?.value) {
        ;(songDetail as any).value = { ...(songDetail as any).value, comments: tree }
      }
    }
  } catch {}
}

const formatDate = (date: string) => {
  return new Date(date).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

// 本地（持久）记录当前用户已点赞的评论，避免刷新后状态丢失
const likedCommentIds = ref<Set<number>>(new Set())
function getLikeStorageKey(){
  const uid = getUid()
  return uid ? `cmLike:${uid}` : 'cmLike:guest'
}
function loadLikedFromStorage(){
  try{
    const raw = localStorage.getItem(getLikeStorageKey())
    if (raw){
      const arr = JSON.parse(raw)
      if (Array.isArray(arr)) likedCommentIds.value = new Set<number>(arr as number[])
    }
  }catch{}
}
function saveLikedToStorage(){
  try{
    localStorage.setItem(getLikeStorageKey(), JSON.stringify(Array.from(likedCommentIds.value)))
  }catch{}
}
// 初次加载/用户切换时同步
loadLikedFromStorage()
watch(()=>getUid(), ()=>{ loadLikedFromStorage() })

// 处理点赞/取消点赞
const handleLike = async (comment: any) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录'); try { window.dispatchEvent(new CustomEvent('auth:show')) } catch {}
    return
  }

  try {
    const hasLiked = likedCommentIds.value.has(comment.commentId)
    if (!hasLiked) {
      const res = await likeComment(comment.commentId)
      if (res.code === 0) {
        // +1 并标记
        if (songDetail.value && songDetail.value.comments) {
          const updated = songDetail.value.comments.map(item => {
            if (item.commentId === comment.commentId) {
              return { ...item, likeCount: (item.likeCount || 0) + 1 }
            }
            return item
          })
          songDetail.value = { ...songDetail.value, comments: updated }
        }
        likedCommentIds.value.add(comment.commentId)
        saveLikedToStorage()
        ElMessage.success('点赞成功')
        // 刷新一次，保证嵌套结构同步
        const sid = getSid()
        if (sid) { await refreshComments(sid) }
      }
    } else {
      const res = await cancelLikeComment(comment.commentId)
      if (res.code === 0) {
        // -1 并取消标记
        if (songDetail.value && songDetail.value.comments) {
          const updated = songDetail.value.comments.map(item => {
            if (item.commentId === comment.commentId) {
              const next = Math.max(0, (item.likeCount || 0) - 1)
              return { ...item, likeCount: next }
            }
            return item
          })
          songDetail.value = { ...songDetail.value, comments: updated }
        }
        likedCommentIds.value.delete(comment.commentId)
        saveLikedToStorage()
        ElMessage.success('已取消点赞')
        const sid = getSid()
        if (sid) { await refreshComments(sid) }
      }
    }
  } catch (error) {
    ElMessage.error('点赞失败')
  }
}

// 删除评论（按ID）
const handleDelete = async (id: number) => {
  try {
    const res = await deleteComment(id)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      const songId = getSid()
      if (songId) { await refreshComments(songId) }
    } else {
      ElMessage.error('删除失败')
    }
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

// 回复评论
const handleReply = async ({ content, imgPath, replyCommentId }: { content: string; imgPath?: string; replyCommentId?: number }) => {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  if (!content && !imgPath) { ElMessage.warning('请输入评论内容或上传图片'); return }
  const sid = getSid(); if (!sid) return
  const res = await addSongComment({ songId: sid, content, imgPath, replyCommentId })
  if (res.code === 0) { await refreshComments(sid) }
}
</script>

<template>
  <div class="h-full p-6 overflow-y-auto mr-16">
    <div v-if="songDetail" class="space-y-6">
      <!-- 歌曲信息 -->
      <div class="space-y-2">
        <h3 class="text-xl font-semibold text-primary-foreground">歌曲信息</h3>
        <div class="grid grid-cols-2 gap-4 text-sm text-muted-foreground">
          <div>
            <span class="text-primary-foreground">专辑：</span>
            {{ songDetail.album }}
          </div>
          <div>
            <span class="text-primary-foreground">发行时间：</span>
            {{ formatDate(songDetail.releaseTime) }}
          </div>
        </div>
      </div>

      <!-- 歌词面板 -->
      <div class="space-y-2">
        <h3 class="text-xl font-semibold text-primary-foreground mt-6">歌词</h3>
        <LyricPanel :songId="songDetail.songId" :currentTime="audio.currentTime.value" />
      </div>

      <!-- 评论区 -->
      <div class="space-y-4">
        <h3 class="text-xl font-semibold text-primary-foreground mt-12">评论（{{ formatNumber(songDetail.comments?.length || 0) }}）</h3>
        
        <!-- 评论输入框 -->
        <div class="mb-4">
          <div class="flex items-start gap-3">
            <div class="flex-1">
              <el-input
                v-model="commentContent"
                type="textarea"
                :rows="4"
                :maxlength="maxLength"
                placeholder="说点什么吧"
                resize="none"
                show-word-limit
              />
              
              <!-- 图片预览 -->
              <div v-if="currentImageUrl" class="mt-3">
                <div class="relative inline-block">
                  <img :src="currentImageUrl" alt="评论图片" class="w-20 h-20 object-cover rounded-lg border" />
                  <button @click="handleImageRemove" class="absolute -top-2 -right-2 w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center text-xs hover:bg-red-600">
                    ×
                  </button>
                </div>
              </div>
              
              <!-- 工具栏 -->
              <div class="flex items-center justify-between mt-4">
                <div class="flex items-center gap-2">
                  <!-- 表情选择器 -->
                  <EmojiPicker @select="handleEmojiSelect" />
                  <!-- 图片上传 -->
                  <ImageUpload ref="imageUploadRef" @upload="handleImageUpload" @remove="handleImageRemove" />
                </div>
                
                <button @click="handleComment" :disabled="!commentContent.trim() && !currentImageUrl"
                  class="px-6 py-1.5 bg-primary text-white rounded-full text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-primary/90 transition-colors">
                  发布
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 评论列表（树形） -->
        <div v-if="comments.length > 0" class="space-y-4">
          <CommentThread
            :list="comments"
            :on-reply="handleReply"
            :current-username="currentUsername"
            @like="handleLike"
            @profile="gotoProfileFromDrawer"
            @delete="handleDelete"
          />
        </div>
        <div v-else class="text-center py-8 text-gray-500"><p>暂无评论，快来抢沙发吧~</p></div>
      </div>
    </div>
    <div v-else class="flex items-center justify-center h-full">
      <el-empty description="暂无歌曲信息" />
    </div>
  </div>
</template>

<style scoped>
.el-button {
  --el-button-hover-text-color: var(--el-color-primary);
  --el-button-hover-bg-color: transparent;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
}

:deep(.el-textarea__inner) {
  border-radius: 12px !important;
}
</style>
