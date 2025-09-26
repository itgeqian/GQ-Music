<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import coverImg from '@/assets/cover.png'
import 'cropperjs/dist/cropper.css'
import Cropper from 'cropperjs'
import {
  // 我的歌单
  getMyPlaylists,
  createMyPlaylist,
  updateMyPlaylist,
  deleteMyPlaylist,
  updateMyPlaylistCover,
  addSongToMyPlaylist,
  // 评论
  getPlaylistDetail,
  deleteComment,
  // 歌曲列表（用于添加歌曲）
  getAllSongs
} from '@/api/system'

// 简单的字节单位格式化
const formatBytes = (bytes: number): string => {
  if (!bytes || bytes <= 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  const val = bytes / Math.pow(k, i)
  return `${val.toFixed(2)} ${sizes[i]}`
}

type PlaylistRow = {
  playlistId: number
  title: string
  coverUrl?: string
  style?: string
  introduction?: string
}

type SongRow = {
  songId: number
  songName: string
  artistName: string
  album: string
  coverUrl?: string
}

const router = useRouter()

/* ========== 搜索/筛选 ========== */
const form = reactive<{ title: string; style: string | undefined }>({
  title: '',
  style: undefined
})

/* ========== 列表与分页 ========== */
const loading = ref(false)
const list = ref<PlaylistRow[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

/* 与管理端一致的风格选项 */
const styleOptions = [
  '节奏布鲁斯', '欧美流行', '华语流行', '粤语流行', '国风流行', '韩语流行', '日本流行',
  '嘻哈说唱', '非洲节拍', '原声带', '轻音乐', '摇滚', '朋克', '电子', '国风', '乡村', '古典'
]

/* ========== 新建/编辑对话框 ========== */
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogFormRef = ref<FormInstance>()
const dialogForm = reactive<{ playlistId?: number; title: string; style?: string; introduction?: string }>({
  title: '',
  style: undefined,
  introduction: ''
})
const dialogRules: FormRules = {
  title: [{ required: true, message: '请输入歌单名称', trigger: 'blur' }]
}

const resetDialog = () => {
  dialogForm.playlistId = undefined
  dialogForm.title = ''
  dialogForm.style = undefined
  dialogForm.introduction = ''
}

const openCreate = () => {
  resetDialog()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

const openEdit = (row: PlaylistRow) => {
  dialogMode.value = 'edit'
  dialogForm.playlistId = row.playlistId
  dialogForm.title = row.title
  dialogForm.style = row.style
  dialogForm.introduction = row.introduction
  dialogVisible.value = true
}

const submitDialog = async () => {
  if (!dialogFormRef.value) return
  await dialogFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (dialogMode.value === 'create') {
        const res: any = await createMyPlaylist({
          title: dialogForm.title,
          style: dialogForm.style,
          introduction: dialogForm.introduction
        })
        if (res.code === 0) {
          ElMessage.success('新建成功')
          dialogVisible.value = false
          fetchList(true)
        } else ElMessage.error(res.message || '新建失败')
      } else {
        const res: any = await updateMyPlaylist({
          playlistId: dialogForm.playlistId as number,
          title: dialogForm.title,
          style: dialogForm.style,
          introduction: dialogForm.introduction
        })
        if (res.code === 0) {
          ElMessage.success('保存成功')
          dialogVisible.value = false
          fetchList()
        } else ElMessage.error(res.message || '保存失败')
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '操作失败')
    }
  })
}

const handleDelete = async (row: PlaylistRow) => {
  await ElMessageBox.confirm(`确认删除歌单「${row.title}」？此操作不可撤销`, '提示', { type: 'warning' })
  const res: any = await deleteMyPlaylist(row.playlistId)
  if (res.code === 0) {
    ElMessage.success('删除成功')
    fetchList()
  } else {
    ElMessage.error(res.message || '删除失败')
  }
}

const goDetail = (row: PlaylistRow) => {
  router.push(`/playlist/${row.playlistId}`)
}

/* ========== 列表查询与分页 ========== */
const handleSearch = () => fetchList(true)
const handleReset = () => { form.title = ''; form.style = undefined; fetchList(true) }
const handleCurrentChange = (p: number) => { pageNum.value = p; fetchList() }
const handleSizeChange = (s: number) => { pageSize.value = s; fetchList(true) }

