<!-- Profile.vue -->
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BackTop from '@/components/BackTop.vue'
import Table from '@/components/Table.vue'
import coverImg from '@/assets/user.jpg'
import { UserStore } from '@/stores/modules/user'
import { AudioStore } from '@/stores/modules/audio'
import { useAudioPlayer } from '@/hooks/useAudioPlayer'
import {
  getUserInfo,
  getFavoriteSongs,
  getFavoriteAlbums,
  getFavoritePlaylists,
  getMyPlaylists,
  getUserProfile,
  followUser,
  unfollowUser,
  getFavoriteSongsByUser,
  getFavoritePlaylistsByUser,
  getUserPlaylistsByUser,
  getFavoriteAlbumsByUser,
  getRecentPlays,
  getRecentPlaysByUser,
  getFollowing,
  getFollowers
} from '@/api/system'

const route = useRoute()
const router = useRouter()
const me = UserStore()

/** ====== 关键：紧凑区可折叠 ====== */
const showMoreOverview = ref(true) // 默认展开，避免空白区域误解

// 自己看自己不显示关注按钮：若无 id 参数（/profile）或 id 等于当前登录用户 id，则视为“自己”
const userIdFromRoute = computed(() => {
  const raw: any = route.params.id
  const v = Array.isArray(raw) ? raw[0] : raw
  const num = Number(v)
  return Number.isFinite(num) ? num : null
})
const isSelfView = computed(
  () => userIdFromRoute.value == null || me.userInfo?.userId === userIdFromRoute.value
)

const base = ref<{
  userId?: number
  username: string
  avatarUrl?: string
  fans?: number
  followings?: number
  privateUser?: boolean
  introduction?: string
}>({ username: '' })

const followed = ref<boolean | null>(null)
const activeTab = ref<'likes' | 'favPlaylists' | 'createdPlaylists'>('likes')

// 关注/粉丝 抽屉数据
type SimpleUser = { userId: number; username: string; avatarUrl?: string }
const drawerOpen = ref(false)
const drawerTab = ref<'following' | 'followers'>('following')
const followingList = ref<SimpleUser[]>([])
const followersList = ref<SimpleUser[]>([])
const drawerLoading = ref(false)
const normalizeAvatar = (list: any[]) => {
  return (list || []).map((u: any) => ({
    ...u,
    avatarUrl: u.avatarUrl || u.userAvatar || ''
  }))
}

const fetchFollowList = async (tab: 'following' | 'followers') => {
  drawerLoading.value = true
  try {
    const targetId = userIdFromRoute.value != null ? Number(userIdFromRoute.value) : undefined
    if (tab === 'following') {
      const res: any = await getFollowing(targetId ? { userId: targetId } : undefined)
      followingList.value = normalizeAvatar(res?.data)
    } else {
      const res: any = await getFollowers(targetId ? { userId: targetId } : undefined)
      followersList.value = normalizeAvatar(res?.data)
    }
  } finally {
    drawerLoading.value = false
  }
}

const openFollowDrawer = async (tab: 'following' | 'followers') => {
  drawerTab.value = tab
  drawerOpen.value = true
  await fetchFollowList(tab)
}

const switchDrawerTab = async (tab: 'following' | 'followers') => {
  drawerTab.value = tab
  // 若目标列表未加载，再拉一次
  if (tab === 'following' && followingList.value.length === 0) await fetchFollowList('following')
  if (tab === 'followers' && followersList.value.length === 0) await fetchFollowList('followers')
}

// 我喜欢（歌曲/专辑）
const likedSongs = ref<any[]>([])
const likedAlbums = ref<any[]>([])
const likeInnerTab = ref<'songs' | 'albums'>('songs')

// 收藏与创建的歌单
type SimplePlaylist = { playlistId: number; title: string; coverUrl?: string }
const favoritePlaylists = ref<SimplePlaylist[]>([])
const myPlaylists = ref<SimplePlaylist[]>([])

