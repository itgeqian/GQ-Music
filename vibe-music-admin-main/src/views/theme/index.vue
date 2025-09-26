```38:75:vibe-music-admin-main/src/views/theme/index.vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getThemeList, addTheme, updateThemeStatus, deleteTheme, updateThemeMeta } from '@/api/system'
import { ElMessage, ElMessageBox, ElInput, ElButton, ElSwitch, ElPopconfirm, ElTag } from 'element-plus'

type ThemeRow = {
  themeId: number
  name: string
  url1080: string
  url1440?: string
  thumbUrl?: string
  status: number
  sort: number
  needVip?: number
}

const loading = ref(false)
const list = ref<ThemeRow[]>([])
const editId = ref<number | null>(null)
const editName = ref('')
const editSort = ref<number | null>(null)
const editVip = ref<boolean>(false)
const uploading = ref(false)
const draggingIndex = ref<number | null>(null)

async function fetchList() {
  loading.value = true
  try {
    const res: any = await getThemeList()
    if (res.code === 0) {
      // 按 sort 倒序、id 倒序，保证新主题靠前
      const rows = (res.data || []) as ThemeRow[]
      list.value = rows.sort((a, b) => (b.sort ?? 0) - (a.sort ?? 0) || (b.themeId - a.themeId))
    } else {
      ElMessage.error(res.message || '获取主题失败')
    }
  } finally {
    loading.value = false
  }
}

function openEditor(row: ThemeRow) {
  editId.value = row.themeId
  editName.value = row.name || ''
  editSort.value = row.sort ?? 0
  editVip.value = (row.needVip ?? 0) === 1
}

function cancelEditor() {
  editId.value = null
  editName.value = ''
  editSort.value = null
  editVip.value = null
}

async function saveEditor() {
  if (editId.value == null) return
  try {
    await updateThemeMeta(editId.value, {
      name: editName.value?.trim(),
      sort: Number(editSort.value ?? 0),
      needVip: editVip.value ? 1 : 0
    } as any)
    ElMessage.success('已保存')
    cancelEditor()
    fetchList()
  } catch {
    ElMessage.error('保存失败')
  }
}

async function uploadTheme(presetName?: string) {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*,video/*'
  input.onchange = async () => {
    const file = input.files && input.files[0]
    if (!file) return
    uploading.value = true
    try {
      const up: any = await addTheme(file, presetName || '')
      if (up.code === 0) {
        ElMessage.success('上传成功')
        fetchList()
      } else {
        ElMessage.error(up.message || '上传失败')
      }
    } finally {
      uploading.value = false
    }
  }
  input.click()
}

async function createTheme() {
  try {
    const { value } = await ElMessageBox.prompt('请输入主题名称（可选）', '上传主题', {
      confirmButtonText: '选择图片',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：夏日蓝调',
    })
    await uploadTheme(value as string)
  } catch { /* 用户取消 */ }
}

async function toggleStatus(row: ThemeRow, val: boolean) {
  try {
    const res: any = await updateThemeStatus(row.themeId, val ? 1 : 0)
    if (res.code === 0) {
      ElMessage.success(val ? '已上架' : '已下架')
      row.status = val ? 1 : 0
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch {
    ElMessage.error('操作失败')
  }
}

async function removeTheme(id: number) {
  try {
    const res: any = await deleteTheme(id)
    if (res.code === 0) {
      ElMessage.success('已删除')
      fetchList()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch {
    ElMessage.error('删除失败')
  }
}

function maxSort(): number {
  if (!list.value.length) return 0
  return Math.max(...list.value.map(i => Number(i.sort || 0)))
}

// 置顶功能已移除

// 拖拽排序：拖完后重新计算唯一排序（越靠左越大）
function onDragStart(idx: number) {
  draggingIndex.value = idx
}
function onDragOver(e: DragEvent) {
  e.preventDefault()
}
async function onDrop(idx: number) {
  if (draggingIndex.value === null || draggingIndex.value === idx) return
  const arr = [...list.value]
  const [moved] = arr.splice(draggingIndex.value, 1)
  arr.splice(idx, 0, moved)
  list.value = arr
  draggingIndex.value = null
  await persistSort()
}

async function persistSort() {
  const n = list.value.length
  const tasks = list.value.map((it, i) => updateThemeMeta(it.themeId, { sort: n - i } as any))
  try {
    await Promise.all(tasks)
    // 不再二次请求，直接本地更新 sort
    list.value = list.value.map((it, i) => ({ ...it, sort: n - i }))
    ElMessage.success('排序已保存')
  } catch {
    ElMessage.error('保存排序失败')
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-4">
      <div class="text-lg font-semibold">主题管理</div>
      <div class="space-x-2">
        <el-button size="small" @click="fetchList" :loading="loading">刷新</el-button>
        <el-button size="small" type="primary" :loading="uploading" @click="createTheme">上传主题</el-button>
      </div>
    </div>

    <div class="text-xs text-gray-400 mb-2">提示：直接拖动卡片可调整顺序（越靠前，排序越大）。</div>
    <div class="grid xl:grid-cols-5 lg:grid-cols-4 md:grid-cols-3 sm:grid-cols-2 gap-5">
      <div v-for="(t,idx) in list" :key="t.themeId" class="rounded-lg overflow-hidden bg-white/5 dark:bg-white/5/10 border border-white/10 group" draggable="true" @dragstart="() => onDragStart(idx)" @dragover="onDragOver" @drop="() => onDrop(idx)">
        <div class="relative">
          <template v-if="(t as any).videoUrl">
            <div class="relative w-full h-36 overflow-hidden">
              <img v-if="(t as any).posterUrl" :src="(t as any).posterUrl" class="w-full h-full object-cover" />
              <video v-else :src="(t as any).videoUrl" autoplay muted loop playsinline class="w-full h-full object-cover"></video>
              <video v-if="(t as any).posterUrl" :src="(t as any).videoUrl" muted loop playsinline class="absolute inset-0 w-full h-full object-cover opacity-0 group-hover:opacity-100 transition-opacity"></video>
            </div>
          </template>
          <template v-else>
            <img :src="t.thumbUrl || t.url1440 || t.url1080" class="w-full h-36 object-cover" />
          </template>
          <el-tag v-if="t.needVip" size="small" effect="dark" class="absolute top-2 left-2">需会员</el-tag>
          <el-tag v-if="t.status!==1" size="small" type="info" effect="plain" class="absolute top-2 right-2">下架</el-tag>
        </div>

        <div class="p-3 space-y-2">
          <!-- 非编辑态（两行布局：第一行仅标题，第二行为元信息+操作） -->
          <div v-if="editId!==t.themeId" class="space-y-2">
            <div class="font-medium truncate" :title="t.name || ('主题#'+t.themeId)">{{ t.name || ('主题#'+t.themeId) }}</div>
            <div class="flex items-center justify-between">
              <div class="text-xs text-gray-400 whitespace-nowrap">ID: {{ t.themeId }} · 排序: {{ t.sort ?? 0 }}</div>
              <div class="flex items-center gap-2 flex-shrink-0">
                <el-switch :model-value="t.status===1" @change="val=>toggleStatus(t, Boolean(val))" />
                <el-button size="small" text type="primary" @click="openEditor(t)">编辑</el-button>
                <el-popconfirm title="确认删除该主题？" @confirm="() => removeTheme(t.themeId)">
                  <template #reference>
                    <el-button size="small" text type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </div>
            </div>
          </div>

          <!-- 编辑态 -->
          <div v-else class="space-y-2">
            <el-input v-model="editName" size="small" placeholder="主题名称" />
            <div class="flex items-center gap-2">
              <el-input v-model.number="editSort" size="small" placeholder="排序(数字越大越靠前)" />
              <div class="flex items-center gap-1 text-xs text-gray-400">
                <span>需会员</span>
                <el-switch v-model="editVip" />
              </div>
            </div>
            <div class="flex items-center justify-end gap-2">
              <el-button size="small" @click="cancelEditor">取消</el-button>
              <el-button size="small" type="primary" @click="saveEditor">保存</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!list.length && !loading" class="text-center text-gray-400 py-12">暂无主题</div>
  </div>
</template>

<style scoped>
/* 轻量化美化 */
.dark .bg-white\/5 { background-color: rgba(255,255,255,.04); }
</style>
