<script setup lang="ts">
import { getArtistDetail, followArtist, cancelFollowArtist, isArtistFollowed, getAlbumsByArtist, getSongsByAlbumId } from '@/api/system'
import Table from '@/components/Table.vue'
import BackTop from '@/components/BackTop.vue'
import { useArtistStore } from '@/stores/modules/artist'
import { ElMessage } from 'element-plus'
import { UserStore } from '@/stores/modules/user'
import { useRoute, useRouter } from 'vue-router'
import { getAllSongs } from '@/api/system'
import defaultAvatar from '@/assets/user.jpg'
import defaultAlbumCover from '@/assets/default_album.jpg'

interface ArtistDetailResponse {
    artistId: number
    artistName: string
    avatar: string
    birth: string
    area: string
    introduction: string
    detail?: string
    songs: any[]
}

const route = useRoute()
const router = useRouter()
// 仅当当前路由确实是歌手详情时才发请求，避免在其它页面（如 /album/:id）误触发
const isArtistRoute = computed(() => route.path.startsWith('/artist/'))
const goBack = () => router.back()
const artistStore = useArtistStore()
// 歌手数据
const artistInfo = computed(() => artistStore.artistInfo)

// 分页与专辑总数（需在使用前声明，避免 TDZ 报错）
const pageNum = ref(1)
const pageSize = ref(50)
const totalSongs = ref(0)
const pageSongs = ref<any[]>([])
const albumTotalCount = ref<number>(0)

// 拉取专辑总数（独立于歌曲分页）
const fetchAlbumTotal = async (artistId: number) => {
    try {
        const resp: any = await getAlbumsByArtist({ artistId, pageNum: 1, pageSize: 1 })
        if (resp?.code === 0 && resp.data) {
            albumTotalCount.value = Number(resp.data.total) || 0
        } else {
            albumTotalCount.value = 0
        }
    } catch {
        albumTotalCount.value = 0
    }
}

// 解析路由中的 artistId（容错：undefined/数组/非数字）
const getRouteArtistId = (): number | null => {
    const idParam: any = route.params.id
    const raw = Array.isArray(idParam) ? idParam[0] : idParam
    const num = Number(raw)
    return Number.isFinite(num) ? num : null
}

const fetchArtistDetail = async (idFromRoute?: number) => {
    const numericId = idFromRoute ?? getRouteArtistId()
    if (numericId == null) {
        console.warn('无效的歌手ID，跳过请求')
        return
    }
    if (!isArtistRoute.value) return

    try {
        const res = await getArtistDetail(numericId)

        if (res.code === 0 && res.data) {
            const artistData = res.data as ArtistDetailResponse
            artistStore.setArtistInfo({
                artistId: artistData.artistId,
                artistName: artistData.artistName || '未知歌手',
                avatar: artistData.avatar || '',
                birth: artistData.birth || '',
                area: artistData.area || '未知',
                introduction: artistData.introduction || '暂无简介',
                detail: artistData.detail || '',
                songs: []
            })
            // 首次加载第一页歌曲
            pageNum.value = 1
            await fetchSongsByArtist()
        } else {
            ElMessage.error(res.message || '获取歌手信息失败')
        }
    } catch (error) {
        console.error('获取歌手详情失败:', error)
        ElMessage.error('获取歌手信息失败，请稍后重试')
    }
}

watch(
    () => route.params.id,
    () => {
        if (!isArtistRoute.value) return
        const id = getRouteArtistId()
        if (id != null) { fetchArtistDetail(id); checkFollowing(id); fetchAlbumTotal(id); fetchAlbumListAll(id) }
    },
    { immediate: true }
)

// 格式化生日
const formatBirth = (birth: string) => {
    if (!birth) return ''
    return new Date(birth).toLocaleDateString()
}

// tabs
const activeTab = ref<'songs' | 'albums' | 'about'>('songs')

