import { trackModel } from '@/stores/interface'
import { defaultSong } from '@/mock'
import { ElNotification } from 'element-plus'
import { PlayMode } from './interface'
import { urlV1 } from '@/api'
import { useRecentStore } from '@/stores/modules/recent'
import { reportRecentPlay } from '@/api/system'
interface AudioPlayer {
  isPlaying: Ref<boolean>
  currentTrack: ComputedRef<trackModel>
  currentTime: Ref<number>
  duration: Ref<number>
  volume: Ref<number>
  visualLevel: Ref<number>
  //   currentLyricIndex: Ref<number>
  audioElement: Ref<HTMLAudioElement | null>
  play: () => void
  pause: () => void
  nextTrack: () => void
  prevTrack: () => void
  seek: (time: number) => void
  togglePlayPause: () => void
  setVolume: (volume: number) => void
  setPlayMode: (mode: PlayMode) => void
  loadTrack: () => Promise<void>
}

export const AudioPlayer = () => {
  const audioStore = AudioStore()
  const recent = useRecentStore?.()
  const audioElement = ref<HTMLAudioElement | null>(null)
  const isPlaying = ref(false)
  const volume = ref()
  const playMode = ref<PlayMode>('order') // 默认为顺序播放
  // 恢复进度：待应用的时间（秒）
  let resumePendingTime: number | null = null

  // 当前播放的歌曲
  const currentTrack = computed<trackModel>(
    () => audioStore.trackList[audioStore.currentSongIndex] || defaultSong
  )
  const currentTime = ref(0)
  const duration = ref(0)
  // ========== 音量级别（供方案C：专辑随音量缩放） ==========
  let audioCtx: AudioContext | null = null
  let analyser: AnalyserNode | null = null
  let freqData: Uint8Array | null = null
  const visualLevel = ref(1)
  let rafId = 0

  const ensureAnalyser = () => {
    if (!audioElement.value) return
    if (audioCtx) return
    try {
      audioElement.value.crossOrigin = 'anonymous'
      audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)()
      const src = audioCtx.createMediaElementSource(audioElement.value)
      analyser = audioCtx.createAnalyser()
      analyser.fftSize = 256
      freqData = new Uint8Array(analyser.frequencyBinCount)
      src.connect(analyser)
      analyser.connect(audioCtx.destination)
    } catch {}
  }

  const getLevel = (): number => {
    if (!analyser || !freqData) return 1
    analyser.getByteFrequencyData(freqData)
    const avg = freqData.reduce((a, b) => a + b, 0) / freqData.length
    return 1 + (avg / 255) * 0.15 // 1.00~1.15 之间
  }

  const loopLevel = () => {
    visualLevel.value = getLevel()
    rafId = requestAnimationFrame(loopLevel)
  }

  //   // 用于追踪当前歌词索引
  //   const currentLyricIndex = ref(0)

  //   currentTrack.value.lyrics?.lyrics

  //   // 更新当前歌曲歌词索引
  //   const updateCurrentLyricIndex = (newTime: number = 0) => {
  //     if (!currentTrack.value.lyrics?.lines) return

  //     // 找到当前时间对应的歌词行
  //     const lyrics = currentTrack.value.lyrics.lines
  //     const targetIndex = lyrics.findIndex(
  //       (line: { time: number }) => line.time > newTime * 1000
  //     )
  //     currentLyricIndex.value =
  //       targetIndex === -1 ? lyrics.length - 1 : targetIndex - 1
  //   }

  // 播放音乐
  const play = () => {
    if (audioElement.value) {
      ensureAnalyser()
      audioCtx?.resume?.().catch(() => {})
      // 若存在待恢复时间，先跳到对应位置再播放
      if (resumePendingTime == null) {
        // 兜底：某些场景未调用 loadTrack，这里再尝试一次读取恢复时间
        try {
          const resume = localStorage.getItem('vmc:pref:resume') === '1'
          if (resume) {
            const lastId = localStorage.getItem('vmc:player:lastTrackId')
            const lastTime = Number(localStorage.getItem('vmc:player:lastTime') || '')
            if (lastId && String(currentTrack.value.id) === lastId && Number.isFinite(lastTime) && lastTime > 0) {
              resumePendingTime = lastTime
              console.log('[player-resume] fallback set resumePendingTime in play()', { id: lastId, lastTime })
            }
          }
        } catch {}
      }
      if (resumePendingTime != null) {
        const apply = () => {
          try { audioElement.value!.currentTime = resumePendingTime as number } catch {}
          resumePendingTime = null
          audioElement.value!.play()
          console.log('[player-resume] applied and play()', { time: audioElement.value!.currentTime })
        }
        if (audioElement.value.readyState >= 1) apply()
        else audioElement.value.addEventListener('loadedmetadata', apply, { once: true })
      } else {
        audioElement.value.play()
        console.log('[player-resume] normal play()')
      }
      isPlaying.value = true
      try {
        // 记录最近播放（限制200条 + 上报后端）
        const t = currentTrack.value
        recent?.add?.(t)
        const idNum = Number(t?.id)
        if (Number.isFinite(idNum)) {
          reportRecentPlay({ songId: idNum })
            .then(() => { try { window.dispatchEvent(new CustomEvent('recent:added')) } catch {} })
            .catch(() => {})
        }
      } catch {}
    }
  }
  // 跳转到指定时间
  const seek = (time: number) => {
    if (audioElement.value) {
      audioElement.value.currentTime = time
      currentTime.value = time
    }
  }
  // 暂停音乐
  const pause = () => {
    if (audioElement.value) {
      audioElement.value.pause()
      isPlaying.value = false
    }
  }

  // 播放下一首
  const nextTrack = async () => {
    switch (playMode.value) {
      case 'loop':
        if (audioStore.currentSongIndex < audioStore.trackList.length - 1) {
          audioStore.currentSongIndex++
        } else {
          audioStore.currentSongIndex = 0 // 从头开始
        }
        break
      case 'shuffle':
        audioStore.currentSongIndex = Math.floor(
          Math.random() * audioStore.trackList.length
        )
        break
      case 'single':
        audioElement.value!.currentTime = 0
        break
      case 'order':
      default:
        if (audioStore.currentSongIndex < audioStore.trackList.length - 1) {
          audioStore.currentSongIndex++
        } else {
          audioStore.currentSongIndex = 0 // 从头开始
        }
        break
    }
    await loadTrack()
    play()
    // 切到下一曲后，将插入游标重置到当前曲目后一位，保证后续加入顺序正确
    try { audioStore.resetNextInsertIndex() } catch {}
  }

  // 播放上一首
  const prevTrack = async () => {
    switch (playMode.value) {
      case 'loop':
        if (audioStore.currentSongIndex > 0) {
          audioStore.currentSongIndex--
        } else {
          audioStore.currentSongIndex = audioStore.trackList.length - 1 // 从尾开始
        }
        break
      case 'shuffle':
        audioStore.currentSongIndex = Math.floor(
          Math.random() * audioStore.trackList.length
        )
        break
      case 'single':
        audioElement.value!.currentTime = 0
        break
      case 'order':
      default:
        if (audioStore.currentSongIndex > 0) {
          audioStore.currentSongIndex--
        } else {
          audioStore.currentSongIndex = audioStore.trackList.length - 1 // 从尾开始
        }
        break
    }
    await loadTrack()
    play()
    try { audioStore.resetNextInsertIndex() } catch {}
  }

  // 加载当前歌曲
  const loadTrack = async () => {
    // 检查歌曲 URL
    await checkUrl()
    // 歌词是否存在
    // checkLyrics()

    if (audioElement.value) {
      audioElement.value.src = currentTrack.value.url
      audioElement.value.load()
      // 恢复进度（偏好开启时）
      try {
        const resume = localStorage.getItem('vmc:pref:resume') === '1'
        if (resume) {
          const lastId = localStorage.getItem('vmc:player:lastTrackId')
          const lastTime = Number(localStorage.getItem('vmc:player:lastTime') || '')
          if (lastId && String(currentTrack.value.id) === lastId && Number.isFinite(lastTime) && lastTime > 0) {
            resumePendingTime = lastTime
            console.log('[player-resume] set resumePendingTime', { id: lastId, lastTime })
          } else {
            resumePendingTime = null
            console.log('[player-resume] no match or invalid lastTime', { lastId, lastTime, current: currentTrack.value.id })
          }
        } else {
          resumePendingTime = null
          console.log('[player-resume] preference disabled')
        }
      } catch { resumePendingTime = null }
    }
  }

  // 检查歌曲 URL
  const checkUrl = async () => {
    // 查看歌曲 URL 是否存在
    if (!currentTrack.value.url) {
      // 如果 currentTrack 的 url 不存在，则获取 URL
      const response = await urlV1(currentTrack.value.id)
      const url = response.data[0]?.url // 获取第一个 URL

      if (!url) return
      // 更新 trackList 中的对应歌曲的 url
      const trackIndex = audioStore.trackList.findIndex(
        (track: { id: any }) => track.id === currentTrack.value.id
      )
      if (trackIndex !== -1) {
        audioStore.trackList[trackIndex].url = url // 更新 URL
      }
    }
    return Promise.resolve()
  }

  // 解析歌词数据
  //   const checkLyrics = () => {
  //     // 查看歌词是否存在
  //     if (!currentTrack.value.lyrics) {
  //       // 如果 currentTrack 的 lyrics 不存在，则获取歌词
  //       lyricNew(currentTrack.value.id).then((response) => {
  //         // 更新 trackList 中的对应歌曲的 url
  //         const trackIndex = audioStore.trackList.findIndex(
  //           (track: { id: any }) => track.id === currentTrack.value.id
  //         )
  //         if (trackIndex !== -1) {
  //           audioStore.trackList[trackIndex].lyrics =
  //             parseAndMergeLyrics(response) // 更新 URL
  //         }
  //       })
  //     }
  //   }

  // 更新当前播放时间
  const updateTime = () => {
    if (audioElement.value) {
      currentTime.value = audioElement.value.currentTime
      try {
        localStorage.setItem('vmc:player:lastTrackId', String(currentTrack.value.id))
        localStorage.setItem('vmc:player:lastTime', String(Math.floor(currentTime.value)))
        console.log('[player-resume] tick save', { id: currentTrack.value.id, time: Math.floor(currentTime.value) })
      } catch {}
    }
  }

  // 更新总时长
  const onLoadedMetadata = () => {
    if (audioElement.value) {
      duration.value = audioElement.value.duration
    }
  }

  // 切换播放/暂停状态
  const togglePlayPause = () => {
    if (isPlaying.value) {
      pause()
    } else {
      play()
    }
  }

  // 设置音量
  const setVolume = (newVolume: number) => {
    if (audioElement.value) {
      volume.value = newVolume
      audioStore.setAudioStore('volume', newVolume)
      audioElement.value.volume = newVolume / 100
    }
  }

  // 设置播放模式
  const setPlayMode = (mode: PlayMode) => {
    playMode.value = mode
    const modeText = {
      order: '顺序播放',
      shuffle: '随机播放',
      loop: '列表循环',
      single: '单曲循环',
    }
    ElNotification({
      title: '播放模式',
      message: `已切换为${modeText[mode]}`,
      type: 'success',
    })
  }

  //   // 更新currentLyricIndex
  //   watch(currentTime, (newTime) => {
  //     updateCurrentLyricIndex(newTime)
  //   })

  // 组件挂载时初始化音频元素
  onMounted(() => {
    audioElement.value = new Audio(currentTrack.value.url)
    const savedVol = Number(localStorage.getItem('vmc:pref:volume') || '')
    volume.value = Number.isFinite(savedVol) ? savedVol : (audioStore.volume || 50)
    audioElement.value.volume = volume.value / 100
    ensureAnalyser()
    rafId = requestAnimationFrame(loopLevel)
    // 歌词是否存在
    // checkLyrics()
    // 添加事件监听器
    audioElement.value.addEventListener('timeupdate', updateTime)
    audioElement.value.addEventListener('ended', nextTrack)
    audioElement.value.addEventListener('loadedmetadata', onLoadedMetadata)
    // 页面关闭前写入一次进度
    window.addEventListener('beforeunload', () => {
      try {
        localStorage.setItem('vmc:player:lastTrackId', String(currentTrack.value.id))
        localStorage.setItem('vmc:player:lastTime', String(Math.floor(currentTime.value || 0)))
        console.log('[player-resume] beforeunload save', { id: currentTrack.value.id, time: Math.floor(currentTime.value || 0) })
      } catch {}
    })

    // 自动播放偏好
    const shouldAutoplay = localStorage.getItem('vmc:pref:autoplay') === '1'
    if (shouldAutoplay) {
      const tryPlay = () => {
        try { play() } catch {}
      }
      audioElement.value.addEventListener('canplay', tryPlay, { once: true })
      // 若已经可播放，直接尝试
      setTimeout(tryPlay, 100)
    }
  })

  // 组件卸载时移除事件监听器
  onUnmounted(() => {
    if (audioElement.value) {
      audioElement.value.removeEventListener('timeupdate', updateTime)
      audioElement.value.removeEventListener('ended', nextTrack)
      audioElement.value.removeEventListener('loadedmetadata', onLoadedMetadata)
    }
    cancelAnimationFrame(rafId)
    try { audioCtx?.close?.() } catch {}
    analyser = null
    freqData = null
    audioCtx = null
  })

  const audioPlayer: AudioPlayer = {
    isPlaying,
    currentTrack,
    currentTime,
    duration,
    volume,
    visualLevel,
    // currentLyricIndex,
    audioElement,
    play,
    pause,
    nextTrack,
    prevTrack,
    seek,
    togglePlayPause,
    setVolume,
    setPlayMode,
    loadTrack,
  }

  return audioPlayer
}

export const useAudioPlayer = (): AudioPlayer => {
  const audioPlayer = inject<AudioPlayer>('audioPlayer')
  if (!audioPlayer) {
    throw new Error('useAudioPlayer must be used within a provider')
  }
  return audioPlayer
}
