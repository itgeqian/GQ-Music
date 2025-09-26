<script setup lang="ts">
import { Song } from '@/api/interface'
import { PropType, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { formatMillisecondsToTime } from '@/utils'
import default_album from '@/assets/default_album.jpg'
import { collectSong, cancelCollectSong, getMyPlaylists, addSongToMyPlaylist, createMyPlaylist, getAllArtists, getAllSongs } from '@/api/system'
import { UserStore } from '@/stores/modules/user'

const audio = AudioStore()
const userStore = UserStore()
const { loadTrack, play } = useAudioPlayer()

const props = defineProps({
  data: {
    type: Array as PropType<Song[]>,
    default: () => [],
  },
  clickableTitle: { type: Boolean, default: true },
  clickableArtist: { type: Boolean, default: true },
  clickableAlbum: { type: Boolean, default: true },
  // 图片懒加载时的滚动容器（如传入，则用于 IntersectionObserver root）
  scrollContainer: { type: [Object, String] as PropType<HTMLElement | string | null>, default: undefined },
  // 是否启用图片懒加载（某些场景如 Tab 切换会导致懒加载不触发，可关闭）
  imageLazy: { type: Boolean, default: true },
  // 仅在“最近播放”页面展示“移除”项
  showRemove: { type: Boolean, default: false },
})

// 监听数据变化，更新当前页面的歌曲列表
watch(() => props.data, (newData) => {
  audio.setCurrentPageSongs(newData)
}, { immediate: true })

// 转换歌曲实体
const convertToTrackModel = (song: Song) => {
  // console.log('原始歌曲数据:', song)
  if (!song.songId || !song.songName || !song.audioUrl) {
    console.error('歌曲数据不完整:', song)
    return null
  }
  return {
    id: song.songId.toString(),
    title: song.songName,
    artist: song.artistName,
    album: song.album,
    cover: song.coverUrl || default_album,
    url: song.audioUrl,
    duration: Number(song.duration) || 0,
    likeStatus: song.likeStatus || 0,
  }
}

// 播放音乐
const handlePlay = async (row: Song) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    try { window.dispatchEvent(new CustomEvent('auth:show')) } catch {}
    return
  }
  // 先将所有表格数据转换为 trackModel
  const allTracks = props.data
    .map(song => convertToTrackModel(song))
    .filter(track => track !== null)

  // 找到当前选中歌曲的索引
  const selectedIndex = props.data.findIndex(song => song.songId === row.songId)

  // 清空现有播放列表并添加所有歌曲
  audio.setAudioStore('trackList', allTracks)
  // 设置当前播放索引为选中的歌曲
  audio.setAudioStore('currentSongIndex', selectedIndex)

  // 加载并播放选中的歌曲
  await loadTrack()
  play()
}

// 更新所有相同歌曲的喜欢状态
const updateAllSongLikeStatus = (songId: number, status: number) => {
  // 更新播放列表中的状态
  audio.trackList.forEach(track => {
    if (Number(track.id) === songId) {
      track.likeStatus = status
    }
  })

  // 更新当前页面的歌曲列表状态
  if (audio.currentPageSongs) {
    audio.currentPageSongs.forEach(song => {
      if (song.songId === songId) {
        song.likeStatus = status
      }
    })
  }

  // 更新原始数据
  if (props.data) {
    const song = props.data.find(song => song.songId === songId)
    if (song) {
      song.likeStatus = status
    }
  }
}

