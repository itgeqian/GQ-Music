<script setup lang="ts">
import { getPlaylistDetail, addPlaylistComment, likeComment, cancelLikeComment, deleteComment } from '@/api/system'
import EmojiPicker from '@/components/EmojiPicker.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import { CommentThread } from '@/components/CommentThread'
import { formatNumber } from '@/utils'
import type { PlaylistDetail, Song } from '@/api/interface'
import coverImg from '@/assets/cover.png'
import { usePlaylistStore } from '@/stores/modules/playlist'
import { useFavoriteStore } from '@/stores/modules/favorite'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserStore } from '@/stores/modules/user'

const route = useRoute()
const router = useRouter()
const goBack = () => router.back()
const audui = AudioStore()
const playlistStore = usePlaylistStore()
const favoriteStore = useFavoriteStore()
const userStore = UserStore()
const playlist = computed(() => playlistStore.playlist)
const songs = computed(() => playlistStore.songs)
const { loadTrack, play } = useAudioPlayer()

// 添加激活的选项卡变量
const activeTab = ref('songs')

// 计算当前歌单是否已收藏
const isCollected = computed(() => {
  const playlistId = Number(route.params.id)
  return favoriteStore.favoritePlaylists.some(item => item.id === playlistId)
})

// 小数字：歌曲/评论数量（与歌手详情一致的回显逻辑）
const songCount = computed(() => songs.value.length)
const commentCount = computed(() => comments.value.length)

// 收藏/取消收藏歌单
const toggleCollect = async () => {
  try {
    const playlistId = Number(route.params.id)
    if (isCollected.value) {
      await favoriteStore.cancelCollectPlaylist(playlistId)
    } else {
      await favoriteStore.collectPlaylist(playlistId)
    }
  } catch (error) {
    ElMessage.error('操作失败')
  }
}
// 关闭播放器抽屉并跳转个人主页
function gotoProfile(userId: any) {
  if (userId == null) return
  ;(window as any).dispatchEvent(new CustomEvent('drawer:close'))
  router.push(`/profile/${userId}`)
}

interface PlaylistComment {
  commentId: number
  username: string
  userAvatar: string
  content: string
  createTime: string
  likeCount: number
  imgPath?: string
}

// 评论相关
const commentContent = ref('')
const commentImageUrl = ref('')
const maxLength = 180
const imageUploadRef = ref()
const comments = computed(() => {
  const raw0 = (playlistStore.playlist?.comments || []) as any[]
  const raw = raw0.map((it:any)=>({
    ...it,
    pCommentId: it.pCommentId ?? it.pcommentId ?? 0,
    children: Array.isArray(it.children) ? it.children : []
  }))
  if (!Array.isArray(raw)) return []
  const hasChildren = raw.some((it:any)=>Array.isArray(it.children) && it.children.length)
  const hasFlatChild = raw.some((it:any)=>Number(it.pCommentId||0) > 0)
  if (hasChildren) return raw
  if (!hasFlatChild) return [...raw].sort((a:any,b:any)=>b.commentId-a.commentId)
  const byId: Record<number, any> = {}; const roots: any[] = []
  raw.forEach(it => { byId[it.commentId] = it })
  raw.forEach(it => { const pid = Number((it as any).pCommentId||0); if (pid>0 && byId[pid]) byId[pid].children.push(it); else if (pid===0) roots.push(it) })
  return roots
})

