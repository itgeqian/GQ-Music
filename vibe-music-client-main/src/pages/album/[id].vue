<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Table from '@/components/Table.vue'
import BackTop from '@/components/BackTop.vue'
import { getAlbumDetail, getSongsByAlbumId, getArtistDetail, collectAlbum, cancelCollectAlbum, addAlbumComment, getAlbumComments, likeComment, cancelLikeComment, isAlbumCollected, deleteComment } from '@/api/system'
import type { Song } from '@/api/interface'
import coverImg from '@/assets/cover.png'
import { ElMessage } from 'element-plus'
import { UserStore } from '@/stores/modules/user'
import EmojiPicker from '@/components/EmojiPicker.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import { CommentThread } from '@/components/CommentThread'

const route = useRoute()
const router = useRouter()
const userStore = UserStore()

const albumId = computed(() => Number(route.params.id))
// 仅当当前路由确实是专辑详情时才进行校验/拉取，避免在其它页面触发弹窗
const isAlbumRoute = computed(() => route.path.startsWith('/album/'))

const album = ref({
  albumId: 0,
  artistId: 0,
  title: '',
  coverUrl: '',
  releaseDate: '',
  category: '',
  introduction: '',
  details: ''
})
const artist = ref<any>(null)
const songs = ref<Song[]>([])
const activeTab = ref<'songs' | 'info' | 'comments'>('songs')
const commentContent = ref('')
const maxLength = 180
const comments = ref<Array<any>>([])
const likedCommentIds = ref<Set<number>>(new Set())
const isCollected = ref(false)
const currentUsername = computed(() => userStore.userInfo?.username || '')

// 图片上传相关
const commentImageUrl = ref('')
const imageUploadRef = ref()

// 小数字回显：歌曲与评论数
const songCount = computed(() => songs.value.length)
const commentCount = computed(() => comments.value.length)

// 获取当前图片URL（用于显示预览）
const currentImageUrl = computed(() => {
  return commentImageUrl.value || (imageUploadRef.value?.imageUrl || '')
})

const tabs: Array<{ name: string; value: 'songs' | 'info' | 'comments' }> = [
  { name: '歌曲', value: 'songs' },
  { name: '专辑信息', value: 'info' },
  { name: '评论', value: 'comments' }
]

const fetchAlbum = async (): Promise<boolean> => {
  const res = await getAlbumDetail(albumId.value)
  if (res.code === 0 && res.data) {
    Object.assign(album.value, res.data)
    // 二次拉取：仅在拿到 artistId 后请求歌手详情
    if (album.value.artistId) {
      try {
        const ar = await getArtistDetail(album.value.artistId)
        if (ar?.code === 0) artist.value = ar.data || null
      } catch {}
    }
    return true
  }
  return false
}

const fetchAlbumSongs = async () => {
  const res: any = await getSongsByAlbumId({ albumId: albumId.value, pageNum: 1, pageSize: 200 })
  songs.value = (res?.data?.items || []) as Song[]
}

function buildTree(list:any[]) {
  if (!Array.isArray(list)) return []
  const byId: Record<number, any> = {}
  const roots: any[] = []
  list.forEach(it => {
    // 归一字段：后端可能返回 pcommentId（小写c）
    const pidRaw = (it as any).pCommentId ?? (it as any).pcommentId ?? 0
    it.pCommentId = pidRaw
    if (!Array.isArray(it.children)) it.children = []
    byId[it.commentId] = it
  })
  list.forEach(it => {
    const pid = Number((it as any).pCommentId || 0)
    if (pid > 0 && byId[pid]) byId[pid].children.push(it)
    else if (pid === 0) roots.push(it)
  })
  return roots
}

const fetchAlbumComments = async () => {
  const res: any = await getAlbumComments(albumId.value)
  if (res?.code === 0) {
    const list = (res.data || []) as any[]
    try { console.log('[album] raw comments:', JSON.parse(JSON.stringify(list))) } catch {}
    const norm = list.map((it:any)=>({
      ...it,
      pCommentId: it.pCommentId ?? it.pcommentId ?? 0,
      children: Array.isArray(it.children) ? it.children : []
    }))
    try { console.log('[album] normalized comments:', JSON.parse(JSON.stringify(norm))) } catch {}
    const hasChildren = norm.some((it:any)=>Array.isArray(it.children) && it.children.length)
    const hasFlatChild = norm.some((it:any)=>Number(it.pCommentId||0) > 0)
    comments.value = hasChildren ? norm : (hasFlatChild ? buildTree(norm) : norm)
    try { console.log('[album] final comments tree:', JSON.parse(JSON.stringify(comments.value))) } catch {}
  }
}

