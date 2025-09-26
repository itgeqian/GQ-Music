<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useAudioPlayer } from '@/hooks/useAudioPlayer'

const { isPlaying } = useAudioPlayer()

// 音频条数据
const bars = ref<number[]>([])
const barCount = 150 // 音频条数量，增加覆盖范围
const maxHeight = 60 // 最大高度，避免遮挡内容

// 动画相关
let animationId: number | null = null
let lastUpdateTime = 0
const updateInterval = 100 // 限制更新频率为10fps，减少性能消耗

// 轻量级模拟动画
const updateBars = () => {
  if (!isPlaying.value) {
    // 暂停时逐渐降低
    bars.value = bars.value.map(height => Math.max(height * 0.9, 0))
    return
  }

  const newBars: number[] = []
  const time = Date.now() * 0.004 // 稍微加快动画速度
  
  for (let i = 0; i < barCount; i++) {
    // 创建更丰富的跳动效果
    const frequency = 0.2 + i * 0.03
    const amplitude = 0.3 + Math.random() * 0.4
    const baseHeight = Math.abs(Math.sin(time * frequency)) * amplitude
    const randomVariation = Math.random() * 0.2
    const height = baseHeight + randomVariation
    newBars.push(Math.min(height, 1))
  }
  
  bars.value = newBars
}

// 动态颜色函数
const getBarColor = (index: number, height: number) => {
  if (!isPlaying.value) {
    return 'transparent' // 暂停时透明
  }
  
  // 根据位置和高度创建彩虹渐变效果
  const hue = (index * 3.6 + height * 60) % 360 // 色相变化
  const saturation = 70 + height * 30 // 饱和度变化
  const lightness = 50 + height * 20 // 亮度变化
  
  return `hsl(${hue}, ${saturation}%, ${lightness}%)`
}

// 优化的动画循环
const animate = (currentTime: number) => {
  // 限制更新频率
  if (currentTime - lastUpdateTime >= updateInterval) {
    updateBars()
    lastUpdateTime = currentTime
  }
  
  animationId = requestAnimationFrame(animate)
}

onMounted(() => {
  // 初始化音频条数据
  bars.value = new Array(barCount).fill(0.1)
  
  // 开始动画循环
  animationId = requestAnimationFrame(animate)
})

onUnmounted(() => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
})
</script>

<template>
  <div 
    v-if="isPlaying" 
    class="simple-audio-visualizer flex items-end justify-center gap-0.5 h-6 w-full playing"
    style="background: transparent !important;"
  >
    <div
      v-for="(height, index) in bars"
      :key="index"
      class="audio-bar transition-all duration-150 ease-out"
      :style="{
        height: `${Math.max(height * maxHeight, 2)}px`,
        width: '2px',
        borderRadius: '1px',
        opacity: 0.7 + height * 0.3,
        background: getBarColor(index, height)
      }"
    />
  </div>
</template>

<style scoped>
.simple-audio-visualizer {
  padding: 4px 0;
  background: transparent !important;
}

/* 强制覆盖主题样式 */
.simple-audio-visualizer,
div.simple-audio-visualizer,
.simple-audio-visualizer.flex {
  background: transparent !important;
  background-color: transparent !important;
}

/* 覆盖暗色和亮色主题 */
:deep(.dark) .simple-audio-visualizer,
:deep(.light) .simple-audio-visualizer,
.dark .simple-audio-visualizer,
.light .simple-audio-visualizer {
  background: transparent !important;
  background-color: transparent !important;
}

.audio-bar {
  min-height: 2px;
  border-radius: 1px;
}

</style>