// 获取当前用户名
const currentUsername = computed(() => userStore.userInfo?.username || '')

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
    const playlistId = Number(route.params.id)
    const content = commentContent.value.trim()
    
    const res = await addPlaylistComment({
      playlistId,
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
      // 重新获取歌单详情以更新评论列表
      const detailRes = await getPlaylistDetail(playlistId)
      if (detailRes.code === 0 && detailRes.data) {
        const playlistData = detailRes.data as PlaylistDetail
        playlistStore.setPlaylistInfo({
          ...playlistStore.playlist!,
          comments: playlistData.comments || []
        })
      }
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

// 图片预览处理
const previewImage = (imageUrl: string) => {
  // 使用Element Plus的图片预览功能
  const img = new Image()
  img.src = imageUrl
  img.onload = () => {
    // 创建预览弹窗
    ElMessageBox.alert('', '图片预览', {
      dangerouslyUseHTMLString: true,
      customClass: 'image-preview-dialog',
      showClose: true,
      message: `<img src="${imageUrl}" style="max-width: 100%; max-height: 80vh; object-fit: contain;" />`
    })
  }
}

// 会话内记录已点赞的评论，避免重复 +1
const likedCommentIds = ref<Set<number>>(new Set())

// 处理点赞/取消点赞
const handleLike = async (comment: PlaylistComment) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录'); try { window.dispatchEvent(new CustomEvent('auth:show')) } catch {}
    return
  }

  try {
    const hasLiked = likedCommentIds.value.has(comment.commentId)
    if (!hasLiked) {
      const res = await likeComment(comment.commentId)
      if (res.code === 0) {
        const updatedComments = comments.value.map(item => {
          if (item.commentId === comment.commentId) {
            return { ...item, likeCount: (item.likeCount || 0) + 1 }
          }
          return item
        })
        playlistStore.setPlaylistInfo({
          ...playlistStore.playlist!,
          comments: updatedComments
        })
        likedCommentIds.value.add(comment.commentId)
        ElMessage.success('点赞成功')
        // 刷新一次，保证嵌套结构同步
        const pid = Number(route.params.id)
        const detailRes = await getPlaylistDetail(pid)
        if (detailRes.code === 0 && detailRes.data) {
          const data = detailRes.data as any
          playlistStore.setPlaylistInfo({ ...playlistStore.playlist!, comments: data.comments || [] })
        }
      }
    } else {
      const res = await cancelLikeComment(comment.commentId)
      if (res.code === 0) {
        const updatedComments = comments.value.map(item => {
          if (item.commentId === comment.commentId) {
            const next = Math.max(0, (item.likeCount || 0) - 1)
            return { ...item, likeCount: next }
          }
          return item
        })
        playlistStore.setPlaylistInfo({
          ...playlistStore.playlist!,
          comments: updatedComments
        })
        likedCommentIds.value.delete(comment.commentId)
        ElMessage.success('已取消点赞')
        const pid = Number(route.params.id)
        const detailRes = await getPlaylistDetail(pid)
        if (detailRes.code === 0 && detailRes.data) {
          const data = detailRes.data as any
          playlistStore.setPlaylistInfo({ ...playlistStore.playlist!, comments: data.comments || [] })
        }
      }
    }
  } catch (error) {
    ElMessage.error('点赞失败')
  }
}

