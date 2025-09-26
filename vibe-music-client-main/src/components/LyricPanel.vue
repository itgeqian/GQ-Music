<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { getLyric } from '@/api/system'
import { useAudioPlayer } from '@/hooks/useAudioPlayer'

const props = defineProps<{ songId: number | null; currentTime: number }>()

const lines = ref<{ timeMs: number; text: string }[]>([])
const active = ref(0)
const box = ref<HTMLElement | null>(null)
const audio = useAudioPlayer()

// 预览滚动与时间提示
const previewIndex = ref(0)
const showHint = ref(false)
const hintText = ref('00:00')
let hideTimer: number | null = null
let lastWheelAt = 0
let holdAutoScrollUntil = 0

async function fetchLyric() {
  lines.value = []
  active.value = 0
  if (!props.songId) return
  const res: any = await getLyric(props.songId)
  if (res?.code === 0 && Array.isArray(res.data)) {
    lines.value = (res.data as any[])
      .map(l => ({ timeMs: l.timeMs, text: String(l.text ?? '') }))
      .filter(l => l.text.trim().length > 0) // 去掉空行
    console.log('[LyricPanel] fetched lines:', lines.value.length)
  }
}

function findActiveIndex(ms: number) {
  if (!lines.value.length) return 0
  let l = 0, r = lines.value.length - 1, ans = 0
  while (l <= r) {
    const mid = (l + r) >> 1
    if (lines.value[mid].timeMs <= ms) { ans = mid; l = mid + 1 } else r = mid - 1
  }
  return ans
}

function scrollToActive() {
  const el = box.value?.querySelector(`[data-i="${active.value}"]`) as HTMLElement | null
  el?.scrollIntoView({ block: 'center', behavior: 'smooth' })
}

function seekTo(i: number) {
  const ms = lines.value[i]?.timeMs ?? 0
  audio.seek(ms / 1000)
}

function scrollToIndex(i: number) {
  const container = box.value
  const el = container?.querySelector(`[data-i="${i}"]`) as HTMLElement | null
  if (!container || !el) return
  // 改为“每次滚动将目标行顶到容器顶部附近”，保证前几行也会发生位移
  const paddingTop = 8 // 与容器 py-2 对齐
  const targetTop = Math.max(0, el.offsetTop - paddingTop)
  console.log('[LyricPanel] scrollToIndex', { i, elOffsetTop: el.offsetTop, elH: el.offsetHeight, ch: container.clientHeight, targetTop, scrollTopBefore: container.scrollTop })
  container.scrollTo({ top: targetTop, behavior: 'smooth' })
}

function formatTime(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000))
  const mm = String(Math.floor(s / 60)).padStart(2, '0')
  const ss = String(s % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

// 行内显示时间，无需绝对定位，避免卡住问题

function stepPreview(direction: 1 | -1) {
  if (!lines.value.length) return
  const now = Date.now()
  if (now - lastWheelAt < 120) return
  lastWheelAt = now
  holdAutoScrollUntil = now + 1000

  if (previewIndex.value < 0 || previewIndex.value >= lines.value.length) {
    previewIndex.value = active.value
  }
  previewIndex.value = Math.min(lines.value.length - 1, Math.max(0, previewIndex.value + direction))
  console.log('[LyricPanel] stepPreview', { direction, previewIndex: previewIndex.value, active: active.value })
  scrollToIndex(previewIndex.value)

  hintText.value = formatTime(lines.value[previewIndex.value].timeMs)
  showHint.value = true
  if (hideTimer) window.clearTimeout(hideTimer)
  hideTimer = window.setTimeout(() => (showHint.value = false), 1000)
}

function onWheel(e: WheelEvent) {
  if (!box.value) return
  e.preventDefault()
  const dir: 1 | -1 = e.deltaY > 0 ? 1 : -1
  console.log('[LyricPanel] wheel', { deltaY: e.deltaY, dir })
  stepPreview(dir)
}

onMounted(() => {
  if (box.value) box.value.addEventListener('wheel', onWheel, { passive: false })
  // 无需额外 scroll 监听
})
onBeforeUnmount(() => {
  if (box.value) box.value.removeEventListener('wheel', onWheel as any)
  if (hideTimer) window.clearTimeout(hideTimer)
})

watch(() => props.songId, () => fetchLyric(), { immediate: true })
watch(() => props.currentTime, (t) => {
  if (Date.now() < holdAutoScrollUntil) return
  const ms = Math.floor(t * 1000)
  const idx = findActiveIndex(ms)
  if (idx !== active.value) {
    console.log('[LyricPanel] time update', { t, ms, idx, prevActive: active.value })
    active.value = idx
    scrollToActive()
  }
})
</script>

<template>
  <div ref="box" class="relative h-80 overflow-y-auto px-2 py-2 rounded-md bg-white/40 dark:bg-zinc-800/30">
    <div v-if="!lines.length" class="text-center text-gray-500 dark:text-gray-400 py-12">暂无歌词</div>
    <div v-else class="space-y-2">
      <div
        v-for="(l,i) in lines"
        :key="i"
        :data-i="i"
        class="relative text-center cursor-pointer transition px-1 py-1 pl-8 select-none"
        :class="i===active ? 'text-primary text-base font-semibold' : 'opacity-80 hover:opacity-100'"
        @click="seekTo(i)"
      >
        <span v-show="showHint && i===previewIndex" class="absolute left-1 text-xs px-1.5 py-0.5 rounded bg-black/60 text-white select-none z-10">{{ hintText }}</span>
        {{ l.text }}
      </div>
    </div>
  </div>
</template>

<style scoped>
</style>