const fetchList = async (reset = false) => {
  if (reset) pageNum.value = 1
  loading.value = true
  try {
    const res: any = await getMyPlaylists({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      title: form.title || undefined,
      style: form.style || undefined
    })
    if (res.code === 0 && res.data) {
      list.value = (res.data.items || []) as PlaylistRow[]
      total.value = Number(res.data.total || 0)
    } else {
      ElMessage.error(res.message || '获取数据失败')
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => fetchList(true))

/* =========================================================
   ≈≈≈ 迁移管理端功能 1：裁剪上传歌单封面（本页内置 Cropper） ≈≈≈
   ========================================================= */
const cropDialogVisible = ref(false)
const cropPreview = ref<string>('')       // 右侧预览
const cropImgEl = ref<HTMLImageElement>() // 左侧被裁剪的 <img>
let cropper: Cropper | null = null
const cropTarget = ref<PlaylistRow | null>(null)
const imageSizeText = ref('')
const imageResolutionText = ref('')
// 右键菜单
const showCtxMenu = ref(false)
const ctxX = ref(0)
const ctxY = ref(0)
let scaleXVal = 1
let scaleYVal = 1

const openCropper = async (row: PlaylistRow) => {
  cropTarget.value = row
  // 先让用户选择图片文件
  await pickImageFile().then(async (file) => {
    if (!file) return
    // 读本地预览
    const reader = new FileReader()
    reader.onload = async (e) => {
      cropPreview.value = String(e.target?.result || '')
      cropDialogVisible.value = true
      // 文件大小
      imageSizeText.value = formatBytes(file.size)
      // 读取原图分辨率
      const _img = new Image()
      _img.onload = () => {
        imageResolutionText.value = `${_img.naturalWidth} x ${_img.naturalHeight}`
      }
      _img.src = cropPreview.value
      await nextTick()
      if (cropper && (cropper as any).destroy) {
        (cropper as any).destroy()
      }
            // 构造
        cropper = new Cropper(cropImgEl.value as HTMLImageElement, {
          aspectRatio: 1,
          viewMode: 1,
          background: false,
          autoCropArea: 1,
          movable: true,
          zoomable: true,
          scalable: true,
          responsive: true,
          crop() {
            const canvas = (cropper as any).getCroppedCanvas({ width: 800, height: 800 })
            cropPreview.value = canvas.toDataURL('image/jpeg', 0.9)
          }
        } as any)
    }
    reader.readAsDataURL(file)
  })
}

// 裁剪区域右键菜单
const onCropContextMenu = (e: MouseEvent) => {
  e.preventDefault()
  ctxX.value = e.clientX
  ctxY.value = e.clientY
  showCtxMenu.value = true
  const hide = () => { showCtxMenu.value = false; document.removeEventListener('click', hide) }
  document.addEventListener('click', hide)
}

const resetCrop = () => (cropper as any)?.reset?.()
const rotateLeft = () => (cropper as any)?.rotate?.(-45)
const rotateRight = () => (cropper as any)?.rotate?.(45)
const zoomIn = () => (cropper as any)?.zoom?.(0.1)
const zoomOut = () => (cropper as any)?.zoom?.(-0.1)
const moveBy = (dx: number, dy: number) => (cropper as any)?.move?.(dx, dy)
const flipH = () => { scaleXVal = scaleXVal === 1 ? -1 : 1; (cropper as any)?.scaleX?.(scaleXVal) }
const flipV = () => { scaleYVal = scaleYVal === 1 ? -1 : 1; (cropper as any)?.scaleY?.(scaleYVal) }

function pickImageFile(): Promise<File | null> {
  return new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.onchange = () => resolve(input.files && input.files[0] ? input.files[0] : null)
    input.click()
  })
}

const submitCropper = async () => {
  if (!cropper || !cropTarget.value) return
  (cropper as any).getCroppedCanvas({ width: 800, height: 800 }).toBlob(async (blob: Blob | null) => {
    if (!blob) return
    try {
      const file = new File([blob], 'cover.jpg', { type: 'image/jpeg' })
      const res: any = await updateMyPlaylistCover(cropTarget.value!.playlistId, file)
      if (res.code === 0) {
        ElMessage.success('封面上传成功')
        cropDialogVisible.value = false
        fetchList()
      } else {
        ElMessage.error(res.message || '封面上传失败')
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '封面上传失败')
    }
  }, 'image/jpeg', 0.9)
}