// 最近播放（用于近7天曲线与常听风格）
const recentItems = ref<any[]>([])
const recentTotal = ref(0)
const fetchRecent = async () => {
  try {
    const page = { pageNum: 1, pageSize: 500 }
    let res: any
    const id = userIdFromRoute.value
    if (id != null && !isSelfView.value) res = await getRecentPlaysByUser({ userId: Number(id), ...page })
    else res = await getRecentPlays(page)
    if (res?.code === 0 && res.data) {
      recentItems.value = res.data.items || []
      recentTotal.value = res.data.total || recentItems.value.length
    } else {
      recentItems.value = []
      recentTotal.value = 0
    }
  } catch {
    recentItems.value = []
    recentTotal.value = 0
  }
}

const fetchBase = async () => {
  const targetId =
    userIdFromRoute.value != null ? userIdFromRoute.value : (me.userInfo?.userId as any)
  if (!targetId) {
    base.value = { username: '' }
    followed.value = null
    return
  }
  const res = await getUserProfile(Number(targetId))
  if (res.code === 0 && res.data) {
    const d: any = res.data
    base.value = {
      userId: d.userId,
      username: d.username,
      avatarUrl: d.userAvatar,
      fans: d.fans,
      followings: d.followings,
      privateUser: d.privateUser,
      introduction: d.introduction
    }
    followed.value = isSelfView.value
      ? null
      : typeof d.followedByMe === 'boolean'
      ? d.followedByMe
      : false
  } else {
    followed.value = null
  }
}

const fetchLikes = async () => {
  try {
    // 他人主页：读取对方收藏；自己主页：读取自己收藏
    const id = userIdFromRoute.value
    if (id != null && !isSelfView.value) {
      const rs: any = await getFavoriteSongsByUser(id, { pageNum: 1, pageSize: 100 })
      likedSongs.value = rs?.data?.items || []
    } else {
      const rs: any = await getFavoriteSongs({ pageNum: 1, pageSize: 100 })
      likedSongs.value = rs?.data?.items || []
    }
  } catch {}
  try {
    const ra = await getFavoriteAlbums({ pageNum: 1, pageSize: 100 })
    if (ra?.code === 0 && ra.data) likedAlbums.value = (ra.data as any).items || []
  } catch {}
  // 他人主页：补充对方收藏专辑
  try {
    const id = userIdFromRoute.value
    if (id != null && !isSelfView.value) {
      const res: any = await getFavoriteAlbumsByUser({ userId: id, pageNum: 1, pageSize: 100 })
      if (res?.code === 0 && res.data) likedAlbums.value = (res.data as any).items || []
    }
  } catch {}
}

watch(
  () => route.params.id,
  () => {
    fetchBase()
    fetchLikes()
    fetchRecent()
    // 若对方为私密用户，直接清空并返回
    if (base.value.privateUser && !isSelfView.value) {
      likedSongs.value = []
      likedAlbums.value = []
      favoritePlaylists.value = []
      myPlaylists.value = []
      return
    }
    // 收藏的歌单
    const id = userIdFromRoute.value
    if (id != null && !isSelfView.value) {
      getFavoritePlaylistsByUser(id, { pageNum: 1, pageSize: 60 }).then((res: any) => {
        if (res.code === 0 && res.data) {
          const items = (res.data.items || []) as any[]
          favoritePlaylists.value = items.map(it => ({
            playlistId: it.playlistId || it.id,
            title: it.title,
            coverUrl: it.coverUrl
          }))
        } else favoritePlaylists.value = []
      })
    } else {
      getFavoritePlaylists({ pageNum: 1, pageSize: 60 }).then((res: any) => {
        if (res.code === 0 && res.data) {
          const items = (res.data.items || []) as any[]
          favoritePlaylists.value = items.map(it => ({
            playlistId: it.playlistId || it.id,
            title: it.title,
            coverUrl: it.coverUrl
          }))
        } else favoritePlaylists.value = []
      })
    }
    // 创建的歌单
    if (isSelfView.value) {
      getMyPlaylists({ pageNum: 1, pageSize: 60 }).then((res: any) => {
        if (res.code === 0 && res.data) {
          const items = (res.data.items || []) as any[]
          myPlaylists.value = items.map(it => ({
            playlistId: it.playlistId || it.id,
            title: it.title,
            coverUrl: it.coverUrl
          }))
        } else myPlaylists.value = []
      })
    } else if (userIdFromRoute.value != null) {
      getUserPlaylistsByUser(userIdFromRoute.value as number, {
        pageNum: 1,
        pageSize: 60
      }).then((res: any) => {
        if (res.code === 0 && res.data) {
          const items = (res.data.items || []) as any[]
          myPlaylists.value = items.map(it => ({
            playlistId: it.playlistId || it.id,
            title: it.title,
            coverUrl: it.coverUrl
          }))
        } else myPlaylists.value = []
      })
    } else myPlaylists.value = []
  },
  { immediate: true }
)