// 跳转能力（歌手/专辑）
const router = useRouter()
const goArtist = async (row: Song, e: Event) => {
  if (!props.clickableArtist) return
  e.stopPropagation()
  const id = (row as any)?.artistId
  if (id) { router.push(`/artist/${id}`); return }
  // 兜底：按名称查询一次歌手
  try {
    const res: any = await getAllArtists({ pageNum: 1, pageSize: 1, artistName: row.artistName || '' })
    const target = res?.data?.items?.[0]
    if (target?.artistId) { router.push(`/artist/${target.artistId}`); return }
  } catch {}
  ElMessage.warning('未找到该歌手的详情信息')
}
const goAlbum = async (row: Song, e: Event) => {
  if (!props.clickableAlbum) return
  e.stopPropagation()
  const id = (row as any)?.albumId
  if (id) { router.push(`/album/${id}`); return }
  // 兜底：通过歌曲库按专辑名+歌手名找一个包含该专辑的条目，取其 albumId
  try {
    const res: any = await getAllSongs({ pageNum: 1, pageSize: 1, songName: '', artistName: row.artistName || '', album: row.album || '' })
    const target = res?.data?.items?.[0]
    if (target?.albumId) { router.push(`/album/${target.albumId}`); return }
  } catch {}
  ElMessage.warning('目前没有该专辑信息')
}

// 处理喜欢/取消喜欢
const handleLike = async (row: Song, e: Event) => {
  e.stopPropagation() // 阻止事件冒泡
  
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    try { window.dispatchEvent(new CustomEvent('auth:show')) } catch {}
    return
  }

  try {
    if (row.likeStatus === 0) {
      // 收藏歌曲
      const res = await collectSong(row.songId)
      if (res.code === 0) {
        updateAllSongLikeStatus(row.songId, 1)
        ElMessage.success('已添加到我的喜欢')
      } else {
        ElMessage.error(res.message || '添加到我的喜欢失败')
      }
    } else {
      // 取消收藏
      const res = await cancelCollectSong(row.songId)
      if (res.code === 0) {
        updateAllSongLikeStatus(row.songId, 0)
        ElMessage.success('已取消喜欢')
      } else {
        ElMessage.error(res.message || '取消喜欢失败')
      }
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

const downLoadMusic = async (row: Song, e: Event) => {
  e.stopPropagation() // 阻止事件冒泡
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    try { window.dispatchEvent(new CustomEvent('auth:show')) } catch {}
    return
  }
  const url = row.audioUrl || ''
  const extFromUrl = (() => {
    try {
      const u = new URL(url)
      const pathname = u.pathname || ''
      const m = pathname.match(/\.([a-zA-Z0-9]+)(?:\?|$)/)
      return m ? `.${m[1]}` : ''
    } catch {
      const m = url.match(/\.([a-zA-Z0-9]+)(?:\?|$)/)
      return m ? `.${m[1]}` : ''
    }
  })()
  const safeExt = extFromUrl && extFromUrl.length <= 6 ? extFromUrl : ''
  const fileName = `${row.songName} - ${row.artistName}${safeExt || ''}`
  try {
    // 使用 fetch 拉取 Blob，避免浏览器直接在当前页打开音频
    const response = await fetch(row.audioUrl, { mode: 'cors' })
    if (!response.ok) throw new Error('下载链接不可用')
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = fileName
    link.rel = 'noopener'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(blobUrl)
  } catch (err) {
    // 回退方案：在新标签打开，避免打断当前播放页
    const newWin = window.open(row.audioUrl, '_blank', 'noopener,noreferrer')
    if (!newWin) {
      ElMessage.error('下载失败，请检查网络或稍后再试')
    }
  }
}

// 判断是否是当前播放的歌曲
const isCurrentPlaying = (songId: number) => {
  const currentTrack = audio.trackList[audio.currentSongIndex]
  return currentTrack && Number(currentTrack.id) === songId
}

// 处理三个点更多菜单
const emit = defineEmits(['row-more-command'])
const handleMoreCommand = async (command: string, row: Song) => {
  if (command === 'like') {
    // 从菜单触发喜欢/取消喜欢
    // 构造一个带 stopPropagation 的伪事件
    const evt = { stopPropagation() {} } as unknown as Event
    await handleLike(row, evt)
    return
  }
  if (command === 'download') {
    const evt = { stopPropagation() {} } as unknown as Event
    await downLoadMusic(row, evt)
    return
  }
  if (command === 'add') {
    openAddToDialog(row)
    return
  }
  if (command === 'enqueueNext') {
    audio.insertNext(convertToTrackModel(row))
    ElMessage.success('已加入播放列表')
    return
  }
  // 最近播放页面会监听该事件以支持“移除”
  if (command === 'remove') {
    emit('row-more-command', 'remove', row)
    return
  }
}

// 添加到歌单弹窗
const addDialogVisible = ref(false)
const addDialogLoading = ref(false)
const addActiveTab = ref<'existing' | 'new'>('existing')
const currentSongForAdd = ref<Song | null>(null)
const myPlaylists = ref<any[]>([])
const selectedPlaylistId = ref<number | null>(null)
const newPlaylistForm = ref<{ title: string; style?: string; introduction?: string }>({ title: '' })

const openAddToDialog = async (row: Song) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    try { window.dispatchEvent(new CustomEvent('auth:show')) } catch {}
    return
  }
  currentSongForAdd.value = row
  addActiveTab.value = 'existing'
  selectedPlaylistId.value = null
  newPlaylistForm.value = { title: '' }
  addDialogVisible.value = true
  await fetchMyPlaylists()
}

