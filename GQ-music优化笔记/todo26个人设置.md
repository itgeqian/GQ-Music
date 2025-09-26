## todo26个人设置



### 目标
- 去掉个人设置页左侧导航，改为信息卡 + 两列表单 + 底部悬浮操作条。
- 新增偏好设置（本地持久化）：
  - 公开资料开关（绑定 public_profile）
  - 播放器偏好：默认音量、自动播放、恢复进度
  - 首页推荐显隐开关
  - 键盘快捷键提示开关与自定义映射（播放/上一首/下一首）
- 播放器恢复进度逻辑优化，刷新后从上次时间点继续。
- 个人中心顶部信息显示创建/更新时间与“第X天”小组件；后端 getUserInfo 返回时间字段。

### 改动清单
- 前端
  - 个人设置页：`vibe-music-client-main/src/pages/user/index.vue`
  - 播放器逻辑：`vibe-music-client-main/src/hooks/useAudioPlayer.ts`
  - 全局快捷键：`vibe-music-client-main/src/layout/index.vue`
  - 首页推荐显隐：`vibe-music-client-main/src/pages/index.vue`
  - 个人主页美化（名片、概览卡、走势、风格标签）：`vibe-music-client-main/src/pages/profile/[id].vue`
- 后端
  - 补充时间字段返回：`src/main/java/cn/edu/seig/vibemusic/model/vo/UserVO.java` 与 `UserServiceImpl#userInfo`

### 关键实现与代码
1) 个人设置页：偏好项与本地持久化（volume/autoplay/resume/hotkey/homeRecommend）
```1:48:vibe-music-client-main/src/pages/user/index.vue
import { AudioStore } from '@/stores/modules/audio'
...
const audioStore = AudioStore()

// 本地偏好
const playerVolume = ref<number>(Number(localStorage.getItem('vmc:pref:volume') || audioStore.volume || 50))
const autoplay = ref<boolean>(localStorage.getItem('vmc:pref:autoplay') === '1')
const resumeProgress = ref<boolean>(localStorage.getItem('vmc:pref:resume') === '1')
const homeRecommend = ref<boolean>(localStorage.getItem('vmc:pref:homeRecommend') !== '0')
...
const onVolumeChange = (v: number) => {
  playerVolume.value = v
  localStorage.setItem('vmc:pref:volume', String(v))
  audioStore.setAudioStore('volume', v)
}
const onAutoplayChange = (val: boolean) => {
  localStorage.setItem('vmc:pref:autoplay', val ? '1' : '0')
}
const onResumeChange = (val: boolean) => {
  localStorage.setItem('vmc:pref:resume', val ? '1' : '0')
}
const onHomeRecommendChange = (val: boolean) => {
  localStorage.setItem('vmc:pref:homeRecommend', val ? '1' : '0')
}
```

2) 个人设置页：自定义热键与提示开关（保存后全局热更新）
```300:346:vibe-music-client-main/src/pages/user/index.vue
const hotkeyHints = ref<boolean>(localStorage.getItem('vmc:pref:hotkeyHints') === '1')
const hotkeysInit = (() => {
  try { const raw = localStorage.getItem('vmc:pref:hotkeys'); if (raw) return JSON.parse(raw) } catch {}
  return { playPause: 'Space', prev: 'KeyJ', next: 'KeyL' }
})()
const hotkeys = reactive<{ playPause: string; prev: string; next: string }>(hotkeysInit)
...
const onHotkeyHintsChange = (val: boolean) => {
  localStorage.setItem('vmc:pref:hotkeyHints', val ? '1' : '0')
}
const saveHotkeys = () => {
  const norm = (v: string) => {
    const s = (v || '').trim()
    if (!s || s === ' ' || s.toLowerCase() === '空格' || s.toLowerCase() === 'space') return 'Space'
    if (/^key[a-z]$/i.test(s)) return s[0].toUpperCase() + s.slice(1)
    if (/^[a-z]$/i.test(s)) return 'Key' + s.toUpperCase()
    return s
  }
  hotkeys.playPause = norm(hotkeys.playPause); hotkeys.prev = norm(hotkeys.prev); hotkeys.next = norm(hotkeys.next)
  localStorage.setItem('vmc:pref:hotkeys', JSON.stringify(hotkeys))
  try { (window as any).dispatchEvent(new CustomEvent('hotkeys:update', { detail: hotkeys })) } catch {}
  ElMessage.success('快捷键已保存')
}
```