const goBack = () => router.back()

// 关注/取消关注
const toggleFollowUser = async () => {
  const id = userIdFromRoute.value
  if (id == null || isSelfView.value) return
  try {
    if (!followed.value) {
      const res = await followUser(id)
      if (res.code === 0) {
        followed.value = true
        // 刷新资料
        const prof = await getUserProfile(id)
        if (prof.code === 0 && prof.data) {
          const d: any = prof.data
          base.value.fans = d.fans
          base.value.followings = d.followings
        }
      }
    } else {
      const res = await unfollowUser(id)
      if (res.code === 0) {
        followed.value = false
        const prof = await getUserProfile(id)
        if (prof.code === 0 && prof.data) {
          const d: any = prof.data
          base.value.fans = d.fans
          base.value.followings = d.followings
        }
      }
    }
  } catch {}
}

// 概览与可视化数据
const likeCount = computed(() => likedSongs.value?.length || 0)
const favPlaylistCount = computed(() => favoritePlaylists.value?.length || 0)
const myPlaylistCount = computed(() => myPlaylists.value?.length || 0)
// 近7天播放曲线
const playTrend7 = computed<number[]>(() => {
  const days = Array.from({ length: 7 }, () => 0)
  const now = Date.now()
  const startMs = now - 6 * 24 * 60 * 60 * 1000
  recentItems.value.forEach((it: any) => {
    const t = new Date(it.createTime || '').getTime()
    if (!Number.isFinite(t) || t < startMs || t > now) return
    const diffDay = Math.floor(
      (new Date(new Date(t).toDateString()).getTime() -
        new Date(new Date(startMs).toDateString()).getTime()) /
        (24 * 60 * 60 * 1000)
    )
    const idx = Math.min(6, Math.max(0, diffDay))
    days[idx]++
  })
  return days
})

// 根据近7天最大值动态缩放到视图高度（24px），避免超出被裁剪出现“断裂”
const trendScale = computed(() => {
  const arr = playTrend7.value || []
  const max = Math.max(1, ...arr)
  const H = 24
  // 留出 2px 顶部边距，避免触顶被裁剪
  return (H - 2) / max
})

// 常听风格 Top3
const topTags = computed(() => {
  const cnt: Record<string, number> = {}
  recentItems.value.forEach((it: any) => {
    const s = (it.style || '').trim()
    if (!s) return
    cnt[s] = (cnt[s] || 0) + 1
  })
  return Object.entries(cnt)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3)
    .map(([k]) => k)
})

// 我的歌手榜 Top3
const topArtists = computed(() => {
  const map: Record<
    string,
    { artistId: number; artistName: string; artistAvatar?: string; count: number }
  > = {}
  recentItems.value.forEach((it: any) => {
    const id = Number(it.artistId)
    const name = (it.artistName || '').trim()
    if (!Number.isFinite(id) || !name) return
    const key = String(id)
    if (!map[key])
      map[key] = {
        artistId: id,
        artistName: name,
        artistAvatar: it.artistAvatar || '',
        count: 0
      }
    map[key].count++
  })
  return Object.values(map).sort((a, b) => b.count - a.count).slice(0, 3)
})

// 歌手榜（最多展示12个，用于宫格展示）
const topArtistsGrid = computed(() => {
  const map: Record<string, { artistId: number; artistName: string; artistAvatar?: string; count: number }> = {}
  recentItems.value.forEach((it: any) => {
    const id = Number(it.artistId)
    const name = (it.artistName || '').trim()
    if (!Number.isFinite(id) || !name) return
    const key = String(id)
    if (!map[key]) map[key] = { artistId: id, artistName: name, artistAvatar: it.artistAvatar || '', count: 0 }
    map[key].count++
  })
  return Object.values(map).sort((a, b) => b.count - a.count).slice(0, 12)
})

