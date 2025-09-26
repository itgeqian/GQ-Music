<script setup lang="ts">
import { formatTime } from '@/utils'
import { Icon } from '@iconify/vue'
import type { SongDetail } from '@/api/interface'
import { ref, inject, type Ref, computed } from 'vue'
import { useAudioPlayer } from '@/hooks/useAudioPlayer'
import vinylImg from '@/assets/vinyl.png'
import Recently from '../../layout/components/footer/components/recently.vue'
import { collectSong, cancelCollectSong } from '@/api/system'
import { ElMessage } from 'element-plus'
import { UserStore } from '@/stores/modules/user'

const {
  currentTrack,
  isPlaying,
  currentTime,
  duration,
  nextTrack,
  prevTrack,
  togglePlayPause,
  seek,
  setPlayMode,
  // 供方案C：随音量缩放
  visualLevel
} = useAudioPlayer()

// 光效：把 visualLevel(1~1.15) 映射到 0~1 的强度
const levelNorm = computed(() => {
  const n = (visualLevel.value - 1) / 0.15
  return Math.max(0, Math.min(1, n))
})
const glowOpacity = computed(() => 0.5 + levelNorm.value * 0.45) // 0.50~0.95 更亮
const glowScale = computed(() => 1.25 + levelNorm.value * 0.45)   // 1.25~1.70 扩散更远
const glowShadow = computed(() => `0 0 ${20 + levelNorm.value * 30}px rgba(42,104,250,0.7)`) // 更强阴影
// Rays：放射光线强度/扩散
const raysOpacity = computed(() => 0.18 + levelNorm.value * 0.4) // 0.18~0.58
const raysScale = computed(() => 1.18 + levelNorm.value * 0.55)  // 1.18~1.73

const songDetail = inject<Ref<SongDetail | null>>('songDetail')
const userStore = UserStore()

// 添加播放模式相关逻辑
const playModes = {
  order: {
    icon: 'ri:order-play-line',
    next: 'shuffle',
    tooltip: '顺序播放'
  },
  shuffle: {
    icon: 'ri:shuffle-line',
    next: 'loop',
    tooltip: '随机播放'
  },
  loop: {
    icon: 'ri:repeat-2-line',
    next: 'single',
    tooltip: '列表循环'
  },
  single: {
    icon: 'ri:repeat-one-line',
    next: 'order',
    tooltip: '单曲循环'
  }
}

const currentMode = ref('order')

const togglePlayMode = () => {
  const nextMode = playModes[currentMode.value].next
  currentMode.value = nextMode
  setPlayMode(nextMode)
}

// 喜欢/取消喜欢
const isLiked = computed(() => {
  const v: any = songDetail?.value?.likeStatus
  return v === 1 || v === true
})