const fetchMyPlaylists = async () => {
  try {
    addDialogLoading.value = true
    const res: any = await getMyPlaylists({ pageNum: 1, pageSize: 100 })
    if (res?.code === 0) {
      myPlaylists.value = res.data?.items || []
    }
  } finally {
    addDialogLoading.value = false
  }
}

const confirmAddToExisting = async () => {
  if (!currentSongForAdd.value || !selectedPlaylistId.value) {
    ElMessage.warning('请选择一个歌单')
    return
  }
  try {
    addDialogLoading.value = true
    const res: any = await addSongToMyPlaylist({
      playlistId: Number(selectedPlaylistId.value),
      songId: Number(currentSongForAdd.value.songId)
    })
    if (res.code === 0) {
      ElMessage.success('已添加到歌单')
      addDialogVisible.value = false
    } else {
      ElMessage.error(res.message || '添加失败')
    }
  } finally {
    addDialogLoading.value = false
  }
}

const createPlaylistAndAdd = async () => {
  if (!newPlaylistForm.value.title?.trim()) {
    ElMessage.warning('请输入歌单名称')
    return
  }
  if (!currentSongForAdd.value) return
  try {
    addDialogLoading.value = true
    const crt: any = await createMyPlaylist({
      title: newPlaylistForm.value.title.trim(),
      style: newPlaylistForm.value.style,
      introduction: newPlaylistForm.value.introduction
    })
    if (crt.code !== 0) {
      ElMessage.error(crt.message || '创建歌单失败')
      return
    }
    // 刷新一次我的歌单并找到新建的歌单 id（后端若返回 id 可直接使用）
    const list: any = await getMyPlaylists({ pageNum: 1, pageSize: 1, title: newPlaylistForm.value.title.trim() })
    const pid = list?.data?.items?.[0]?.playlistId
    const targetPid = pid || crt.data?.playlistId
    if (!targetPid) {
      ElMessage.warning('未获取到新歌单ID，请稍后重试')
      return
    }
    const bind: any = await addSongToMyPlaylist({ playlistId: Number(targetPid), songId: Number(currentSongForAdd.value.songId) })
    if (bind.code === 0) {
      ElMessage.success('已添加到新歌单')
      addDialogVisible.value = false
    } else {
      ElMessage.error(bind.message || '添加到新歌单失败')
    }
  } finally {
    addDialogLoading.value = false
  }
}
</script>