// 关注按钮（接入后端）
const following = ref(false)
async function checkFollowing(artistId: number) {
    try {
        const res = await isArtistFollowed(artistId)
        if (res.code === 0 && typeof res.data === 'boolean') following.value = res.data
    } catch {}
}
const toggleFollow = async () => {
    const id = getRouteArtistId()
    if (id == null) return
    // 未登录只提示，不发请求
    const userStore = UserStore()
    if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录'); try { window.dispatchEvent(new CustomEvent('auth:show')) } catch {}
        return
    }
    try {
        if (!following.value) {
            const res = await followArtist(id)
            if (res.code === 0) { following.value = true; ElMessage.success('已关注') }
            else ElMessage.error(res.message || '关注失败')
        } else {
            const res = await cancelFollowArtist(id)
            if (res.code === 0) { following.value = false; ElMessage.success('已取消关注') }
            else ElMessage.error(res.message || '取消关注失败')
        }
    } catch (e:any) { ElMessage.error(e?.message || '操作失败') }
}

async function fetchSongsByArtist() {
    const name = artistInfo.value?.artistName || ''
    if (!name) return
    const res: any = await getAllSongs({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        songName: debouncedSearch.value || '',
        artistName: name,
        album: ''
    })
    if (res?.code === 0 && res.data) {
        pageSongs.value = res.data.items || []
        totalSongs.value = res.data.total || pageSongs.value.length
    }
}

// 专辑列表：改为独立请求专辑分页，避免与歌曲分页耦合
const albumList = ref<Array<{ albumId: number; title: string; coverUrl?: string }>>([])
const albumSongsMap = ref<Record<number, any[]>>({})
const albumLoadingMap = ref<Record<number, boolean>>({})

async function fetchAlbumListAll(artistId: number) {
    try {
        // pageSize 使用专辑总数，若未知则先给一个较大的兜底值
        const size = albumTotalCount.value && albumTotalCount.value > 0 ? albumTotalCount.value : 100
        const resp: any = await getAlbumsByArtist({ artistId, pageNum: 1, pageSize: size })
        if (resp?.code === 0 && resp.data && Array.isArray(resp.data.items)) {
            albumList.value = resp.data.items.map((it: any) => ({ albumId: it.albumId, title: it.title, coverUrl: it.coverUrl }))
            // 默认全部收起
            const init: Record<string, boolean> = {}
            albumList.value.forEach((_, idx) => { init[String(idx)] = true })
            collapsedAlbums.value = init
        } else {
            albumList.value = []
        }
    } catch {
        albumList.value = []
    }
}

// 专辑折叠开关：key 使用索引，避免老数据无 albumId 时冲突
const collapsedAlbums = ref<Record<string, boolean>>({})
const isAlbumCollapsed = (idx: number) => !!collapsedAlbums.value[String(idx)]
const toggleAlbumCollapse = async (idx: number) => {
    const k = String(idx)
    const willExpand = collapsedAlbums.value[k] === true
    collapsedAlbums.value[k] = !collapsedAlbums.value[k]

    // 懒加载：首次展开时拉取该专辑下的歌曲
    if (willExpand === true) {
        const album = albumList.value[idx]
        if (album && album.albumId && !albumSongsMap.value[album.albumId]) {
            try {
                albumLoadingMap.value[album.albumId] = true
                const resp: any = await getSongsByAlbumId({ albumId: album.albumId, pageNum: 1, pageSize: 500 })
                if (resp?.code === 0 && resp.data && Array.isArray(resp.data.items)) {
                    albumSongsMap.value[album.albumId] = resp.data.items
                } else {
                    albumSongsMap.value[album.albumId] = []
                }
            } catch {
                albumSongsMap.value[album.albumId] = []
            } finally {
                albumLoadingMap.value[album.albumId] = false
            }
        }
    }
}

// 数量（歌曲使用后端 total；专辑使用后端 total，避免受歌曲分页影响）
const songCount = computed(() => totalSongs.value || pageSongs.value.length)
const albumCount = computed(() => albumTotalCount.value)

