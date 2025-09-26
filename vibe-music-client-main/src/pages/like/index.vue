<script setup lang="ts">
import { getFavoriteSongs, getFollowArtists, getFavoriteAlbums } from '@/api/system'
import type { Song } from '@/api/interface'
import coverImg from '@/assets/cover.png'
import { AudioStore } from '@/stores/modules/audio'
import { useRoute, useRouter } from 'vue-router'
import { useFollowStore } from '@/stores/modules/follow'

const route = useRoute()
const router = useRouter()
const audui = AudioStore()
const { loadTrack, play } = useAudioPlayer()
// 仍保留本地 followStore 以兼容其它组件（此页已使用后端数据）
useFollowStore()

const songs = ref<Song[]>([])
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

const playlist = ref({
    name: '我喜欢的音乐',
    coverImgUrl: coverImg,
    trackCount: 0,
    tags: []
})

// 选项卡
const activeTab = ref<'songs' | 'albums' | 'following'>('songs')

// 我的关注（后端拉取）
interface FollowArtist { artistId: number; artistName: string; avatar?: string }
const followArtists = ref<FollowArtist[]>([])
const followTotal = ref(0)
const followPage = ref(1)
const followPageSize = ref(30)

const fetchFollowArtists = async () => {
    const res = await getFollowArtists({ pageNum: followPage.value, pageSize: followPageSize.value })
    if (res.code === 0 && res.data) {
        const data = res.data as { items: FollowArtist[]; total: number }
        followArtists.value = data.items || []
        followTotal.value = data.total || 0
    } else {
        followArtists.value = []
        followTotal.value = 0
    }
}

interface PageResult {
    items: Song[]
    total: number
}

const getSongs = async () => {
    const res = await getFavoriteSongs({
        pageNum: currentPage.value,
        pageSize: pageSize.value,
        songName: searchKeyword.value,
        artistName: '',
        album: ''
    })
    if (res.code === 0 && res.data) {
        const pageData = res.data as PageResult
        songs.value = pageData.items
        playlist.value.trackCount = pageData.total
        // 使用第一首歌的封面作为封面图
        if (pageData.items.length > 0) {
            playlist.value.coverImgUrl = pageData.items[0].coverUrl || coverImg
        }
    }
}

const handleSearch = () => {
    currentPage.value = 1 // 搜索时重置页码
    getSongs()
}

const handlePlayAll = async () => {
    audui.setAudioStore('trackList', [])

    if (!songs.value.length) return

    const result = songs.value.map(song => ({
        id: song.songId.toString(),
        title: song.songName,
        artist: song.artistName,
        album: song.album,
        cover: song.coverUrl || coverImg,
        url: song.audioUrl,
        duration: parseFloat(song.duration) * 1000,
        likeStatus: song.likeStatus
    }))

    audui.setAudioStore('trackList', result)
    audui.setAudioStore('currentSongIndex', 0)
    await loadTrack()
    play()
}

// 搜索与排序（歌曲 Tab）
type SortKey = 'default' | 'title' | 'artist' | 'album'
const sortKey = ref<SortKey>('default')
const filteredSongs = computed(() => {
  const q = searchKeyword.value.trim().toLowerCase()
  let list = songs.value
  if (q) {
    list = list.filter((s: any) =>
      (s.songName || '').toLowerCase().includes(q) ||
      (s.artistName || '').toLowerCase().includes(q) ||
      (s.album || '').toLowerCase().includes(q)
    )
  }
  const arr = [...list]
  const cmp = (a: any, b: any, field: string) => String(a?.[field] || '').localeCompare(String(b?.[field] || ''), 'zh')
  if (sortKey.value === 'title') arr.sort((a,b)=>cmp(a,b,'songName'))
  else if (sortKey.value === 'artist') arr.sort((a,b)=>cmp(a,b,'artistName'))
  else if (sortKey.value === 'album') arr.sort((a,b)=>cmp(a,b,'album'))
  return arr
})