/* =========================================================
   ≈≈≈ 迁移管理端功能 2：添加歌曲到歌单（带筛选、分页、多选） ≈≈≈
   ========================================================= */
const addSongDialogVisible = ref(false)
const songLoading = ref(false)
const songQuery = reactive<{ pageNum: number; pageSize: number; songName?: string; artistName?: string; album?: string }>({
  pageNum: 1,
  pageSize: 10
})
const songTotal = ref(0)
const songList = ref<SongRow[]>([])
const songSelected = ref<Set<number>>(new Set())
const addSongTargetPlaylistId = ref<number | null>(null)
const activeAddTab = ref<'pick' | 'bound'>('pick')
const boundLoading = ref(false)
const boundList = ref<SongRow[]>([])
const boundSelected = ref<Set<number>>(new Set())

const openAddSong = (row: PlaylistRow) => {
  addSongTargetPlaylistId.value = row.playlistId
  songQuery.pageNum = 1
  songQuery.pageSize = 10
  songQuery.songName = undefined
  songQuery.artistName = undefined
  songQuery.album = undefined
  songSelected.value = new Set()
  boundSelected.value = new Set()
  activeAddTab.value = 'pick'
  addSongDialogVisible.value = true
  fetchSongList()
  fetchBoundList()
}

const fetchSongList = async () => {
  songLoading.value = true
  try {
    const res: any = await getAllSongs({
      pageNum: songQuery.pageNum,
      pageSize: songQuery.pageSize,
      songName: songQuery.songName,
      artistName: songQuery.artistName,
      album: songQuery.album
    })
    if (res.code === 0 && res.data) {
      songList.value = (res.data.items || []) as SongRow[]
      songTotal.value = Number(res.data.total || 0)
    } else {
      songList.value = []
      songTotal.value = 0
      ElMessage.error(res.message || '获取歌曲失败')
    }
  } finally {
    songLoading.value = false
  }
}

const onSongSelectionChange = (selection: SongRow[]) => {
  songSelected.value = new Set(selection.map(s => s.songId))
}

// 获取已绑定歌曲（从歌单详情里拿）
const fetchBoundList = async () => {
  if (!addSongTargetPlaylistId.value) return
  boundLoading.value = true
  try {
    const res: any = await getPlaylistDetail(addSongTargetPlaylistId.value)
    if (res.code === 0 && res.data && Array.isArray(res.data.songs)) {
      boundList.value = res.data.songs.map((s: any) => ({
        songId: s.songId,
        songName: s.songName,
        artistName: s.artistName,
        album: s.album,
        coverUrl: s.coverUrl
      }))
    } else {
      boundList.value = []
    }
  } finally {
    boundLoading.value = false
  }
}

const onBoundSelectionChange = (selection: SongRow[]) => {
  boundSelected.value = new Set(selection.map(s => s.songId))
}

const confirmAddSongs = async () => {
  if (!addSongTargetPlaylistId.value || songSelected.value.size === 0) {
    ElMessage.warning('请先选择要添加的歌曲')
    return
  }
  let ok = 0
  for (const sid of songSelected.value) {
    const res: any = await addSongToMyPlaylist({ playlistId: addSongTargetPlaylistId.value, songId: sid })
    if (res.code === 0) ok++
  }
  if (ok > 0) {
    ElMessage.success(`已添加 ${ok} 首歌曲`)
    // 刷新“已绑定”列表并切换到已绑定
    fetchBoundList()
    activeAddTab.value = 'bound'
  } else {
    ElMessage.error('添加失败')
  }
}

// 从已绑定移除
const removeBoundSongs = async () => {
  if (!addSongTargetPlaylistId.value || boundSelected.value.size === 0) {
    ElMessage.warning('请选择要移除的歌曲')
    return
  }
  let ok = 0
  for (const sid of boundSelected.value) {
    // 这里后端没有批量接口，沿用服务侧 removeSongFromMyPlaylist 单个调用
    const res: any = await (await import('@/api/system')).removeSongFromMyPlaylist({ playlistId: addSongTargetPlaylistId.value, songId: sid })
    if (res.code === 0) ok++
  }
  if (ok > 0) {
    ElMessage.success(`已移除 ${ok} 首歌曲`)
    fetchBoundList()
  } else {
    ElMessage.error('操作失败')
  }
}