// 搜索与排序（仅影响“歌曲”Tab）
const songSearch = ref('')
const debouncedSearch = ref('')
let searchTimer: any = null
watch(songSearch, (val) => {
    if (searchTimer) clearTimeout(searchTimer)
    searchTimer = setTimeout(() => { debouncedSearch.value = val }, 150)
}, { immediate: true })
watch(debouncedSearch, () => { pageNum.value = 1; fetchSongsByArtist() })
type SortKey = 'default' | 'title' | 'artist' | 'album'
const sortKey = ref<SortKey>('default')
const filteredSortedSongs = computed(() => {
    const kw = debouncedSearch.value.trim().toLowerCase()
    let list = pageSongs.value
    if (kw) {
        list = list.filter((s: any) =>
            (s.songName || '').toLowerCase().includes(kw) ||
            (s.artistName || '').toLowerCase().includes(kw) ||
            (s.album || '').toLowerCase().includes(kw)
        )
    }
    const arr = [...list]
    const cmp = (a: any, b: any, field: string) => String(a?.[field] || '').localeCompare(String(b?.[field] || ''), 'zh')
    if (sortKey.value === 'title') arr.sort((a, b) => cmp(a, b, 'songName'))
    else if (sortKey.value === 'artist') arr.sort((a, b) => cmp(a, b, 'artistName'))
    else if (sortKey.value === 'album') arr.sort((a, b) => cmp(a, b, 'album'))
    return arr
})

// 取消定时与可见性自动刷新，避免阅读时页面跳动
</script>

