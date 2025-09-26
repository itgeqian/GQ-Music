<script setup lang="ts">
import { Icon } from '@iconify/vue'
import Avatar from './components/avatar.vue'
const route = useRoute()
const router = useRouter()
const currentIcon = ref('material-symbols:wb-sunny-outline-rounded')
const theme = themeStore()
import { useDark, useToggle } from '@vueuse/core'
import { getHotKeywords, reportKeyword } from '@/api/system'
import ThemeSwitcher from '@/layout/components/header/components/theme-switcher.vue'

const searchText = ref('')
const searchType = ref('music') // 默认搜索音乐
const hotVisible = ref(false)
const hotList = ref<string[]>([])
let hotTimer: number | null = null
const hotLeft = computed(() => hotList.value.filter((_, i) => i % 2 === 0))
const hotRight = computed(() => hotList.value.filter((_, i) => i % 2 === 1))

// 搜索类型选项
const searchTypes = [
  { label: '音乐', value: 'music' },
  { label: '用户', value: 'users' }
]

const isDark = useDark({
  selector: 'html',
  attribute: 'class',
  valueDark: 'dark',
  valueLight: 'light',
})
const toggleDark = useToggle(isDark)

const toggleMode = () => {
  theme.setDark(!isDark.value)
  toggleDark()
}

// 初始化时根据 store 设置图标
watch(
  () => theme.isDark,
  (newValue) => {
    currentIcon.value = newValue
      ? 'mdi:weather-night'
      : 'material-symbols:wb-sunny-outline-rounded'
  },
  { immediate: true }
)

// 赋值到搜索框
watch(
  () => route.query,
  (newValue) => {
    if (newValue.query) {
      searchText.value = newValue.query as string
    }
  },
  { immediate: true }
)

// 热搜：聚焦后拉取，输入时隐藏，失焦稍后隐藏以便点击
async function openHot() {
  try {
    const res: any = await getHotKeywords(10)
    if (res?.code === 0) hotList.value = (res.data as string[]) || []
    hotVisible.value = true
  } catch {}
}
function onFocus() {
  openHot()
}
function onInput() {
  hotVisible.value = false
}
function onBlur() {
  // 延迟关闭，给点击热词的时间
  hotTimer && clearTimeout(hotTimer)
  hotTimer = window.setTimeout(() => (hotVisible.value = false), 150)
}
function useHot(keyword: string) {
  hotVisible.value = false
  handleEnter(keyword)
}

function handleEnter(val?: string) {
  const q = (val ?? searchText.value).toString()
  if (!q.trim()) return
  
  // 主动上报一次，确保计数即时+1
  reportKeyword(q).catch(() => {})
  
  // 根据搜索类型跳转到不同页面
  let target = ''
  if (searchType.value === 'users') {
    target = '/user/search?query=' + encodeURIComponent(q)
  } else {
    // 音乐搜索（歌曲/歌手/专辑）都使用library页面
    target = '/library?query=' + encodeURIComponent(q)
  }
  
  // 若已在相同页面且关键词相同，强制刷新一次（附带时间戳）
  const currentPath = route.path.split('?')[0]
  const targetPath = target.split('?')[0]
  if (currentPath === targetPath && (route.query.query as string) === q) {
    router.replace(target + '&t=' + Date.now())
  } else {
    router.push(target)
  }
}
</script>
<template>
  <header class="px-4 py-2 flex items-center bg-transparent">
    <button class="flex relative w-60" @click="router.push('/')">
      <img src="\logo.svg" alt="logo" class="w-10 h-10 ml-2" />
      <span class="ml-3 text-2xl font-bold flex justify-center items-center"
        >GQ Music</span
      >
    </button>
    <!-- 输入框和头像 -->
    <div class="flex items-center gap-3">
      <div class="relative mr-6 flex items-center">
        <!-- 搜索类型选择器 -->
        <el-select v-model="searchType" class="w-20 mr-2" size="small">
          <el-option
            v-for="type in searchTypes"
            :key="type.value"
            :label="type.label"
            :value="type.value"
          />
        </el-select>
        
        <div class="relative">
          <Icon
            icon="mdi:magnify"
            class="absolute left-2 top-1/2 transform -translate-y-1/2 text-gray-500 text-xl"
          />
          <input
            v-model="searchText"
            type="text"
            class="mt-0.5 w-64 text-sm pl-8 pr-2 py-2 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary transition-all duration-300 focus:w-80 search-bg"
            :placeholder="`搜索${searchTypes.find(t => t.value === searchType)?.label || '音乐'}...`"
            @focus="onFocus"
            @input="onInput"
            @blur="onBlur"
            @keyup.enter="handleEnter()"
          />
          <div v-show="hotVisible && hotList.length" class="absolute left-0 right-0 top-10 bg-white dark:bg-zinc-800 rounded-md shadow-lg z-50">
            <div class="px-3 py-2 text-xs text-gray-500">热搜</div>
            <div class="px-2 pb-2 grid grid-cols-2 gap-x-4">
              <ul>
                <li v-for="(k,i) in hotLeft" :key="'l-'+i"
                    class="flex items-center px-1 py-2 hover:bg-gray-100 dark:hover:bg-zinc-700 cursor-pointer text-sm rounded"
                    @mousedown.prevent @click="useHot(k)">
                  <span class="mr-2 inline-flex w-5 justify-center text-[10px] font-semibold"
                        :class="(i*2+1) <= 3 ? 'text-red-500' : 'text-gray-400'">{{ i*2+1 }}</span>
                  <span class="truncate">{{ k }}</span>
                </li>
              </ul>
              <ul>
                <li v-for="(k,i) in hotRight" :key="'r-'+i"
                    class="flex items-center px-1 py-2 hover:bg-gray-100 dark:hover:bg-zinc-700 cursor-pointer text-sm rounded"
                    @mousedown.prevent @click="useHot(k)">
                  <span class="mr-2 inline-flex w-5 justify-center text-[10px] font-semibold"
                        :class="(i*2+2) <= 3 ? 'text-red-500' : 'text-gray-400'">{{ i*2+2 }}</span>
                  <span class="truncate">{{ k }}</span>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
      <button @click="toggleMode">
        <Icon class="text-xl" :icon="currentIcon" />
      </button>
    </div>
    <div class="ml-auto flex items-center gap-3">
      <ThemeSwitcher />
      <Avatar />
    </div>
  </header>
</template>

<style scoped>
.search-bg {
  background-color: #e3e3e3;
}
</style>