/* ========== 评论管理（与之前一致） ========== */
type PlaylistComment = { commentId: number; username: string; content: string; createTime: string; likeCount: number; imgPath?: string; pCommentId?: number; pcommentId?: number; replyNickName?: string; children?: any[]; _depth?: number; _displayContent?: string }
const commentDialogVisible = ref(false)
const commentLoading = ref(false)
const commentPlaylistId = ref<number | null>(null)
const comments = ref<PlaylistComment[]>([])
const showImageDialog = ref(false)
const previewUrl = ref('')

const openComments = async (row: PlaylistRow) => {
  commentPlaylistId.value = row.playlistId
  commentDialogVisible.value = true
  fetchComments()
}

function buildTree(list: any[]) {
  if (!Array.isArray(list)) return []
  const byId: Record<number, any> = {}
  const roots: any[] = []
  list.forEach(it => { (it as any).pCommentId = (it as any).pCommentId ?? (it as any).pcommentId ?? 0; byId[it.commentId] = { ...it, children: Array.isArray((it as any).children) ? (it as any).children : [] } })
  Object.values(byId).forEach((it:any)=>{ const pid = Number((it as any).pCommentId||0); if (pid>0 && byId[pid]) byId[pid].children.push(it); else if (pid===0) roots.push(it) })
  return roots
}

function flatten(list:any[], depth=0, out:any[]=[]){
  list.forEach(it=>{ out.push({ ...it, _depth: depth, _displayContent: (it.replyNickName?`回复 @${it.replyNickName} `:"") + (it.content||"") }); if (Array.isArray(it.children) && it.children.length) flatten(it.children, depth+1, out) })
  return out
}

const fetchComments = async () => {
  if (!commentPlaylistId.value) return
  commentLoading.value = true
  try {
    const res: any = await getPlaylistDetail(commentPlaylistId.value)
    if (res.code === 0 && res.data) {
      const raw = (res.data.comments || []) as PlaylistComment[]
      const hasChildren = raw.some((it:any)=>Array.isArray(it.children) && it.children.length)
      const tree = hasChildren ? raw : buildTree(raw as any[])
      comments.value = flatten(tree) as any
    }
    else ElMessage.error(res.message || '获取评论失败')
  } finally {
    commentLoading.value = false
  }
}

const onDeleteComment = async (row: PlaylistComment) => {
  await ElMessageBox.confirm('确定删除该评论？', '提示', { type: 'warning' })
  const res: any = await deleteComment(row.commentId)
  if (res.code === 0) { ElMessage.success('删除成功'); fetchComments() }
  else ElMessage.error(res.message || '删除失败')
}

const openImage = (row: PlaylistComment) => {
  if (!row.imgPath) return
  previewUrl.value = row.imgPath
  showImageDialog.value = true
}
</script>