const toggleCollect = async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录'); try { window.dispatchEvent(new CustomEvent('auth:show')) } catch {}
    return
  }
  try {
    if (!isCollected.value) {
      const res = await collectAlbum(albumId.value)
      if (res.code === 0) {
        isCollected.value = true
        ElMessage.success('收藏成功')
        window.dispatchEvent(new CustomEvent('favorite:album:changed'))
      } else {
        ElMessage.error(res.message || '收藏失败')
      }
    } else {
      const res = await cancelCollectAlbum(albumId.value)
      if (res.code === 0) {
        isCollected.value = false
        ElMessage.success('已取消收藏')
        window.dispatchEvent(new CustomEvent('favorite:album:changed'))
      } else {
        ElMessage.error(res.message || '取消收藏失败')
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const goBack = () => router.back()

watch(
  () => route.params.id,
  async () => {
    if (!isAlbumRoute.value) return
    const idNum = Number(route.params.id)
    if (!Number.isFinite(idNum) || idNum <= 0) {
      router.replace('/library')
      return
    }
    const ok = await fetchAlbum()
    if (!ok) {
      router.replace('/library')
      return
    }
    await fetchAlbumSongs()
    await fetchAlbumComments()
    const fav = await isAlbumCollected(albumId.value)
    if (fav?.code === 0) isCollected.value = Boolean(fav.data)
  },
  { immediate: true }
)

const handlePublish = async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录'); try { window.dispatchEvent(new CustomEvent('auth:show')) } catch {}
    return
  }
  if (!commentContent.value.trim() && !commentImageUrl.value) {
    ElMessage.warning('请输入评论内容或上传图片')
    return
  }
  const res = await addAlbumComment({ 
    albumId: albumId.value, 
    content: commentContent.value.trim(),
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
    await fetchAlbumComments()
  } else {
    ElMessage.error(res.message || '发布失败')
  }
}

const handleLike = async (c: any) => {
  const id = c.commentId
  const liked = likedCommentIds.value.has(id)
  try {
    if (!liked) {
      const res = await likeComment(id)
      if (res.code === 0) {
        likedCommentIds.value.add(id)
        c.likeCount += 1
        // 刷新一次，保证子评论等嵌套结构同步
        await fetchAlbumComments()
      }
    } else {
      const res = await cancelLikeComment(id)
      if (res.code === 0) {
        likedCommentIds.value.delete(id)
        c.likeCount = Math.max(0, (c.likeCount || 0) - 1)
        await fetchAlbumComments()
      }
    }
  } catch {}
}

// 表情选择处理
const handleEmojiSelect = (emoji: string) => {
  commentContent.value += emoji
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

// 图片预览处理
const previewImage = (imageUrl: string) => {
  // 使用Element Plus的图片预览功能
  const img = new Image()
  img.src = imageUrl
  img.onload = () => {
    // 创建预览窗口
    const preview = document.createElement('div')
    preview.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.8);
      display: flex;
      justify-content: center;
      align-items: center;
      z-index: 9999;
      cursor: pointer;
    `
    preview.innerHTML = `
      <img src="${imageUrl}" style="max-width: 90%; max-height: 90%; object-fit: contain;" />
    `
    document.body.appendChild(preview)
    preview.onclick = () => {
      document.body.removeChild(preview)
    }
  }
}

// 二级回复提交
async function submitAlbumReply(payload: { content: string; imgPath?: string; replyCommentId?: number }) {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  if (!payload.content && !payload.imgPath) { ElMessage.warning('请输入评论内容或上传图片'); return }
  const res = await addAlbumComment({ albumId: albumId.value, content: payload.content, imgPath: payload.imgPath, replyCommentId: payload.replyCommentId })
  if (res.code === 0) { ElMessage.success('回复成功'); await fetchAlbumComments() } else { ElMessage.error(res.message || '回复失败') }
}

const handleDelete = async (c: any) => {
  if (!userStore.isLoggedIn) return
  if (c?.username !== currentUsername.value) return
  try {
    const res = await deleteComment(c.commentId)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      // 从本地列表移除，或重新拉取
      comments.value = comments.value.filter(item => item.commentId !== c.commentId)
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// 关闭播放器抽屉并跳转个人主页
function gotoProfile(userId: any) {
  if (userId == null) return
  ;(window as any).dispatchEvent(new CustomEvent('drawer:close'))
  router.push(`/profile/${userId}`)
}
</script>

<template>
  <div class="flex flex-col h-full flex-1 md:overflow-hidden">
    <div class="px-6 pt-4">
      <button @click="goBack" class="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors">
        <icon-akar-icons:arrow-left />
        返回
      </button>
    </div>

    <div class="flex flex-col md:flex-row p-6 gap-6">
      <div class="flex-shrink-0 w-60 h-60">
        <img :alt="album.title" class="w-full h-full object-cover rounded-lg shadow-lg" :src="(album.coverUrl || coverImg) + '?param=500y500'" />
      </div>
      <div class="flex flex-col justify-between flex-1">
        <div>
          <h1 class="text-3xl font-bold mb-2">{{ album.title }}</h1>
          <div class="flex items-center gap-3 text-sm text-muted-foreground mb-2">
            <span>发行时间：{{ album.releaseDate || '-' }}</span>
            <span v-if="album.category">• {{ album.category }}</span>
          </div>
          <!-- 简介：填充上方空白区域，过长显示两行省略 -->
          <p v-if="album.introduction" class="text-sm text-muted-foreground line-clamp-4 max-w-4xl">
            {{ album.introduction }}
          </p>
        </div>
        <div class="flex items-center gap-4 mt-4">
          <button @click="toggleCollect"
            class="inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 border border-input bg-hoverMenuBg h-10 w-10 rounded-lg border-2 border-gray-300"
            :class="{ 'text-red-500': isCollected }">
            <icon-ic:round-favorite v-if="isCollected" class="text-xl" />
            <icon-ic:round-favorite-border v-else class="text-xl" />
          </button>
        </div>
      </div>
    </div>

    <div class="px-6 flex-1 flex flex-col overflow-hidden">
      <div class="border-b border-transparent pb-1" style="background: transparent">
        <div class="inline-flex h-10 items-center rounded-lg bg-transparent p-1 text-muted-foreground w-full justify-start mb-2">
          <button v-for="tab in tabs" :key="tab.value" @click="activeTab = tab.value" :class="{
            'bg-activeMenuBg text-foreground shadow-sm': activeTab === tab.value
          }"
            class="inline-flex items-center justify-center whitespace-nowrap rounded-sm px-3 py-1.5 text-sm font-medium transition-all">
            {{ tab.name }}
            <span v-if="tab.value==='songs' && songCount" class="ml-1 text-xs opacity-80">({{ songCount }})</span>
            <span v-else-if="tab.value==='comments' && commentCount" class="ml-1 text-xs opacity-80">({{ commentCount }})</span>
          </button>
        </div>
      </div>

      <div class="flex-1 overflow-y-auto min-h-0">
        <div v-show="activeTab === 'songs'">
          <Table :data="songs" />
        </div>
        <div v-show="activeTab === 'info'" class="py-6 text-sm text-muted-foreground space-y-3">
          <p v-if="album.releaseDate">发行时间：{{ album.releaseDate }}</p>
          <p v-if="album.category">类型：{{ album.category }}</p>
          <p v-if="album.introduction">简介：</p>
          <p v-if="album.introduction" class="text-sm text-muted-foreground whitespace-pre-line line-clamp-4 max-w-4xl">{{ album.introduction }}</p>
          <p v-else>暂无简介</p>
          <p v-if="album.details">详情：</p>
          <p v-if="album.details" class="text-sm text-muted-foreground whitespace-pre-line max-w-4xl">{{ album.details }}</p>
        </div>
        <div v-show="activeTab === 'comments'" class="py-6">
          <div class="px-2 md:px-6">
            <div class="mb-4 flex items-start gap-3">
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

                  <button @click="handlePublish" :disabled="!commentContent.trim() && !currentImageUrl"
                    class="px-6 py-1.5 bg-primary text-white rounded-full text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-primary/90 transition-colors">
                    发布
                  </button>
                </div>
              </div>
            </div>

            <div v-if="comments.length === 0" class="text-sm text-muted-foreground text-center">暂无评论</div>
            <div v-else>
              <CommentThread :list="comments" :on-reply="submitAlbumReply" :current-username="currentUsername" @like="handleLike" @profile="(uid:number)=>router.push(`/profile/${uid}`)" @delete="async (id:number)=>{ const res = await deleteComment(id); if(res.code===0){ ElMessage.success('删除成功'); await fetchAlbumComments() } else { ElMessage.error(res.message||'删除失败') } }" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <BackTop />
  </div>
</template>