// 监听当前页面歌曲列表的变化
watch(() => audui.currentPageSongs, (newSongs) => {
    if (newSongs && newSongs.length > 0) {
        // 检查是否有歌曲的收藏状态变为0（取消收藏）
        const hasUnlikedSong = newSongs.some((song) => song.likeStatus === 0)
        if (hasUnlikedSong) {
            getSongs() // 重新获取收藏列表
        }
    }
}, { deep: true })

// 监听路由变化，每次进入页面时重新获取数据
watch(() => route.path, (newPath) => {
    if (newPath === '/like') {
        getSongs()
    }
})

onMounted(() => {
    getSongs()
    // 为了在 Tab 上显示数量，预取一次关注歌手的总数
    fetchFollowArtists()
    fetchFavoriteAlbums()

    const onFavAlbumChanged = () => {
        if (activeTab.value === 'albums') {
            fetchFavoriteAlbums()
        }
    }
    window.addEventListener('favorite:album:changed', onFavAlbumChanged)
    onBeforeUnmount(() => window.removeEventListener('favorite:album:changed', onFavAlbumChanged))
})

// 后端：收藏专辑列表与计数
const favoriteAlbums = ref<Array<{ albumId: number; title: string; coverUrl?: string }>>([])
const favAlbumTotal = ref(0)
const favAlbumPage = ref(1)
const favAlbumPageSize = ref(30)
const fetchFavoriteAlbums = async () => {
    const res = await getFavoriteAlbums({ pageNum: favAlbumPage.value, pageSize: favAlbumPageSize.value })
    if (res.code === 0 && res.data) {
        const data = res.data as { items: any[]; total: number }
        favoriteAlbums.value = data.items || []
        favAlbumTotal.value = data.total || 0
    } else {
        favoriteAlbums.value = []
        favAlbumTotal.value = 0
    }
}
const albumCount = computed(() => favAlbumTotal.value)
const followingCount = computed(() => followTotal.value)

// 切换到“我的关注”时加载
watch(activeTab, (tab) => {
    if (tab === 'following') fetchFollowArtists()
    if (tab === 'albums') fetchFavoriteAlbums()
})
</script>

