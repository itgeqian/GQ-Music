<script setup lang="ts">
import Table from '@/components/Table.vue'
import { useRecentStore } from '@/stores/modules/recent'
import { getRecentPlays, removeRecentPlay, clearRecentPlays } from '@/api/system'
import { ElMessageBox, ElMessage } from 'element-plus'
import coverImg from '@/assets/cover.png'

const recent = useRecentStore()

// 将 recent.list 的 trackModel 映射为 Table 需要的 Song 结构字段
const localSongs = computed(() => {
  return recent.list.map((t) => ({
    songId: Number(t.id),
    songName: t.title,
    artistName: t.artist,
    album: t.album,
    // 本地记录通常没有 artistId/albumId，仅提供占位，Table 会容错
    artistId: (t as any).artistId,
    albumId: (t as any).albumId,
    // 保持以“秒”为单位，交给 Table 里 *1000 转 ms 格式化
    duration: String(Math.floor(Number(t.duration) || 0)),
    coverUrl: t.cover || coverImg,
    audioUrl: t.url,
    likeStatus: t.likeStatus || 0,
  }))
})

// 服务端分页数据
const serverSongs = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const fetchServer = async () => {
  const res: any = await getRecentPlays({ pageNum: pageNum.value, pageSize: pageSize.value })
  if (res?.code === 0) {
    const data = res.data || { items: [], total: 0 }
    // 服务器字段映射到表格
    serverSongs.value = (data.items || []).map((s: any) => ({
      songId: s.songId,
      songName: s.songName,
      artistName: s.artistName,
      album: s.album,
      // 关键：补充用于跳转的 ID（复用“喜欢”模块返回字段）
      artistId: s.artistId,
      albumId: s.albumId,
      // 后端返回的是秒的字符串/数值，这里保持为“秒”
      duration: String(Math.floor(Number(s.duration) || 0)),
      coverUrl: s.coverUrl || coverImg,
      audioUrl: s.audioUrl,
      likeStatus: s.likeStatus || 0,
    }))
    total.value = data.total || serverSongs.value.length
  } else {
    serverSongs.value = []
    total.value = 0
  }
}

// 搜索与排序
const search = ref('')
type SortKey = 'default' | 'title' | 'artist' | 'album'
const sortKey = ref<SortKey>('default')
const mergedSongs = computed(() => serverSongs.value.length ? serverSongs.value : localSongs.value)
const filteredSongs = computed(() => {
  const q = search.value.trim().toLowerCase()
  let list = mergedSongs.value
  if (q) {
    list = list.filter((s: any) =>
      (s.songName || '').toLowerCase().includes(q) ||
      (s.artistName || '').toLowerCase().includes(q) ||
      (s.album || '').toLowerCase().includes(q)
    )
  }
  const arr = [...list]
  const cmp = (a: any, b: any, field: string) => String(a?.[field] || '').localeCompare(String(b?.[field] || ''), 'zh')
  if (sortKey.value === 'title') arr.sort((a,b)=>cmp(a,b,'songName'))
  else if (sortKey.value === 'artist') arr.sort((a,b)=>cmp(a,b,'artistName'))
  else if (sortKey.value === 'album') arr.sort((a,b)=>cmp(a,b,'album'))
  return arr
})

onMounted(fetchServer)
// 监听新增最近播放事件，实时刷新服务端数据
onMounted(() => {
  const handler = () => fetchServer()
  window.addEventListener('recent:added', handler)
  onBeforeUnmount(() => window.removeEventListener('recent:added', handler))
})
// 事件处理需先声明，再在模板里使用
const onClearAll = async () => {
  try {
    await ElMessageBox.confirm('确定要清除全部最近播放记录吗？此操作不可撤销。', '清除确认', { type: 'warning' })
  } catch { return }
  try {
    await clearRecentPlays()
    ElMessage.success('已清空最近播放')
    serverSongs.value = []
    total.value = 0
    recent.clear()
  } catch {}
}

const onRowMore = async (cmd: string, row: any) => {
  if (cmd !== 'remove') return
  try {
    await removeRecentPlay({ songId: Number(row.songId) })
    ElMessage.success('已从最近播放移除')
    fetchServer()
  } catch {}
}
</script>

<template>
  <div class="flex flex-col h-full flex-1 md:overflow-hidden">
    <div class="flex items-center justify-between p-6">
      <div>
        <h1 class="text-3xl font-bold mb-2">最近播放</h1>
        <div class="text-sm text-muted-foreground">本地 {{ recent.list.length }} 首（保留200），服务端共 {{ total }} 首</div>
      </div>
      <button class="h-9 px-4 rounded-md hover:bg-hoverMenuBg" @click="onClearAll">清除全部记录</button>
    </div>

    <div class="px-6 mb-3 flex items-center">
      <div class="ml-auto flex items-center gap-3">
      <div class="relative">
        <icon-akar-icons:search class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
        <input v-model="search" placeholder="搜索" class="h-9 rounded-md border bg-background pl-9 px-3 text-sm" />
      </div>
      <el-dropdown>
        <el-button class="h-9">排序</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :class="{ 'is-active': sortKey==='default' }" @click="sortKey='default'">默认排序</el-dropdown-item>
            <el-dropdown-item :class="{ 'is-active': sortKey==='title' }" @click="sortKey='title'">歌曲名</el-dropdown-item>
            <el-dropdown-item :class="{ 'is-active': sortKey==='artist' }" @click="sortKey='artist'">歌手</el-dropdown-item>
            <el-dropdown-item :class="{ 'is-active': sortKey==='album' }" @click="sortKey='album'">专辑</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      </div>
    </div>

    <div class="flex-1 md:overflow-x-hidden">
      <Table :data="filteredSongs" :clickable-title="false" :show-remove="true" @row-more-command="onRowMore" />
      <nav class="mx-auto flex w-full justify-center mt-3" v-if="serverSongs.length">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10,20,30,50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchServer"
          @current-change="fetchServer"
          class="mb-3"
        />
      </nav>
    </div>
  </div>
  
</template>