<template>
  <el-table :data="data" style="
      --el-table-border: none;
      --el-table-border-color: none;
      --el-table-tr-bg-color: none;
      --el-table-header-bg-color: none;
      --el-table-row-hover-bg-color: transparent;
    " class="!rounded-lg !h-full transition duration-300">
    <el-table-column>
      <template #header>
        <div class="grid grid-cols-[auto_4fr_3fr_3fr_1fr_2fr_1fr_1fr] items-center gap-6 w-full text-left mt-2">
          <div class="ml-3">标题</div>
          <div class="w-12"></div>
          <div class="ml-1">歌手</div>
          <div>专辑</div>
          <div>喜欢</div>
          <div class="ml-7">时长</div>
          <div>下载</div>
          <div>更多</div>
        </div>
      </template>
      <template #default="{ row }">
        <div
          class="grid grid-cols-[auto_4fr_3fr_3fr_1fr_2fr_1fr_1fr] items-center gap-6 w-full group transition duration-300 rounded-2xl p-2"
          :class="[
            isCurrentPlaying(row.songId) ? 'bg-[hsl(var(--hover-menu-bg))]' : 'hover:bg-[hsl(var(--hover-menu-bg))]',
            'cursor-pointer'
          ]"
          @click="handlePlay(row)">
          <!-- 标题和封面（无封面时补位以保证对齐） -->
          <div class="w-10 h-10 relative">
            <template v-if="row.coverUrl">
              <el-image :src="row.coverUrl" fit="cover" :lazy="props.imageLazy" :scroll-container="(props.scrollContainer as any) || undefined" :alt="row.songName" class="w-full h-full rounded-md" />
              <!-- Play 按钮，使用 group-hover 控制透明度 -->
              <div
                class="absolute inset-0 flex items-center justify-center text-white opacity-0 transition-opacity duration-300 z-10 group-hover:opacity-100 group-hover:bg-black/50 rounded-md">
                <icon-tabler:player-play-filled class="text-lg" />
              </div>
            </template>
            <template v-else>
              <div class="w-full h-full rounded-md bg-[hsl(var(--hover-menu-bg))]"></div>
            </template>
          </div>

          <!-- 歌曲名称（仅悬停高亮，不跳转） -->
          <div class="text-left">
            <span
              class="flex-1 line-clamp-1"
              :class="props.clickableTitle ? 'hover:text-[hsl(var(--el-color-primary))]' : ''"
            >
              {{ row.songName }}
            </span>
          </div>

          <!-- 歌手（悬停高亮，可跳转，受 clickableArtist 控制） -->
          <div class="text-left">
            <span
              class="line-clamp-1 w-48"
              :class="props.clickableArtist ? 'hover:text-[hsl(var(--el-color-primary))] hover:underline cursor-pointer' : ''"
              @click.stop="goArtist(row, $event)"
            >
              {{ row.artistName }}
            </span>
          </div>

          <!-- 专辑（悬停高亮，可跳转，受 clickableAlbum 控制） -->
          <div class="text-left">
            <span
              :class="props.clickableAlbum ? 'hover:text-[hsl(var(--el-color-primary))] hover:underline cursor-pointer' : ''"
              @click.stop="goAlbum(row, $event)"
            >
              {{ row.album || '未命名专辑' }}
            </span>
          </div>

          <!-- 喜欢 -->
          <div class="flex items-center ml-1">
            <el-button text circle @click="handleLike(row, $event)">
              <icon-mdi:cards-heart-outline v-if="!userStore.isLoggedIn || row.likeStatus === 0" class="text-lg" />
              <icon-mdi:cards-heart v-else class="text-lg text-red-500" />
            </el-button>
          </div>

          <!-- 时长 -->
          <div class="text-left ml-8">
            <span>{{ formatMillisecondsToTime(Number(row.duration) * 1000) }}</span>
          </div>

          <!-- 下载 -->
          <div class="flex items-center ml-1">
            <el-button text circle @click.stop="downLoadMusic(row, $event)">
              <icon-material-symbols:download class="text-lg" />
            </el-button>
          </div>

          <!-- 更多（三个点） -->
          <div class="flex items-center ml-1">
            <el-dropdown @command="(cmd:string)=>handleMoreCommand(cmd, row)">
              <el-button text circle @click.stop>
                <icon-hugeicons:more class="text-lg" />
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="like">{{ row.likeStatus===1 ? '取消喜欢' : '我喜欢' }}</el-dropdown-item>
                  <el-dropdown-item divided command="add">添加到</el-dropdown-item>
                  <el-dropdown-item divided command="enqueueNext">加入播放列表</el-dropdown-item>
                  <el-dropdown-item divided command="download">下载</el-dropdown-item>
                  <el-dropdown-item v-if="props.showRemove" divided command="remove">移除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>
    </el-table-column>
  </el-table>

  <!-- 添加到歌单 弹窗 -->
  <el-dialog v-model="addDialogVisible" width="520px" :close-on-click-modal="false" :destroy-on-close="true" title="添加到歌单">
    <el-tabs v-model="addActiveTab" type="card">
      <el-tab-pane label="已有歌单" name="existing">
        <el-skeleton v-if="addDialogLoading" animated rows="3" />
        <div v-else class="space-y-2 max-h-72 overflow-auto">
          <el-radio-group v-model="selectedPlaylistId" class="w-full">
            <div v-for="pl in myPlaylists" :key="pl.playlistId" class="flex items-center justify-between w-full py-2">
              <el-radio :label="pl.playlistId">{{ pl.title }}</el-radio>
              <el-tag size="small" v-if="pl.style">{{ pl.style }}</el-tag>
            </div>
          </el-radio-group>
        </div>
      </el-tab-pane>
      <el-tab-pane label="新建歌单" name="new">
        <el-form label-width="72px" class="pt-2">
          <el-form-item label="名称">
            <el-input v-model="newPlaylistForm.title" maxlength="40" show-word-limit placeholder="请输入歌单名称" />
          </el-form-item>
          <el-form-item label="风格">
            <el-select v-model="newPlaylistForm.style" placeholder="可选">
              <el-option label="华语流行" value="华语流行" />
              <el-option label="欧美流行" value="欧美流行" />
              <el-option label="日本流行" value="日本流行" />
              <el-option label="韩语流行" value="韩语流行" />
              <el-option label="轻音乐" value="轻音乐" />
              <el-option label="摇滚" value="摇滚" />
              <el-option label="电子" value="电子" />
            </el-select>
          </el-form-item>
          <el-form-item label="简介">
            <el-input v-model="newPlaylistForm.introduction" type="textarea" rows="3" maxlength="200" show-word-limit placeholder="可填写简介" />
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="addDialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="addDialogLoading" v-if="addActiveTab==='existing'" @click="confirmAddToExisting">确定</el-button>
        <el-button type="primary" :loading="addDialogLoading" v-else @click="createPlaylistAndAdd">创建并添加</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style scoped>