// 删除评论
const handleDelete = async (comment: PlaylistComment) => {
  try {
    const res = await deleteComment(comment.commentId)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      // 重新获取歌单详情以更新评论列表
      const playlistId = Number(route.params.id)
      const detailRes = await getPlaylistDetail(playlistId)
      if (detailRes.code === 0 && detailRes.data) {
        const playlistData = detailRes.data as PlaylistDetail
        playlistStore.setPlaylistInfo({
          ...playlistStore.playlist!,
          comments: playlistData.comments || []
        })
      }
    } else {
      ElMessage.error('删除失败')
    }
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

watch(
  () => route.params.id,
  async (id) => {
    if (id) {
      playlistStore.setPlaylistInfo(null)
      playlistStore.setSongs([])
      const res = await getPlaylistDetail(Number(id))
      if (res.code === 0 && res.data && typeof res.data === 'object' && 'songs' in res.data) {
        const playlistData = res.data as PlaylistDetail
        // 转换歌曲数据为 Song 类型
        const convertedSongs: Song[] = playlistData.songs.map(song => ({
          songId: song.songId,
          songName: song.songName,
          // 名称
          artistName: song.artistName,
          album: song.album,
          // 可选ID（用于跳转）。后端若未返回则为 undefined
          artistId: (song as any).artistId,
          albumId: (song as any).albumId,
          duration: song.duration,
          coverUrl: song.coverUrl || coverImg,
          audioUrl: song.audioUrl,
          likeStatus: song.likeStatus,
          releaseTime: song.releaseTime
        }))

        playlistStore.setSongs(convertedSongs)
        playlistStore.setPlaylistInfo({
          name: playlistData.title,
          description: playlistData.introduction,
          coverImgUrl: playlistData.coverUrl || coverImg,
          creator: {
            nickname: (playlistData as any).creatorName || 'GQ Music',
            avatarUrl: (playlistData as any).creatorAvatar || coverImg
          },
          trackCount: playlistData.songs.length,
          tracks: convertedSongs,
          commentCount: playlistData.comments?.length || 0,
          tags: [],
          comments: playlistData.comments || []
        })
      }
    }
  },
  { immediate: true }
)

const handlePlayAll = async () => {
  audui.setAudioStore('trackList', [])

  if (!songs.value.length) return

  const result = songs.value.map(song => ({
    id: song.songId.toString(),
    title: song.songName,
    artist: song.artistName,
    album: song.album,
    cover: song.coverUrl || coverImg,
    url: song.audioUrl,
    duration: parseFloat(song.duration) * 1000,
    likeStatus: song.likeStatus
  }))

  audui.setAudioStore('trackList', result)
  audui.setAudioStore('currentSongIndex', 0)
  await loadTrack()
  play()
}

// 搜索与排序（歌曲 Tab）
const kw = ref('')
type SortKey = 'default' | 'title' | 'artist' | 'album'
const order = ref<SortKey>('default')
const filteredSongs = computed(() => {
  const list = songs.value || []
  const q = kw.value.trim().toLowerCase()
  let result = list
  if (q) {
    result = list.filter((s: any) =>
      (s.songName || '').toLowerCase().includes(q) ||
      (s.artistName || '').toLowerCase().includes(q) ||
      (s.album || '').toLowerCase().includes(q)
    )
  }
  const arr = [...result]
  const cmp = (a: any, b: any, field: string) => String(a?.[field] || '').localeCompare(String(b?.[field] || ''), 'zh')
  if (order.value === 'title') arr.sort((a,b)=>cmp(a,b,'songName'))
  else if (order.value === 'artist') arr.sort((a,b)=>cmp(a,b,'artistName'))
  else if (order.value === 'album') arr.sort((a,b)=>cmp(a,b,'album'))
  return arr
})
</script>
<template>
  <div class="flex flex-col h-full flex-1 md:overflow-hidden">
    <!-- 顶部返回按钮单独一行，避免破坏详情区域的横向布局 -->
    <div class="px-6 pt-4">
      <button @click="goBack" class="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors">
        <icon-akar-icons:arrow-left />
        返回
      </button>
    </div>

    <div class="flex flex-col md:flex-row p-6 gap-6">
      <div class="flex-shrink-0 w-60 h-60">
        <img :alt="playlist?.name" class="w-full h-full object-cover rounded-lg shadow-lg"
          :src="(playlist?.coverImgUrl || coverImg) + '?param=500y500'" />
      </div>
      <div class="flex flex-col justify-between">
        <div>
          <h1 class="text-3xl font-bold mb-2">{{ playlist?.name }}</h1>
          <p class="text-muted-foreground mb-4 line-clamp-2" :title="playlist?.description">
            {{ playlist?.description }}
          </p>
          <div class="flex items-center gap-2 text-sm text-muted-foreground mb-4">
            <span class="relative flex shrink-0 overflow-hidden rounded-full w-6 h-6">
              <img class="aspect-square h-full w-full" :alt="playlist?.creator.nickname"
                :src="playlist?.creator.avatarUrl" /></span>
            <span>{{ playlist?.creator.nickname }}</span>
            <span>•</span>
            <span>{{ playlist?.trackCount }} 首歌曲</span>
          </div>
          <div class="flex items-center gap-2 text-sm text-muted-foreground" v-if="playlist?.tags">
            <el-tag v-for="tag in playlist?.tags" class="text-sm" effect="dark" :key="tag">{{ tag }}
            </el-tag>
          </div>
        </div>
        <div class="flex items-center gap-4 mt-4">
          <button @click="handlePlayAll"
            class="text-white inline-flex items-center justify-center gap-2 whitespace-nowrap text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 rounded-lg px-8">
            <icon-solar:play-line-duotone />
            播放全部</button>
          <button @click="toggleCollect"
            class="inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 border border-input bg-hoverMenuBg h-10 w-10 rounded-lg border-2 border-gray-300"
            :class="{ 'text-red-500': isCollected }">
            <icon-ic:round-favorite v-if="isCollected" class="text-xl" />
            <icon-ic:round-favorite-border v-else class="text-xl" />
          </button>
        </div>
      </div>
    </div>
    <div class="px-6 mb-3 flex items-center">
      <div class="ml-auto flex items-center gap-3">
      <div class="relative">
        <icon-akar-icons:search class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
        <input v-model="kw" placeholder="搜索" class="h-9 rounded-md border bg-background pl-9 px-3 text-sm" />
      </div>
      <el-dropdown>
        <el-button class="h-9">排序</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :class="{ 'is-active': order==='default' }" @click="order='default'">默认排序</el-dropdown-item>
            <el-dropdown-item :class="{ 'is-active': order==='title' }" @click="order='title'">歌曲名</el-dropdown-item>
            <el-dropdown-item :class="{ 'is-active': order==='artist' }" @click="order='artist'">歌手</el-dropdown-item>
            <el-dropdown-item :class="{ 'is-active': order==='album' }" @click="order='album'">专辑</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      </div>
    </div>
    <!-- 选项卡组件 -->
    <div class="px-6 flex-1 flex flex-col overflow-hidden">
      <div class="border-0 p-0 m-0 bg-transparent" style="background: transparent">
        <div
          class="inline-flex h-10 items-center rounded-lg p-1 text-muted-foreground w-full justify-start mb-2"
          style="background: transparent; backdrop-filter: none; -webkit-backdrop-filter: none; box-shadow: none;">
          <button v-for="tab in [
            { name: '歌曲', value: 'songs' },
            { name: '评论', value: 'comments' }
          ]" :key="tab.value" @click="activeTab = tab.value" :class="{
            'bg-activeMenuBg text-foreground shadow-sm': activeTab === tab.value
          }"
            class="inline-flex items-center justify-center whitespace-nowrap rounded-sm px-3 py-1.5 text-sm font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50">
                {{ tab.name }}
                <span v-if="tab.value==='songs' && songCount" class="ml-1 text-xs opacity-80">({{ formatNumber(songCount) }})</span>
                <span v-else-if="tab.value==='comments' && commentCount" class="ml-1 text-xs opacity-80">({{ formatNumber(commentCount) }})</span>
          </button>
        </div>
      </div>

      <!-- 内容区域 -->
      <div class="flex-1 overflow-y-auto min-h-0">
        <div v-show="activeTab === 'songs'">
      <Table :data="filteredSongs" />
        </div>
        <div v-show="activeTab === 'comments'" class="py-4">
          <!-- 评论输入框 -->
          <div class="p-4 mb-4">
            <div class="flex items-start gap-3 mr-8">
              <div class="flex-1">
                <el-input
                  v-model="commentContent"
                  type="textarea"
                  :rows="3"
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

          <!-- 评论列表 -->
            <div class="mb-6 ml-6">
              <h3 class="font-bold mb-4">最新评论（{{ formatNumber(commentCount) }}）</h3>
              <div v-if="comments.length">
                <CommentThread :list="comments" :on-reply="async ({content,imgPath,replyCommentId})=>{
                  if(!userStore.isLoggedIn){ ElMessage.warning('请先登录'); return }
                  if(!content && !imgPath){ ElMessage.warning('请输入评论内容或上传图片'); return }
                  const playlistId = Number(route.params.id)
                  const res = await addPlaylistComment({ playlistId, content, imgPath, replyCommentId })
                  if(res.code===0){ const detailRes = await getPlaylistDetail(playlistId); if(detailRes.code===0&&detailRes.data){ const data = detailRes.data as any; playlistStore.setPlaylistInfo({ ...playlistStore.playlist!, comments: data.comments||[] }) } } else { ElMessage.error(res.message||'回复失败') }
                }" :current-username="currentUsername" @like="handleLike" @profile="(uid:number)=>router.push(`/profile/${uid}`)" @delete="async (id:number)=>{ const res = await deleteComment(id); if(res.code===0){ const pid = Number(route.params.id); const r = await getPlaylistDetail(pid); if(r.code===0&&r.data){ playlistStore.setPlaylistInfo({ ...playlistStore.playlist!, comments: (r.data as any).comments||[] }) } } }" />
              </div>
              <div v-else class="text-center py-8 text-gray-500">
                <p>暂无评论，快来抢沙发吧~</p>
              </div>
            </div>
        </div>
      </div>
    </div>

  </div>
  <BackTop />
</template>

<style scoped>
:deep(.el-input__wrapper) {
  border-radius: 8px;
}

:deep(.el-textarea__inner) {
  border-radius: 12px !important;
}
</style>