<template>
  <div class="flex flex-col h-full flex-1 md:overflow-hidden">
    <div class="flex flex-col md:flex-row p-6 gap-6">
      <div class="flex-shrink-0 w-60 h-60">
        <img :alt="playlist.name" class="w-full h-full object-cover rounded-lg shadow-lg"
          :src="playlist.coverImgUrl + '?param=500y500'" />
      </div>
      <div class="flex flex-col justify-between flex-1">
        <div>
          <h1 class="text-3xl font-bold mb-2">{{ playlist.name }}</h1>
          <div class="flex items-center gap-2 text-sm text-muted-foreground mb-4 ml-1">
            <span v-if="activeTab==='songs'">{{ playlist.trackCount }} 首歌曲</span>
            <span v-else-if="activeTab==='albums'">{{ albumCount }} 张专辑</span>
            <span v-else>已关注 {{ followingCount }} 位歌手</span>
          </div>
          <div class="flex items-center gap-2 text-sm text-muted-foreground" v-if="playlist.tags">
            <el-tag v-for="tag in playlist.tags" class="text-sm" effect="dark" :key="tag">{{ tag }}
            </el-tag>
          </div>
        </div>
        <div class="flex items-center justify-between mt-4" v-if="activeTab==='songs'">
          <button @click="handlePlayAll"
            class="text-white inline-flex items-center justify-center gap-2 whitespace-nowrap text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 rounded-lg px-8">
            <icon-solar:play-line-duotone />
            播放全部
          </button>

          <div class="flex items-center gap-3">
            <div class="relative">
              <icon-akar-icons:search class="absolute left-3 top-1/2 transform -translate-y-1/2 text-muted-foreground" />
              <input v-model="searchKeyword" @keyup.enter="handleSearch"
                class="flex h-10 rounded-lg border border-input transform duration-300 bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-0 pl-10 w-56"
                placeholder="搜索" />
            </div>
            <el-dropdown>
              <el-button class="h-10">排序</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :class="{ 'is-active': sortKey==='default' }" @click="sortKey='default'">默认排序</el-dropdown-item>
                  <el-dropdown-item :class="{ 'is-active': sortKey==='title' }" @click="sortKey='title'">歌曲名</el-dropdown-item>
                  <el-dropdown-item :class="{ 'is-active': sortKey==='artist' }" @click="sortKey='artist'">歌手</el-dropdown-item>
                  <el-dropdown-item :class="{ 'is-active': sortKey==='album' }" @click="sortKey='album'">专辑</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>
    <!-- 分类导航（仿歌手详情页） -->
    <div class="px-6 mb-4 flex items-center gap-4 bg-transparent">
      <button class="px-3 py-2 rounded-md" :class="activeTab==='songs' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'" @click="activeTab='songs'">
        歌曲<span v-if="playlist.trackCount" class="ml-1 text-xs opacity-80">({{ playlist.trackCount }})</span>
      </button>
      <button class="px-3 py-2 rounded-md" :class="activeTab==='albums' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'" @click="activeTab='albums'">
        专辑<span v-if="albumCount" class="ml-1 text-xs opacity-80">({{ albumCount }})</span>
      </button>
      <button class="px-3 py-2 rounded-md" :class="activeTab==='following' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'" @click="activeTab='following'">
        我的关注<span v-if="followingCount" class="ml-1 text-xs opacity-80">({{ followingCount }})</span>
      </button>
    </div>

    <!-- 歌曲 -->
    <div v-if="activeTab==='songs'" class="flex-1 md:overflow-x-hidden">
      <Table :data="filteredSongs" />
      <nav class="mx-auto flex w-full justify-center mt-3">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="playlist.trackCount"
          :page-sizes="[10, 20, 30, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getSongs"
          @current-change="getSongs"
          class="mb-3"
        />
      </nav>
    </div>

    <!-- 专辑：后端收藏专辑列表 -->
    <div v-else-if="activeTab==='albums'" class="flex flex-col gap-6 px-6 flex-1 overflow-y-auto min-h-0">
      <template v-if="favoriteAlbums.length">
        <div v-for="al in favoriteAlbums" :key="al.albumId"
             class="border rounded-lg p-4 flex items-center justify-between hover:bg-hoverMenuBg cursor-pointer"
             @click="router.push(`/album/${al.albumId}`)">
          <div class="flex items-center gap-3">
            <img :src="al.coverUrl || ''" alt="cover" class="w-12 h-12 rounded object-cover bg-muted" />
            <span class="text-lg font-semibold text-primary hover:underline">{{ al.title }}</span>
          </div>
        </div>
      </template>
      <div v-else class="w-full py-16 text-center text-muted-foreground">暂无专辑</div>
    </div>

    <!-- 我的关注：头像宫格 + 悬浮显示歌手名（照搬歌手模块样式） -->
    <div v-else class="px-6 pb-6">
      <div v-if="followArtists.length" class="grid grid-cols-3 md:grid-cols-5 lg:grid-cols-6 gap-6">
        <div v-for="a in followArtists" :key="a.artistId" class="flex flex-col items-center cursor-pointer group"
             @click="router.push(`/artist/${a.artistId}`)">
          <div class="relative w-24 h-24 md:w-28 md:h-28 rounded-full overflow-hidden shadow">
            <img :src="a.avatar || ''" class="w-full h-full object-cover" />
            <div class="absolute inset-0 bg-black/0 group-hover:bg-black/40 transition" />
            <div class="absolute inset-x-0 bottom-1 text-center text-white text-xs opacity-0 group-hover:opacity-100 transition truncate px-2">
              {{ a.artistName }}
            </div>
          </div>
        </div>
      </div>
      <div v-else class="w-full py-16 text-center text-muted-foreground">还没有关注任何歌手</div>
    </div>
  </div>
</template>