:deep(.el-table__row) {
  background: transparent !important;
}

:deep(.el-table__row:hover) td {
  background: transparent !important;
}

:deep(.el-table__cell) {
  padding: 0 !important;
}
/* 紧凑密度：通过 html[data-density="compact"] 控制 */
:global(html[data-density="compact"]) .table-compact :deep(.el-table__cell) {
  padding: 0 !important;
}
:global(html[data-density="compact"]) :deep(.grid.grid-cols-[auto_4fr_3fr_3fr_1fr_2fr_1fr_1fr]) {
  gap: 10px !important;
}
:global(html[data-density="compact"]) :deep(.group.rounded-2xl.p-2) {
  padding: 6px !important;
  border-radius: 10px !important;
}
/* 彻底去除表格黑边/背景色 */
:deep(.el-table) { background: transparent !important; }
:deep(.el-table__inner-wrapper) { background: transparent !important; }
:deep(.el-table__header-wrapper),
:deep(.el-table__body-wrapper) { background: transparent !important; }
:deep(.el-table__body-wrapper .el-scrollbar__wrap),
:deep(.el-table__header-wrapper .el-scrollbar__wrap) { background: transparent !important; }
:deep(.el-table th), :deep(.el-table td) { background: transparent !important; border-bottom-color: transparent !important; }
:deep(.el-table--border) { --el-table-border-color: transparent !important; }
:deep(.el-table__border-left-patch) { background: transparent !important; }
:deep(.el-table::before) { background: transparent !important; }
:deep(.el-table__empty-block) { background: transparent !important; }
</style>