// 听歌时段分布（24小时）
const hourDistribution = computed<number[]>(() => {
  const hours = Array.from({ length: 24 }, () => 0)
  recentItems.value.forEach((it: any) => {
    const t = new Date(it.createTime || '').getTime()
    if (!Number.isFinite(t)) return
    const h = new Date(t).getHours()
    hours[h]++
  })
  return hours
})

// 四段分布：夜(0-6) / 早(6-12) / 午(12-18) / 晚(18-24)
const daySegments = computed(() => {
  const h = hourDistribution.value
  const seg = [
    { key: '夜', value: h.slice(0, 6).reduce((a,b)=>a+b,0) },
    { key: '晨', value: h.slice(6, 12).reduce((a,b)=>a+b,0) },
    { key: '午', value: h.slice(12, 18).reduce((a,b)=>a+b,0) },
    { key: '晚', value: h.slice(18, 24).reduce((a,b)=>a+b,0) },
  ]
  const total = Math.max(1, seg.reduce((a,b)=>a + b.value, 0))
  // 生成扇形图用的 conic-gradient 字符串
  const colors = ['#60a5fa', '#34d399', '#fbbf24', '#f87171']
  let acc = 0
  const parts: string[] = []
  seg.forEach((s, i) => {
    const start = (acc / total) * 360
    acc += s.value
    const end = (acc / total) * 360
    parts.push(`${colors[i]} ${start}deg ${end}deg`)
  })
  return {
    data: seg,
    total,
    gradient: `conic-gradient(${parts.join(',')})`,
    colors
  }
})

// 最近播放 Top3（紧凑列表）
const recentTop10 = computed<any[]>(() => {
  const arr = [...recentItems.value]
  arr.sort((a: any, b: any) => new Date(b.createTime || 0).getTime() - new Date(a.createTime || 0).getTime())
  return arr.slice(0, 3)
})

// 迷你播放：点击最近播放条目立即播放该首
const audio = AudioStore()
const { loadTrack, play } = useAudioPlayer()
const miniPlay = async (row: any) => {
  const track = {
    id: String(row.songId),
    title: row.songName || '',
    artist: row.artistName || '',
    album: row.album || '',
    cover: row.coverUrl || '',
    url: row.audioUrl || '',
    duration: Number(row.duration || 0),
    likeStatus: row.likeStatus || 0
  }
  audio.setAudioStore('trackList', [track])
  audio.setAudioStore('currentSongIndex', 0)
  await loadTrack()
  play()
}

// 显示 mm:ss
const formatSec = (s: number) => {
  const sec = Math.max(0, Math.floor(Number(s) || 0))
  const m = Math.floor(sec / 60)
  const r = sec % 60
  return `${m.toString().padStart(2,'0')}:${r.toString().padStart(2,'0')}`
}

const goTag = (tag: string) => {
  router.push({ path: '/playlist', query: { tag } })
}
</script>

