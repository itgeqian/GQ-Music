<script setup lang="ts">
import Left from './components/left.vue'
import Center from './components/center.vue'
import Right from './components/right.vue'
import SimpleVisualizer from '@/components/AudioVisualizer/SimpleVisualizer.vue'
import { useAudioPlayer } from '@/hooks/useAudioPlayer'

const { isPlaying } = useAudioPlayer()

// 是否显示音频可视化条（用户偏好）
const showVisualizer = ref(localStorage.getItem('vmc:pref:visualizer') !== '0')

onMounted(() => {
  // 接收来自“个人设置”的更新
  const handler = (ev: any) => {
    try {
      const enabled = !!(ev?.detail?.enabled)
      showVisualizer.value = enabled
    } catch {}
  }
  window.addEventListener('pref:visualizer:update', handler)
  // 跨标签同步
  const storageHandler = () => {
    showVisualizer.value = localStorage.getItem('vmc:pref:visualizer') !== '0'
  }
  window.addEventListener('storage', storageHandler)
  onBeforeUnmount(() => {
    window.removeEventListener('pref:visualizer:update', handler)
    window.removeEventListener('storage', storageHandler)
  })
})
</script>
<template>
  <div class="footer-container">
    <!-- 音频可视化条 - 只在播放时显示，且用户开启 -->
    <SimpleVisualizer v-if="showVisualizer" />
    
    <!-- 播放控制栏 -->
    <footer class="border-t flex items-center justify-between shadow-2xl shadow-black">
      <!-- 左边：歌曲封面和歌曲名称 -->
      <Left />
      <!-- 中间：控制区 -->
      <Center />
      <!-- 右边：历史播放和音量 -->
      <Right />
    </footer>
  </div>
</template>

<style scoped>
.footer-container {
  position: relative;
}

</style>
