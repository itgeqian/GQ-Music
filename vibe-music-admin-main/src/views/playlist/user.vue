<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getUserPlaylists, recommendPlaylist, cancelRecommendPlaylist, getSongsOfPlaylist } from '@/api/system'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const form = reactive<{ title?: string; style?: string }>({})
const dataList = ref<any[]>([])

// 查看歌曲对话框状态
const viewVisible = ref(false)
const currentPlaylist = ref<{ id: number; title: string } | null>(null)
const songLoading = ref(false)
const songList = ref<any[]>([])
const songPage = ref(1)
const songSize = ref(10)
const songTotal = ref(0)

const fetchPinned = async () => {
  const userData = (await import('@/utils/auth')).getToken()
  return fetch('/admin/pinnedPlaylists', { headers: { Authorization: userData.accessToken } }).then(r=>r.json()).catch(()=>({code:1,data:[]}))
}

const fetchData = async () => {
  loading.value = true
  try {
    const [pinRes, res]: any = await Promise.all([
      fetchPinned(),
      getUserPlaylists({ pageNum: pagination.pageNum, pageSize: pagination.pageSize, title: form.title, style: form.style })
    ])
    const pinned = new Set((pinRes?.data || []).map((it:any)=>it.playlistId))
    if (res.code === 0 && res.data) {
      dataList.value = (res.data.items || []).map((it:any)=>({ ...it, isPinned: pinned.has(it.playlistId) }))
      pagination.total = Number(res.data.total || 0)
    }
  } finally {
    loading.value = false
  }
}

const handleRecommend = async (row: any) => {
  const { value } = await ElMessageBox.prompt('请输入推荐权重（数字，越大越靠前）', '设为推荐', { inputValue: '100' })
  const weight = parseInt(value || '100', 10)
  const res: any = await recommendPlaylist(row.playlistId, isNaN(weight) ? 100 : weight)
  if (res.code === 0) { row.isPinned = true; ElMessage.success('已设为推荐') } else { ElMessage.error(res.message || '操作失败') }
}

const handleCancelRecommend = async (row: any) => {
  const res: any = await cancelRecommendPlaylist(row.playlistId)
  if (res.code === 0) { row.isPinned = false; ElMessage.success('已取消推荐') } else { ElMessage.error(res.message || '操作失败') }
}

// 读取歌单歌曲
const openViewSongs = async (row: any) => {
  currentPlaylist.value = { id: row.playlistId, title: row.title }
  songPage.value = 1
  songSize.value = 10
  viewVisible.value = true
  await fetchSongs()
}

const fetchSongs = async () => {
  if (!currentPlaylist.value) return
  songLoading.value = true
  try {
    const res: any = await getSongsOfPlaylist({ playlistId: currentPlaylist.value.id, pageNum: songPage.value, pageSize: songSize.value })
    if (res?.code === 0 && res?.data) {
      songTotal.value = Number(res.data.total || 0)
      songList.value = (res.data.items || []).map((s:any)=>({
        songId: s.songId,
        songName: s.songName,
        artistName: s.artistName,
        album: s.album,
        coverUrl: s.coverUrl
      }))
    } else {
      songTotal.value = 0
      songList.value = []
    }
  } finally {
    songLoading.value = false
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="p-4">
    <el-form :inline="true" :model="form" class="mb-3">
      <el-form-item label="歌单：">
        <el-input v-model="form.title" placeholder="请输入歌单" clearable style="width: 220px" @keyup.enter.native="fetchData" />
      </el-form-item>
      <el-form-item label="风格：">
        <el-input v-model="form.style" placeholder="请选择风格" clearable style="width: 180px" @keyup.enter.native="fetchData" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="fetchData">搜索</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="dataList" v-loading="loading" border>
      <el-table-column label="封面" width="90" align="center">
        <template #default="{ row }">
          <el-image :src="row.coverUrl" style="width:56px;height:56px;border-radius:6px" fit="cover" />
        </template>
      </el-table-column>
      <el-table-column label="歌单" prop="title" min-width="200" />
      <el-table-column label="推荐状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isPinned" type="success" effect="dark">已推荐</el-tag>
          <el-tag v-else type="info">未推荐</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="风格" prop="style" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.style" effect="dark">{{ row.style }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="简介" prop="introduction" min-width="280" show-overflow-tooltip />
      <el-table-column label="操作" width="320" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openViewSongs(row)">查看歌曲</el-button>
          <el-button link type="primary" :disabled="row.isPinned" @click="handleRecommend(row)">设为推荐</el-button>
          <el-button link type="danger" :disabled="!row.isPinned" @click="handleCancelRecommend(row)">取消推荐</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="mt-4 flex justify-end">
      <el-pagination background layout="total, prev, pager, next" :current-page="pagination.pageNum" :page-size="pagination.pageSize" :total="pagination.total" @current-change="(p:number)=>{pagination.pageNum=p;fetchData()}" />
    </div>

    <!-- 查看歌曲（只读） -->
    <el-dialog v-model="viewVisible" :title="currentPlaylist ? `歌单《${currentPlaylist.title}》的歌曲` : '歌单歌曲'" width="880px">
      <el-table :data="songList" v-loading="songLoading" height="420px">
        <el-table-column label="封面" width="90" align="center">
          <template #default="{ row }">
            <el-image :src="row.coverUrl" style="width:60px;height:60px;border-radius:6px" fit="cover" />
          </template>
        </el-table-column>
        <el-table-column prop="songName" label="歌曲名" min-width="200" />
        <el-table-column prop="artistName" label="歌手" width="160" />
        <el-table-column prop="album" label="专辑" min-width="180" />
      </el-table>
      <div class="mt-3 flex justify-end">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="songTotal"
          v-model:current-page="songPage"
          v-model:page-size="songSize"
          @current-change="fetchSongs"
        />
      </div>
      <template #footer>
        <el-button @click="viewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
</style>