<template>
    <div class="container mx-auto pt-10 pb-0 px-5 h-full flex-1 flex flex-col">
        <div class="mb-4">
            <button @click="goBack" class="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors">
                <icon-akar-icons:arrow-left />
                返回
            </button>
        </div>
        <!-- 歌手详情 -->
        <div class="flex flex-col lg:flex-row items-center gap-8">
            <div class="w-48 h-48 rounded-full overflow-hidden bg-gray-200">
                <img :src="artistInfo?.avatar || defaultAvatar" :alt="artistInfo?.artistName" class="w-full h-full object-cover" @error="($event.target as HTMLImageElement).src = defaultAvatar" />
            </div>
            <div class="text-center lg:text-left flex-1">
                <h1 class="text-3xl font-semibold text-foreground">
                    {{ artistInfo?.artistName }}
                </h1>
                <div class="mt-4 space-y-2 text-sm text-muted-foreground">
                    <p v-if="artistInfo?.birth">生日：{{ formatBirth(artistInfo.birth) }}</p>
                    <p v-if="artistInfo?.area">地区：{{ artistInfo.area }}</p>
                    <p v-if="artistInfo?.introduction" class="mt-2 line-clamp-4">简介：{{ artistInfo.introduction }}
                    </p>
                </div>
                <!-- 关注按钮：放在简介下方，圆角样式，与个人详情页一致 -->
                <div class="mt-4">
                    <el-button :type="following ? 'success' : 'primary'" size="small" @click="toggleFollow" class="rounded-full px-6">
                        {{ following ? '已关注' : '关注' }}
                    </el-button>
                </div>
            </div>
        </div>

        <!-- 顶部：搜索 + 排序（右对齐，位于导航上方） -->
        <div class="mt-0 px-0 flex items-center">
            <div class="ml-auto flex items-center gap-3">
                <div class="relative">
                    <icon-akar-icons:search class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                    <input v-model="songSearch" placeholder="搜索" class="h-9 rounded-md border bg-background pl-9 px-3 text-sm" />
                </div>
                <el-dropdown>
                    <el-button class="h-9">排序</el-button>
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

        <!-- 分类导航 -->
        <div class="mt-3 border-b border-transparent mb-3 flex items-center gap-4" style="background: transparent">
            <button class="px-3 py-2 rounded-md" :class="activeTab==='songs' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'" @click="activeTab='songs'">
                歌曲<span v-if="songCount" class="ml-1 text-xs opacity-80">({{ songCount }})</span>
            </button>
            <button class="px-3 py-2 rounded-md" :class="activeTab==='albums' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'" @click="activeTab='albums'">
                专辑<span v-if="albumCount" class="ml-1 text-xs opacity-80">({{ albumCount }})</span>
            </button>
            <button class="px-3 py-2 rounded-md" :class="activeTab==='about' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'" @click="activeTab='about'">详情</button>
        </div>

        <!-- 歌曲列表（只滚动列表区域，避免整页滚动卡顿） -->
        <div v-show="activeTab==='songs'" class="flex-1 min-h-0 flex flex-col">
            <div v-if="filteredSortedSongs && filteredSortedSongs.length > 0" class="flex-1 min-h-0 overflow-y-auto">
                <!-- 歌曲列表：禁用图片懒加载，解决从“专辑”切回“歌曲”时无滚动无法触发懒加载的问题 -->
                <Table :data="filteredSortedSongs" :clickable-artist="false" :image-lazy="false" />
            </div>
            <div v-else class="w-full py-16 text-center text-muted-foreground">暂无歌曲</div>
            <!-- 分页：与曲库页一致，贴紧播放器 -->
            <nav class="mx-auto flex w-full justify-center mt-3">
                <el-pagination
                  v-model:current-page="pageNum"
                  v-model:page-size="pageSize"
                  :total="totalSongs"
                  :page-sizes="[20,50,100]"
                  layout="total, sizes, prev, pager, next, jumper"
                  @size-change="fetchSongsByArtist"
                  @current-change="fetchSongsByArtist"
                  class="mb-1"
                />
            </nav>
        </div>

        <!-- 专辑：按 album 聚合展示 -->
        <div v-show="activeTab==='albums'" class="flex flex-col gap-6">
            <template v-if="albumList && albumList.length > 0">
                <div v-for="(al, i) in albumList" :key="al.albumId || i" class="border rounded-lg p-4">
                    <div class="mb-2 flex items-center justify-between">
                        <h3 class="text-lg font-semibold flex items-center gap-3 m-0">
                            <img :src="al.coverUrl || defaultAlbumCover" alt="cover" class="w-10 h-10 rounded object-cover" @error="($event.target as HTMLImageElement).src = defaultAlbumCover" />
                            <a class="text-primary cursor-pointer hover:underline" @click="$router.push({ path: `/album/${al.albumId ?? ''}` })">{{ al.title }}</a>
                            <span v-if="albumSongsMap[al.albumId]" class="text-xs text-muted-foreground">（{{ albumSongsMap[al.albumId].length }} 首）</span>
                        </h3>
                        <button class="flex items-center gap-1 text-sm text-primary hover:underline" @click="toggleAlbumCollapse(i)">
                            <span>{{ isAlbumCollapsed(i) ? '展开' : '收起' }}</span>
                            <span :style="{ display: 'inline-block', transform: isAlbumCollapsed(i) ? 'rotate(-90deg)' : 'rotate(0deg)', transition: 'transform .2s' }">▾</span>
                        </button>
                    </div>
                    <!-- 专辑分组：禁用图片懒加载，解决从“歌曲”切到“专辑”时 Observer 根容器变化导致不触发的问题 -->
                    <div v-if="!isAlbumCollapsed(i)">
                        <div v-if="albumLoadingMap[al.albumId]" class="text-sm text-muted-foreground py-4">加载中...</div>
                        <Table v-else :data="(albumSongsMap[al.albumId] || []).map((s:any)=>({ ...s, coverUrl: s.coverUrl || al.coverUrl || defaultAlbumCover }))" :clickable-artist="false" :image-lazy="false" />
                    </div>
                </div>
            </template>
            <div v-else class="w-full py-16 text-center text-muted-foreground">暂无专辑</div>
        </div>

        <!-- 详情：展示歌手基础信息与简介（可扩展） -->
        <div v-show="activeTab==='about'" class="space-y-3 text-sm text-muted-foreground">
            <p v-if="artistInfo?.artistName">姓名：{{ artistInfo.artistName }}</p>
            <p v-if="artistInfo?.birth">生日：{{ formatBirth(artistInfo.birth) }}</p>
            <p v-if="artistInfo?.area">地区：{{ artistInfo.area }}</p>
            <p v-if="artistInfo?.introduction">简介：</p>
            <p v-if="artistInfo?.introduction" class="whitespace-pre-line leading-7">{{ artistInfo.introduction }}</p>
            <p class="mt-2" v-if="artistInfo?.detail">详情：</p>
            <p v-if="artistInfo?.detail" class="whitespace-pre-line leading-7">{{ artistInfo.detail }}</p>
            <p v-else>暂无详情</p>
        </div>
    </div>
    <BackTop />
</template>