<template>
  <div class="p-4 w-full h-full">
    <!-- 搜索区 -->
    <el-form :inline="true" :model="form" class="mb-3">
      <el-form-item label="歌单：">
        <el-input v-model="form.title" placeholder="请输入歌单" clearable @keyup.enter="handleSearch" style="width: 220px" />
      </el-form-item>
      <el-form-item label="风格：">
        <el-select v-model="form.style" placeholder="请选择风格" clearable style="width: 180px">
          <el-option v-for="it in styleOptions" :key="it" :label="it" :value="it" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
      <el-form-item class="!ml-auto">
        <el-button type="success" @click="openCreate">新增歌单</el-button>
      </el-form-item>
    </el-form>

    <!-- 表格区 -->
    <el-table :data="list" v-loading="loading" table-layout="auto" header-cell-class-name="table-header" style="background: transparent">
      <el-table-column label="封面" width="90" align="center">
        <template #default="{ row }">
          <el-image :src="row.coverUrl || coverImg" fit="cover" style="width: 56px; height: 56px; border-radius: 6px" />
        </template>
      </el-table-column>
      <el-table-column label="歌单" prop="title" min-width="180">
        <template #default="{ row }">
          <span class="link" @click="goDetail(row)">{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column label="风格" prop="style" width="120" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.style" type="success" effect="dark">{{ row.style }}</el-tag>
          <span v-else class="text-inactive">-</span>
        </template>
      </el-table-column>
      <el-table-column label="简介" prop="introduction" min-width="280" show-overflow-tooltip />
      <el-table-column label="操作" width="320" align="center" fixed="right">
        <template #default="{ row }">
          <div class="inline-flex items-center gap-2 align-middle">
            <el-button type="primary" link @click="goDetail(row)">查看</el-button>
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-popconfirm :title="`是否确认删除歌单：${row.title}？`" @confirm="handleDelete(row)">
              <template #reference><el-button type="danger" link>删除</el-button></template>
            </el-popconfirm>

            <el-dropdown>
              <span class="text-[hsl(var(--el-color-primary))] cursor-pointer ml-2 align-middle select-none">更多</span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openCropper(row)">上传封面</el-dropdown-item>
                  <el-dropdown-item @click="openAddSong(row)">添加歌曲</el-dropdown-item>
                  <el-dropdown-item @click="openComments(row)">评论管理</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 30, 50]"
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 新建/编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增歌单' : '编辑歌单'" width="520px">
      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="64px">
        <el-form-item label="歌单" prop="title">
          <el-input v-model="dialogForm.title" placeholder="请输入歌单名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="风格">
          <el-select v-model="dialogForm.style" placeholder="请选择风格" clearable>
            <el-option v-for="it in styleOptions" :key="it" :label="it" :value="it" />
          </el-select>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="dialogForm.introduction" type="textarea" placeholder="请输入简介" :autosize="{ minRows: 3, maxRows: 6 }" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDialog">确定</el-button>
      </template>
    </el-dialog>

    <!-- 裁剪上传封面（左裁剪右预览） -->
    <el-dialog v-model="cropDialogVisible" title="裁剪、上传歌单封面" width="920px">
      <div class="flex gap-6">
        <div class="flex-1">
          <div @contextmenu="onCropContextMenu">
            <img ref="cropImgEl" :src="cropPreview" alt="crop" style="max-width: 100%; height: 420px; object-fit: contain" />
          </div>
          <div class="text-xs text-inactive mt-2">温馨提示：右键上方裁剪区可开启功能菜单（拖动/缩放/旋转）</div>
        </div>
        <div style="width:320px">
          <div style="width:300px;height:300px" class="rounded-full overflow-hidden mx-auto border">
            <img :src="cropPreview" alt="preview" style="width: 100%; height: 100%; object-fit: cover" />
          </div>
          <div class="mt-3 text-sm text-inactive text-center">
            图像大小：{{ imageResolutionText }}<br />
            文件大小：{{ imageSizeText }}
          </div>
        </div>
      </div>
      <!-- 右键菜单 -->
      <div v-if="showCtxMenu" class="fixed z-50 bg-white dark:bg-neutral-800 border rounded-md shadow-lg p-2 space-x-2 flex flex-wrap gap-2"
           :style="{ left: ctxX + 'px', top: ctxY + 'px' }">
        <el-button size="small" @click="resetCrop">重置</el-button>
        <el-button size="small" @click="rotateLeft">逆时针</el-button>
        <el-button size="small" @click="rotateRight">顺时针</el-button>
        <el-button size="small" @click="zoomIn">放大</el-button>
        <el-button size="small" @click="zoomOut">缩小</el-button>
        <el-button size="small" @click="() => moveBy(0, -10)">上移</el-button>
        <el-button size="small" @click="() => moveBy(0, 10)">下移</el-button>
        <el-button size="small" @click="() => moveBy(-10, 0)">左移</el-button>
        <el-button size="small" @click="() => moveBy(10, 0)">右移</el-button>
        <el-button size="small" @click="flipH">水平翻转</el-button>
        <el-button size="small" @click="flipV">垂直翻转</el-button>
      </div>
      <template #footer>
        <el-button @click="cropDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCropper">确定</el-button>
      </template>
    </el-dialog>

    <!-- 添加歌曲到歌单（带筛选/分页/多选） -->
    <el-dialog v-model="addSongDialogVisible" title="添加歌曲到歌单" width="980px">
      <el-tabs v-model="activeAddTab">
        <el-tab-pane label="选择添加" name="pick">
          <div class="flex gap-3 mb-3">
        <el-input v-model="songQuery.songName" placeholder="输入歌曲名搜索" clearable style="width: 220px" @keyup.enter="fetchSongList" />
        <el-input v-model="songQuery.artistName" placeholder="输入歌手名搜索" clearable style="width: 220px" @keyup.enter="fetchSongList" />
        <el-input v-model="songQuery.album" placeholder="输入专辑搜索" clearable style="width: 220px" @keyup.enter="fetchSongList" />
        <el-button type="primary" :loading="songLoading" @click="fetchSongList">搜索</el-button>
        <el-button @click="songQuery.songName='';songQuery.artistName='';songQuery.album='';songQuery.pageNum=1;fetchSongList()">重置</el-button>
          </div>
          <el-table :data="songList" v-loading="songLoading" border height="520px" @selection-change="onSongSelectionChange">
            <el-table-column type="selection" width="48" :selectable="() => true" />
            <el-table-column label="封面" width="70" align="center">
              <template #default="{ row }"><el-image :src="row.coverUrl || coverImg" fit="cover" style="width:44px;height:44px;border-radius:6px" /></template>
            </el-table-column>
            <el-table-column label="歌曲名" prop="songName" min-width="200" />
            <el-table-column label="歌手" prop="artistName" width="160" />
            <el-table-column label="专辑" prop="album" min-width="180" />
          </el-table>
          <div class="mt-3 flex justify-between items-center">
            <div class="text-sm">已选 {{ songSelected.size }} 首</div>
            <el-pagination background layout="total, prev, pager, next" :current-page="songQuery.pageNum" :page-size="songQuery.pageSize" :total="songTotal" @current-change="(p:number)=>{songQuery.pageNum=p;fetchSongList()}" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="已绑定" name="bound">
          <div class="flex items-center justify-between mb-3">
            <div class="text-sm text-inactive">已绑定 {{ boundList.length }} 首</div>
            <el-button type="danger" @click="removeBoundSongs">移除选中</el-button>
          </div>
          <el-table :data="boundList" v-loading="boundLoading" border height="520px" @selection-change="onBoundSelectionChange">
            <el-table-column type="selection" width="48" />
            <el-table-column label="封面" width="70" align="center">
              <template #default="{ row }"><el-image :src="row.coverUrl || coverImg" fit="cover" style="width:44px;height:44px;border-radius:6px" /></template>
            </el-table-column>
            <el-table-column label="歌曲名" prop="songName" min-width="200" />
            <el-table-column label="歌手" prop="artistName" width="160" />
            <el-table-column label="专辑" prop="album" min-width="180" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="addSongDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="confirmAddSongs">确认添加</el-button>
      </template>
    </el-dialog>

    <!-- 评论管理 -->
    <el-dialog v-model="commentDialogVisible" title="评论管理" width="720px" append-to-body top="8vh">
      <el-table :data="comments" v-loading="commentLoading" border :max-height="'50vh'">
        <el-table-column label="评论ID" prop="commentId" width="100" />
        <el-table-column label="用户" prop="username" width="140" />
        <el-table-column label="内容" min-width="280">
          <template #default="{ row }">
            <span :style="{ marginLeft: (row._depth||0)*16 + 'px' }">{{ row._displayContent || row.content }}</span>
            <el-tag v-if="row.imgPath" type="warning" size="small" effect="plain" class="ml-2 cursor-pointer" @click="openImage(row)">图片</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" prop="createTime" width="160" />
        <el-table-column label="点赞" prop="likeCount" width="90" align="center" />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-popconfirm title="确定删除该评论？" @confirm="onDeleteComment(row)">
              <template #reference><el-button type="danger" link>删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="commentDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="fetchComments">刷新</el-button>
      </template>
    </el-dialog>

    <!-- 图片预览 -->
    <el-dialog v-model="showImageDialog" title="图片预览" width="600px" append-to-body>
      <div class="w-full flex justify-center items-center">
        <img v-if="showImageDialog" :src="previewUrl" style="max-width:100%;max-height:70vh;object-fit:contain;" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.table-header { background: var(--el-fill-color-light); color: var(--el-text-color-primary); }
.link { color: var(--el-color-primary); cursor: pointer; }
.text-inactive { color: rgba(148, 163, 184, 0.8); }
</style>
