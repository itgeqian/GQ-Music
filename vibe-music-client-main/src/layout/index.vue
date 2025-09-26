<script setup lang="ts">
import Header from './components/header/index.vue'
import Aside from './components/aside/index.vue'
import Main from './components/main/index.vue'
import Footer from './components/footer/index.vue'
// import BG from './components/bg/index.vue'
import { themeStore } from '@/stores/modules/theme'
const theme = themeStore()
import { AudioPlayer } from '@/hooks/useAudioPlayer'
import { UserStore } from '@/stores/modules/user'
const user = UserStore()

const player = AudioPlayer()
provide('audioPlayer', player)

// 键盘快捷键：可自定义映射与提示开关
const getKeyMap = () => {
  try {
    const raw = localStorage.getItem('vmc:pref:hotkeys')
    if (raw) return JSON.parse(raw)
  } catch {}
  return { playPause: 'Space', next: 'KeyL', prev: 'KeyJ' }
}
const showHotkeyHints = localStorage.getItem('vmc:pref:hotkeyHints') === '1'
const keyMap = reactive(getKeyMap())

const handler = (e: KeyboardEvent) => {
  const tag = (e.target as HTMLElement)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || (e.target as HTMLElement)?.isContentEditable) return
  if (e.code === keyMap.playPause) { e.preventDefault(); player.togglePlayPause?.() }
  else if (e.code === keyMap.next) { e.preventDefault(); player.nextTrack?.() }
  else if (e.code === keyMap.prev) { e.preventDefault(); player.prevTrack?.() }
  else if (e.code === 'ArrowLeft') {
    e.preventDefault()
    const t = Math.max(0, (player.currentTime?.value || 0) - 5)
    player.seek?.(t)
  } else if (e.code === 'ArrowRight') {
    e.preventDefault()
    const dur = player.duration?.value || 0
    const t = Math.min(dur > 0 ? dur : (player.currentTime?.value || 0) + 5, (player.currentTime?.value || 0) + 5)
    player.seek?.(t)
  }
}

onMounted(() => {
  window.addEventListener('keydown', handler)
  if (showHotkeyHints) {
    console.info('快捷键: 播放/暂停 Space（可在个人设置自定义），上一首 J，下一首 L')
  }
  // 监听来自设置页的快捷键更新事件
  window.addEventListener('hotkeys:update', (ev: any) => {
    try {
      const data = ev?.detail || getKeyMap()
      keyMap.playPause = data.playPause
      keyMap.next = data.next
      keyMap.prev = data.prev
      // 为防缓存问题，立刻写回 localStorage 并提示
      localStorage.setItem('vmc:pref:hotkeys', JSON.stringify(keyMap))
    } catch {}
  })
  // 跨标签同步
  window.addEventListener('storage', () => {
    const latest = getKeyMap()
    keyMap.playPause = latest.playPause
    keyMap.next = latest.next
    keyMap.prev = latest.prev
  })
})

onUnmounted(() => {
  window.removeEventListener('keydown', handler)
})
</script>
<template>
  <!-- <BG /> -->
  <div class="absolute w-full flex flex-col h-full">
    <!-- 背景层：视频优先，其次静态图，仅背景模糊 -->
    <div class="absolute inset-0 -z-10">
      <video v-if="user.isLoggedIn && theme.videoUrl" :src="theme.videoUrl" autoplay muted loop playsinline
             style="width:100%;height:100%;object-fit:cover;"
             :style="{
               filter: `${theme.blurStrength ? `blur(${theme.blurStrength}px)` : ''} ${theme.brightness !== undefined ? `brightness(${theme.brightness}%)` : ''}`.trim()
             }"></video>
      <div v-else :style="(user.isLoggedIn && theme.backgroundUrl) ? {
        backgroundImage: `url(${theme.backgroundUrl})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center center',
        filter: `${theme.blurStrength ? `blur(${theme.blurStrength}px)` : ''} ${theme.brightness !== undefined ? `brightness(${theme.brightness}%)` : ''}`.trim()
      } : {}" class="w-full h-full"></div>
    </div>

    <!-- 前景内容：不受模糊影响 -->
    <div class="relative z-10 w-full flex flex-col h-full overflow-hidden">
      <Header />
      <div class="flex flex-1 overflow-hidden">
        <Aside />
        <Main />
      </div>
      <Footer />
    </div>
  </div>
</template>