<template>
  <div class="flex flex-col h-full flex-1 md:overflow-hidden">
    <div class="px-6 pt-4">
      <button
        @click="goBack"
        class="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
      >
        <icon-akar-icons:arrow-left />
        返回
      </button>
    </div>

    <!-- 顶部基本信息（紧凑 + 可折叠） -->
    <div class="flex flex-col md:flex-row p-4 gap-3 items-start">
      <!-- 头像 + 文本信息（缩小） -->
      <div class="flex-shrink-0 w-20 h-20 rounded-full overflow-hidden bg-muted">
        <img :src="base.avatarUrl || coverImg" alt="avatar" class="w-full h-full object-cover" />
      </div>

      <div class="min-w-[220px]">
        <div class="flex items-center gap-2">
          <h1 class="text-2xl font-bold leading-none">{{ base.username || '用户' }}</h1>
        </div>
        <div class="mt-1 text-xs text-muted-foreground flex items-center gap-2">
          <button class="underline decoration-dotted hover:text-foreground" @click="openFollowDrawer('followers')">粉丝 {{ base.fans ?? 0 }}</button>
          <span>•</span>
          <button class="underline decoration-dotted hover:text-foreground" @click="openFollowDrawer('following')">关注 {{ base.followings ?? 0 }}</button>
        </div>
        <div class="mt-1">
          <el-button
            v-if="!isSelfView"
            size="small"
            :type="followed ? 'success' : 'primary'"
            @click="toggleFollowUser"
            class="rounded-full px-3 py-0 h-7"
          >
            {{ followed ? '已关注' : '关注' }}
          </el-button>
        </div>
        <div v-if="base.introduction"
             class="mt-1 text-xs text-muted-foreground line-clamp-2 max-w-[420px] break-words overflow-hidden"
             :title="base.introduction">
          “{{ base.introduction }}”
        </div>
      </div>

      <!-- 右侧概览：一行紧凑卡片 -->
      <div class="flex-1 w-full md:w-auto md:flex md:flex-col gap-2">
        <!-- 关键信息（常驻，单行，占用最小） -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
          <div class="rounded-md border bg-card p-2 shadow-sm">
            <div class="text-[11px] text-muted-foreground">喜欢的歌曲</div>
            <div class="text-lg font-semibold mt-0.5 leading-none">{{ likeCount }}</div>
          </div>
          <div class="rounded-md border bg-card p-2 shadow-sm">
            <div class="text-[11px] text-muted-foreground">收藏的歌单</div>
            <div class="text-lg font-semibold mt-0.5 leading-none">{{ favPlaylistCount }}</div>
          </div>
          <div class="rounded-md border bg-card p-2 shadow-sm">
            <div class="text-[11px] text-muted-foreground">创建的歌单</div>
            <div class="text-lg font-semibold mt-0.5 leading-none">{{ myPlaylistCount }}</div>
          </div>
          <div class="rounded-md border bg-card p-2 shadow-sm">
            <div class="text-[11px] text-muted-foreground">近7天播放</div>
            <div class="text-lg font-semibold mt-0.5 leading-none">
              {{ playTrend7.reduce((a, b) => a + b, 0) }}
            </div>
          </div>
        </div>

        <!-- 折叠内容将移到下方全宽区域 -->

        <!-- 展开/收起按钮（极小高度） -->
        <div class="flex justify-end">
          <button
            class="text-xs text-muted-foreground hover:text-foreground px-2 py-1"
            @click="showMoreOverview = !showMoreOverview"
          >
            {{ showMoreOverview ? '收起统计' : '展开统计' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 全宽：展开统计时，组件区(左)与右侧统计(右)各占一半，避免头像下方留白 -->
    <transition name="fade">
      <div v-if="showMoreOverview" class="px-6 md:grid md:grid-cols-2 gap-1.5 mt-2 max-h-[50vh] overflow-y-auto">
        <!-- 左：组件区（趋势 + 风格） -->
        <div class="space-y-1.5">
          <div class="rounded-md border bg-card p-2 shadow-sm">
            <div class="text-sm text-muted-foreground">最近收听走势（7日）</div>
            <svg viewBox="0 0 70 24" class="w-full h-8 mt-1">
              <polyline
                :points="playTrend7.map((v,i)=>{ const y=Math.max(1, 24 - Math.round(v * trendScale)); return `${i*10},${y}` }).join(' ')"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
                stroke-linecap="round"
                stroke-linejoin="round"
                vector-effect="non-scaling-stroke"
                class="text-primary"
              />
            </svg>
          </div>
          <div class="rounded-md border bg-card p-2 shadow-sm">
            <div class="text-sm text-muted-foreground mb-2">常听风格</div>
            <div class="flex flex-wrap gap-1.5">
              <button v-for="t in topTags" :key="t" class="px-2 h-6 rounded-full bg-hoverMenuBg hover:bg-activeMenuBg text-xs" @click="goTag(t)">{{ t }}</button>
              <span v-if="!topTags.length" class="text-xs text-muted-foreground">暂无统计</span>
            </div>
          </div>
          <!-- 最近播放（3）移动到左侧 -->
          <div class="rounded-md border bg-card p-2 shadow-sm">
            <div class="text-xs text-muted-foreground mb-1">最近播放（3）</div>
            <div class="flex flex-col gap-1">
              <div v-for="s in recentTop10" :key="s.songId" class="flex items-center gap-2 text-xs cursor-pointer hover:bg-hoverMenuBg rounded px-1 py-0.5" @click="miniPlay(s)">
                <img :src="s.coverUrl || ''" class="w-6 h-6 rounded object-cover bg-muted" />
                <div class="flex-1 min-w-0">
                  <div class="truncate">{{ s.songName }}</div>
                  <div class="text-[10px] text-muted-foreground truncate">{{ s.artistName }}</div>
                </div>
                <div class="text-[10px] text-muted-foreground">{{ formatSec(Number(s.duration||0)) }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 右：我的歌手榜 + 时段分布 + 最近播放(3) -->
        <div class="space-y-1.5">
          <div class="rounded-md border bg-card p-3 shadow-sm">
            <div class="text-sm text-muted-foreground">我的歌手榜</div>
            <div class="mt-1.5 grid grid-cols-4 gap-2">
              <template v-if="topArtistsGrid.length">
                <div v-for="a in topArtistsGrid" :key="a.artistId" class="flex items-center gap-2 cursor-pointer hover:bg-hoverMenuBg rounded px-2 py-1"
                     @click="router.push(`/artist/${a.artistId}`)">
                  <img :src="a.artistAvatar || ''" class="w-7 h-7 rounded-full object-cover bg-muted" />
                  <div class="min-w-0">
                    <div class="text-xs truncate max-w-[96px]">{{ a.artistName }}</div>
                    <div class="text-[10px] text-muted-foreground truncate">{{ a.count }} 次</div>
                  </div>
                </div>
              </template>
              <div v-else class="text-[12px] text-muted-foreground">暂无统计</div>
            </div>
          </div>
          <div class="rounded-md border bg-card p-4 shadow-sm">
            <div class="text-xs text-muted-foreground mb-1">听歌时段分布</div>
            <div class="flex items-center gap-3">
              <!-- 扇形图：用 conic-gradient 渲染，保持高度与最近播放对齐 -->
              <div class="w-16 h-16 rounded-full" :style="{ backgroundImage: daySegments.gradient }"></div>
              <!-- 四段柱状图：高度等比，保证与左侧最近播放卡片下边对齐 -->
              <div class="grid grid-cols-4 gap-2 items-end h-16">
                <div v-for="(s, idx) in daySegments.data" :key="s.key" class="flex flex-col items-center">
                  <div class="w-4 rounded" :style="{ height: Math.max(4, Math.round((s.value / daySegments.total) * 56)) + 'px', backgroundColor: daySegments.colors[idx] }"></div>
                  <div class="mt-1 text-[10px] text-muted-foreground">{{ s.key }}</div>
                </div>
              </div>
            </div>
          </div>
          
        </div>
      </div>
    </transition>

    <!-- 一级导航：更紧凑 -->
    <div class="px-6">
      <div class="flex items-center gap-2 text-muted-foreground w-full mb-1">
        <button
          v-for="tab in [
            { name: '我喜欢', value: 'likes' },
            { name: '收藏的歌单', value: 'favPlaylists' },
            { name: '创建的歌单', value: 'createdPlaylists' }
          ]"
          :key="tab.value"
          @click="activeTab = tab.value as any"
          :class="{ 'bg-activeMenuBg text-foreground': activeTab === (tab.value as any) }"
          class="px-3 h-8 rounded-full text-sm font-medium transition-colors hover:bg-hoverMenuBg"
        >
          {{ tab.name }}
        </button>
      </div>
    </div>

    <!-- 内容区域 -->
    <div class="flex-1 overflow-y-auto min-h-0 px-6 pb-6">
      <!-- 隐私保护：非本人且为私密用户时 -->
      <div v-if="base.privateUser && !isSelfView" class="w-full py-24 text-center text-muted-foreground">
        该用户为私密用户，无法查看个人信息
      </div>

      <template v-else>
        <!-- 我喜欢：内部 songs/albums 子 Tab -->
        <div v-if="activeTab === 'likes'">
          <div class="mb-3 flex items-center gap-2">
            <button
              class="px-3 h-8 rounded-full"
              :class="likeInnerTab === 'songs' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'"
              @click="likeInnerTab = 'songs'"
            >
              歌曲
              <span v-if="likedSongs.length" class="ml-1 text-xs opacity-80">
                ({{ likedSongs.length }})
              </span>
            </button>
            <button
              class="px-3 h-8 rounded-full"
              :class="likeInnerTab === 'albums' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'"
              @click="likeInnerTab = 'albums'"
            >
              专辑
              <span v-if="likedAlbums.length" class="ml-1 text-xs opacity-80">
                ({{ likedAlbums.length }})
              </span>
            </button>
          </div>

          <div v-if="likeInnerTab === 'songs'">
            <Table :data="likedSongs" />
          </div>
          <div v-else class="grid md:grid-cols-2 lg:grid-cols-3 gap-3">
            <div
              v-for="al in likedAlbums"
              :key="al.albumId"
              class="p-3 rounded-md hover:bg-hoverMenuBg cursor-pointer"
              @click="router.push(`/album/${al.albumId}`)"
            >
              <div class="flex items-center gap-3">
                <img :src="al.coverUrl || ''" class="w-12 h-12 rounded object-cover bg-muted" />
                <div class="text-primary font-medium">{{ al.title }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 收藏的歌单 -->
        <div v-else-if="activeTab === 'favPlaylists'" class="grid md:grid-cols-2 lg:grid-cols-3 gap-3">
          <template v-if="favoritePlaylists.length">
            <div
              v-for="pl in favoritePlaylists"
              :key="pl.playlistId"
              class="p-3 rounded-md hover:bg-hoverMenuBg cursor-pointer"
              @click="router.push(`/playlist/${pl.playlistId}`)"
            >
              <div class="flex items-center gap-3">
                <img :src="pl.coverUrl || ''" class="w-12 h-12 rounded object-cover bg-muted" />
                <div class="text-primary font-medium">{{ pl.title }}</div>
              </div>
            </div>
          </template>
          <div v-else class="w-full py-16 text-center text-muted-foreground">暂无收藏的歌单</div>
        </div>

        <!-- 创建的歌单（仅本人视图展示我的歌单） -->
        <div v-else class="grid md:grid-cols-2 lg:grid-cols-3 gap-3">
          <template v-if="myPlaylists.length">
            <div
              v-for="pl in myPlaylists"
              :key="pl.playlistId"
              class="p-3 rounded-md hover:bg-hoverMenuBg cursor-pointer"
              @click="router.push(`/playlist/${pl.playlistId}`)"
            >
              <div class="flex items-center gap-3">
                <img :src="pl.coverUrl || ''" class="w-12 h-12 rounded object-cover bg-muted" />
                <div class="text-primary font-medium">{{ pl.title }}</div>
              </div>
            </div>
          </template>
          <div v-else class="w-full py-16 text-center text-muted-foreground">暂无创建的歌单</div>
        </div>
      </template>
    </div>

    <!-- 关注/粉丝 抽屉 -->
    <el-drawer v-model="drawerOpen" :title="drawerTab==='following' ? '我关注的人' : '关注我的人'" size="360px" direction="rtl">
      <div v-loading="drawerLoading">
        <div class="flex items-center gap-2 mb-3">
          <button class="px-2 h-7 rounded-full" :class="drawerTab==='following' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'" @click="switchDrawerTab('following')">我关注</button>
          <button class="px-2 h-7 rounded-full" :class="drawerTab==='followers' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'" @click="switchDrawerTab('followers')">粉丝</button>
        </div>
        <div class="space-y-2" v-if="drawerTab==='following'">
          <div v-for="u in followingList" :key="u.userId" class="flex items-center gap-2 cursor-pointer hover:bg-hoverMenuBg rounded px-2 py-1" @click="router.push(`/profile/${u.userId}`)">
            <img :src="u.avatarUrl || ''" class="w-8 h-8 rounded-full object-cover bg-muted" />
            <div class="text-sm">{{ u.username }}</div>
          </div>
        </div>
        <div class="space-y-2" v-else>
          <div v-for="u in followersList" :key="u.userId" class="flex items-center gap-2 cursor-pointer hover:bg-hoverMenuBg rounded px-2 py-1" @click="router.push(`/profile/${u.userId}`)">
            <img :src="u.avatarUrl || ''" class="w-8 h-8 rounded-full object-cover bg-muted" />
            <div class="text-sm">{{ u.username }}</div>
          </div>
        </div>
      </div>
    </el-drawer>

    <BackTop />
  </div>
</template>

<style scoped>
/* 轻微的淡入动画，用于折叠区展开 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
