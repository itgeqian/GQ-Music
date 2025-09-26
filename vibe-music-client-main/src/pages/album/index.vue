<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import Table from '@/components/Table.vue'
import type { Song } from '@/api/interface'
import { http } from '@/utils/http'
import coverImg from '@/assets/cover.png'

const route = useRoute()
const router = useRouter()

// 由路由 query 提供 artistId 或 artistName + album 名称
const artistId = computed(() => Number(route.query.artistId) || undefined)
const artistName = computed(() => (route.query.artistName as string) || undefined)
const albumTitle = computed(() => (route.query.album as string) || '')

// 轻量实现：复用歌曲查询接口，按 artist/album 过滤
const songs = ref<Song[]>([])
const loading = ref(false)
const albumCover = ref<string>(coverImg)

const fetchSongsByAlbum = async () => {
  loading.value = true
  try {
    // 复用后端 /song/getAllSongs 接口（关键字过滤）
    const res: any = await http('post', '/song/getAllSongs', {
      data: {
        pageNum: 1,
        pageSize: 200,
        songName: '',
        artistName: artistName.value || '',
        album: albumTitle.value || ''
      },
    })
    const items: Song[] = res?.data?.items || []
    // 如果通过 artistId 进入，进一步在前端过滤 artistName 不可靠时
    const filtered = artistId.value
      ? items.filter(it => it.artistName && it.album === albumTitle.value)
      : items
    songs.value = filtered
    if (filtered.length > 0) {
      albumCover.value = filtered[0].coverUrl || coverImg
    } else {
      albumCover.value = coverImg
    }
  } finally {
    loading.value = false
  }
}

watch(() => route.fullPath, fetchSongsByAlbum, { immediate: true })

const backToArtist = () => {
  if (artistId.value) router.push(`/artist/${artistId.value}`)
}
</script>

<template>
  <div class="flex flex-col h-full bg-background flex-1 md:overflow-hidden">
    <div class="flex flex-col md:flex-row p-6 gap-6 items-center">
      <div class="flex-shrink-0 w-48 h-48">
        <img :alt="albumTitle" class="w-full h-full object-cover rounded-lg shadow-lg"
          :src="albumCover + '?param=500y500'" />
      </div>
      <div class="flex flex-col justify-center flex-1">
        <h1 class="text-2xl font-bold mb-2">{{ albumTitle }}</h1>
        <div class="text-sm text-muted-foreground">
          <span v-if="artistName">歌手：
            <a class="text-primary cursor-pointer hover:underline" @click="backToArtist" v-if="artistId">{{ artistName }}</a>
            <span v-else>{{ artistName }}</span>
          </span>
          <span class="ml-4">曲目：{{ songs.length }}</span>
        </div>
      </div>
    </div>

    <div class="px-6" v-loading="loading">
      <Table :data="songs" :clickableAlbum="false" />
    </div>
  </div>
  
</template>