const toggleLike = async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录'); try { window.dispatchEvent(new CustomEvent('auth:show')) } catch {}
    return
  }
  const sid = songDetail?.value?.songId || Number(currentTrack.value.id)
  if (!sid) return
  try {
    if (!isLiked.value) {
      const res: any = await collectSong(Number(sid))
      if (res.code === 0) {
        if (songDetail?.value) (songDetail.value as any).likeStatus = 1
        if (currentTrack.value) (currentTrack.value as any).likeStatus = 1
        ElMessage.success('已添加到我的喜欢')
      } else {
        ElMessage.error(res.message || '添加到我的喜欢失败')
      }
    } else {
      const res: any = await cancelCollectSong(Number(sid))
      if (res.code === 0) {
        if (songDetail?.value) (songDetail.value as any).likeStatus = 0
        if (currentTrack.value) (currentTrack.value as any).likeStatus = 0
        ElMessage.success('已取消喜欢')
      } else {
        ElMessage.error(res.message || '取消喜欢失败')
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}
</script>

<template>
  <div class="w-full h-[calc(80vh-8rem)] relative inset-0 px-4 flex flex-col items-center">
    <div class="flex flex-1 flex-col gap-4 items-center justify-center w-full">
      <!-- 封面 -->
      <div :class="` ${isPlaying ? 'is-playing' : ''}`">
        <div class="album">
          <div class="album-glow" :style="{
            opacity: glowOpacity,
            transform: `translate(-50%, -50%) scale(${glowScale})`
          }"></div>
          <div class="album-rays" :style="{
            opacity: raysOpacity,
            transform: `translate(-50%, -50%) scale(${raysScale})`,
            animationPlayState: isPlaying ? 'running' : 'paused'
          }"></div>
          <div class="album-art" :style="{
            backgroundImage: `url(${songDetail?.coverUrl || currentTrack.cover})`,
            transform: `scale(${visualLevel})`,
            transition: 'transform 80ms linear',
            boxShadow: glowShadow
          }"></div>
          <div class="vinyl" :style="{
            animationPlayState: isPlaying ? 'running' : 'paused',
            backgroundImage: `url(${vinylImg}), url(${songDetail?.coverUrl || currentTrack.cover})`
          }"></div>
        </div>
      </div>
      <!-- 标题类 -->
      <div class="flex flex-col items-center gap-2 mt-10">
        <h2 class="text-3xl font-bold text-primary-foreground">
          {{ songDetail?.songName || currentTrack.title }}
        </h2>
        <p class="text-xl text-inactive">{{ songDetail?.artistName || currentTrack.artist }}</p>
      </div>
      <!-- 控制区 -->
      <div class="flex gap-2 w-full items-center justify-center mt-8">
        <div class="flex items-center gap-2 w-2/4">
          <span class="text-xs w-10 text-foreground/50 text-center">{{
            formatTime(currentTime)
          }}</span>
          <el-slider v-model="currentTime" :show-tooltip="false" @change="seek" :max="duration" class="flex-1"
            size="small" />
          <span class="text-xs w-10 text-foreground/50 text-center">{{
            formatTime(duration)
          }}</span>
        </div>
      </div>
      <div class="flex items-center justify-center gap-14 w-2/4 mt-12">
        <!-- 喜欢按钮 -->
        <el-button text circle class="!p-3" @click="toggleLike">
          <icon-mdi:cards-heart v-if="isLiked" class="text-2xl text-red-500" />
          <icon-mdi:cards-heart-outline v-else class="text-2xl" />
        </el-button>
        <el-tooltip :content="playModes[currentMode].tooltip" placement="top" effect="dark">
          <el-button text circle @click="togglePlayMode">
            <Icon :icon="playModes[currentMode].icon" class="text-2xl" />
          </el-button>
        </el-tooltip>
        <el-button text circle class="!p-3" @click="prevTrack">
          <icon-solar:skip-previous-bold class="text-2xl" />
        </el-button>
        <el-button text circle class="!p-3" @click="togglePlayPause">
          <Icon
            :icon="isPlaying ? 'ic:round-pause-circle' : 'material-symbols:play-circle'"
            class="text-7xl"
            :color="'#2a68fa'"
          />
        </el-button>
        <el-button text circle class="!p-3" @click="nextTrack">
          <icon-solar:skip-previous-bold class="scale-x-[-1] text-2xl" />
        </el-button>
        <el-button text circle class="scale-125 text-primary-foreground">
          <Recently />
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.album {
  box-shadow: 3px 3px 15px rgba(0, 0, 0, 0.65);
  height: 100%;
  position: relative;
  width: 100%;
  z-index: 10;
  border-radius: 8px;
}

.album-glow {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 300px;
  height: 300px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  pointer-events: none;
  transition: opacity 80ms linear, transform 80ms linear;
  /* 太阳放射：核心亮环 + 外扩渐变 */
  background:
    radial-gradient(circle at center,
      rgba(42,104,250,0.9) 55%,
      rgba(42,104,250,0.45) 75%,
      rgba(42,104,250,0.15) 90%,
      rgba(42,104,250,0.0) 100%),
    radial-gradient(circle at center,
      rgba(255,255,255,0.28) 0%,
      rgba(255,255,255,0.0) 70%);
  /* 只显示外圈，内圈打洞，让光从边缘向外扩散 */
  -webkit-mask: radial-gradient(circle at center, transparent 52%, #000 54%);
  mask: radial-gradient(circle at center, transparent 52%, #000 54%);
  filter: blur(22px);
  z-index: 5;
}

.album-rays {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 360px;
  height: 360px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  pointer-events: none;
  transition: opacity 80ms linear, transform 80ms linear;
  z-index: 6; /* 位于光晕之上，封面之下 */
  /* 放射光线（重复扇形） */
  background: repeating-conic-gradient(
    from 0deg,
    rgba(42,104,250,0.55) 0deg 6deg,
    rgba(42,104,250,0.0) 6deg 14deg
  );
  /* 仅显示外圈（内侧打洞） */
  -webkit-mask: radial-gradient(circle at center, transparent 48%, #000 52%);
  mask: radial-gradient(circle at center, transparent 48%, #000 52%);
  filter: blur(12px);
  mix-blend-mode: screen;
}

.album-art {
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
  height: 315px;
  position: relative;
  width: 325px;
  z-index: 10;
  border-radius: 50%;
}

.vinyl {
  animation: spin 2s linear infinite;
  transition: all 500ms;
  background-position: center, center;
  background-size: cover, 40% auto;
  background-repeat: no-repeat;
  border-radius: 100%;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.8);
  height: 300px;
  left: 5%;
  position: absolute;
  top: 8px;
  width: 300px;
  z-index: 5;
  will-change: transform, left;

  .is-playing & {
    left: 52%;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
</style>
