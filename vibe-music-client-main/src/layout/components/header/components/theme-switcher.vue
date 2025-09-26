<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { themeStore } from '@/stores/modules/theme'
import { getThemeList, getMyTheme, setMyThemeById, uploadMyTheme, setMyCustomTheme, resetMyTheme } from '@/api/system'

const theme = themeStore()
const visible = ref(false)
const official = ref<Array<any>>([])
const loading = ref(false)
const filterType = ref<'image' | 'video'>('image')
const filteredOfficial = computed(() =>
  (official.value || []).filter((it: any) => (filterType.value === 'video' ? it.type === 1 : it.type !== 1))
)

async function openDialog() {
  try {
    const { UserStore } = await import('@/stores/modules/user')
    const u = UserStore()
    if (!u.isLoggedIn) {
      ElMessage.warning('请先登录')
      try { window.dispatchEvent(new CustomEvent('auth:show')) } catch {}
      return
    }
  } catch {}
  visible.value = true
  try {
    const [listRes, myRes]: any = await Promise.all([getThemeList(), getMyTheme()])
    if (listRes?.code === 0) official.value = (listRes.data as any[]) || []
    if (myRes?.code === 0 && myRes.data) {
      const ut: any = myRes.data
      // 仅在已登录时恢复用户主题；未登录不恢复，避免遮挡登录页
      const user = (await import('@/stores/modules/user')).UserStore()
      if (user.isLoggedIn) {
        if (ut.themeType === 'custom' && ut.imageUrl) theme.applyCustomTheme(ut.imageUrl)
        if (ut.themeType === 'official' && ut.themeId) theme.applyOfficialTheme(ut.themeId)
      }
    }
  } catch {}
}

async function chooseOfficial(item: any) {
  loading.value = true
  try {
    const res: any = await setMyThemeById(item.themeId)
    if (res.code === 0) {
      const url = item.type === 1 ? (item.videoUrl || '') : (item.url1440 || item.url1080 || item.thumbUrl || '')
      theme.applyOfficialTheme(item.themeId, url)
      ElMessage.success('已应用主题')
    } else ElMessage.error(res.message || '设置失败')
  } finally {
    loading.value = false
  }
}

async function uploadCustom() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*,video/*'
  input.onchange = async () => {
    const file = input.files && input.files[0]
    if (!file) return
    loading.value = true
    try {
      const up: any = await uploadMyTheme(file)
      if (up.code === 0 && typeof up.data === 'string') {
        const url = up.data as string
        const res: any = await setMyCustomTheme({ imageUrl: url })
        if (res.code === 0) {
          theme.applyCustomTheme(url)
          ElMessage.success('自定义壁纸已应用')
        } else ElMessage.error(res.message || '设置失败')
      } else ElMessage.error(up.message || '上传失败')
    } finally {
      loading.value = false
    }
  }
  input.click()
}

async function restoreDefault() {
  const res: any = await resetMyTheme()
  if (res.code === 0) {
    theme.applyOfficialTheme(0)
    theme.backgroundUrl = ''
    theme.videoUrl = ''
    ElMessage.success('已恢复默认')
  } else ElMessage.error(res.message || '操作失败')
}
</script>

<template>
  <button class="w-8 h-8 flex items-center justify-center rounded hover:bg-black/5 dark:hover:bg-white/10" @click="openDialog">
    <icon-ri:t-shirt-line class="text-lg" />
  </button>
  <el-dialog v-model="visible" title="更换主题" width="720px" append-to-body>
    <div class="space-y-3">
      <div class="flex justify-between items-center">
        <div class="text-sm text-inactive">官方主题</div>
        <div class="space-x-2">
          <el-button size="small" @click="uploadCustom" :loading="loading">上传自定义</el-button>
          <el-button size="small" @click="restoreDefault">恢复默认</el-button>
        </div>
      </div>
      <div class="flex items-center gap-3 text-sm">
        <span class="text-inactive">模糊度</span>
        <el-slider :min="0" :max="20" :step="1" v-model="theme.blurStrength" style="width: 240px" />
        <span class="w-8 text-center">{{ theme.blurStrength }}px</span>
      </div>
      <div class="flex items-center gap-3 text-sm">
        <span class="text-inactive">亮度</span>
        <el-slider :min="50" :max="150" :step="1" v-model="theme.brightness" style="width: 240px" />
        <span class="w-10 text-center">{{ theme.brightness }}%</span>
      </div>
      <div class="flex items-center gap-3 mt-1">
        <el-radio-group v-model="filterType" size="small">
          <el-radio-button label="image">静态壁纸</el-radio-button>
          <el-radio-button label="video">动态壁纸</el-radio-button>
        </el-radio-group>
      </div>
      <div class="grid grid-cols-4 gap-3 mt-2">
        <div v-for="item in filteredOfficial" :key="item.themeId" class="relative group cursor-pointer" @click="chooseOfficial(item)">
          <template v-if="item.type === 1">
            <!-- 优先显示海报，hover 时播放视频预览 -->
            <div class="relative w-full h-28 rounded-md border overflow-hidden">
              <img v-if="item.posterUrl" :src="item.posterUrl" class="w-full h-full object-cover" />
              <video v-else :src="item.videoUrl" autoplay muted loop playsinline class="w-full h-full object-cover"></video>
              <video v-if="item.posterUrl" :src="item.videoUrl" muted loop playsinline class="absolute inset-0 w-full h-full object-cover opacity-0 group-hover:opacity-100 transition-opacity"></video>
            </div>
            <span class="absolute top-1 left-1 px-1.5 py-0.5 text-[10px] rounded bg-black/60 text-white">视频</span>
          </template>
          <template v-else>
            <img :src="item.thumbUrl || item.url1080" class="w-full h-28 object-cover rounded-md border" />
          </template>
          <div class="absolute inset-0 rounded-md ring-2 ring-primary/0 group-hover:ring-primary/60 transition"></div>
          <div class="mt-1 text-xs text-center truncate" :title="item.name || '官方主题'">{{ item.name || '官方主题' }}</div>
        </div>
      </div>
    </div>
  </el-dialog>
  
</template>

<style scoped>
.text-inactive{ color: rgba(0,0,0,.45) }
:deep(.dark) .text-inactive{ color: rgba(255,255,255,.45) }
</style>