3) 全局快捷键：与底部播放栏使用同一播放器实例；监听配置变更
```13:58:vibe-music-client-main/src/layout/index.vue
const player = AudioPlayer()
provide('audioPlayer', player)

const getKeyMap = () => {
  try { const raw = localStorage.getItem('vmc:pref:hotkeys'); if (raw) return JSON.parse(raw) } catch {}
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
  else if (e.code === 'ArrowLeft') { e.preventDefault(); player.seek?.(Math.max(0, (player.currentTime?.value || 0) - 5)) }
  else if (e.code === 'ArrowRight') { e.preventDefault(); player.seek?.((player.currentTime?.value || 0) + 5) }
}
onMounted(() => {
  window.addEventListener('keydown', handler)
  window.addEventListener('hotkeys:update', (ev: any) => {
    try { const data = ev?.detail || getKeyMap(); keyMap.playPause = data.playPause; keyMap.next = data.next; keyMap.prev = data.prev
      localStorage.setItem('vmc:pref:hotkeys', JSON.stringify(keyMap)) } catch {}
  })
})
```

4) 播放器恢复进度：刷新后从上次位置继续（带调试日志）
```26:116:vibe-music-client-main/src/hooks/useAudioPlayer.ts
let resumePendingTime: number | null = null
...
const play = () => {
  if (audioElement.value) {
    ensureAnalyser()
    audioCtx?.resume?.().catch(() => {})
    if (resumePendingTime == null) {
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
  }
}
...
if (audioElement.value) {
  audioElement.value.src = currentTrack.value.url
  audioElement.value.load()
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
...
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
```

5) 首页“今日为你推荐”显隐
```1:20:vibe-music-client-main/src/pages/index.vue
const { loadTrack, play } = useAudioPlayer()
const showHomeRecommend = ref<boolean>(localStorage.getItem('vmc:pref:homeRecommend') !== '0')
```
```140:176:vibe-music-client-main/src/pages/index.vue
<!-- 推荐（显隐开关） -->
<button class="mt-6" v-if="showHomeRecommend">
  ...
</button>
```

6) 个人设置顶部时间与“第X天”
```30:76:vibe-music-client-main/src/pages/user/index.vue
const userMeta = reactive({ status: undefined as undefined | number, publicProfile: undefined as undefined | number, createTime: '' as string, updateTime: '' as string })
const daysSince = ref<number>(0)
const updateDaysSince = () => {
  try {
    let basis = userMeta.createTime
    if (!basis) {
      const key = 'vmc:user:firstSeen'
      const cached = localStorage.getItem(key)
      if (cached) basis = cached
      else { basis = new Date().toISOString().slice(0, 19).replace('T', ' '); localStorage.setItem(key, basis) }
    }
    const norm = String(basis).replace('T', ' ').replace(/-/g, '/')
    const created = new Date(norm)
    const diff = Math.max(0, Date.now() - created.getTime())
    daysSince.value = Math.floor(diff / 86400000) + 1
  } catch { daysSince.value = 0 }
}
const formatDateTime = (s?: string) => { if (!s) return '—'; const str = String(s).replace('T', ' '); return str.length > 19 ? str.slice(0, 19) : str }
```

7) 后端：getUserInfo 返回时间字段（供前端展示）
```1:45:src/main/java/cn/edu/seig/vibemusic/model/vo/UserVO.java
@Data
public class UserVO implements Serializable {
  ...
  private String introduction;
  /** 创建时间 */
  private java.time.LocalDateTime createTime;
  /** 更新时间 */
  private java.time.LocalDateTime updateTime;
}
```
```170:180:src/main/java/cn/edu/seig/vibemusic/service/impl/UserServiceImpl.java
public Result<UserVO> userInfo() {
  Map<String, Object> map = ThreadLocalUtil.get();
  Long userId = TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));
  User user = userMapper.selectById(userId);
  UserVO userVO = new UserVO();
  BeanUtils.copyProperties(user, userVO);
  return Result.success(userVO);
}
```

### 本地存储键约定
- 音量：`vmc:pref:volume`（0-100）
- 自动播放：`vmc:pref:autoplay`（'1'/'0'）
- 恢复进度：`vmc:pref:resume`（'1'/'0'）
- 上次播放信息：`vmc:player:lastTrackId`、`vmc:player:lastTime`
- 首页推荐显隐：`vmc:pref:homeRecommend`（'1'/'0'）
- 快捷键与提示：`vmc:pref:hotkeys`（JSON），`vmc:pref:hotkeyHints`（'1'/'0'）
- 首次到访时间（fallback）：`vmc:user:firstSeen`

### 测试要点
- 未登录时打开个人设置，应弹出登录对话框。
- 修改音量/自动播放/恢复进度，刷新页面后仍生效。
- 播放到中途刷新 → 再次点击同一歌曲，应从上次时间继续（查看控制台日志标记）。
- 修改热键后无需刷新即可生效，底部播放栏同步响应。
- 个人设置顶部时间格式为 “YYYY-MM-DD HH:mm:ss”，且“第 X 天”正确显示。

