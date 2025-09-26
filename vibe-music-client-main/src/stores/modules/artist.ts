import { defineStore } from 'pinia'
import type { Song } from '@/api/interface'

interface ArtistInfo {
  artistId: number
  artistName: string
  avatar: string
  birth: string
  area: string
  introduction: string
  /** 歌手详情（生平经历等） */
  detail?: string
  songs: Song[]
}

export const useArtistStore = defineStore('ArtistStore', {
  state: () => ({
    artistInfo: null as ArtistInfo | null,
  }),
  actions: {
    setArtistInfo(info: ArtistInfo) {
      this.artistInfo = info
    },
  },
}) 