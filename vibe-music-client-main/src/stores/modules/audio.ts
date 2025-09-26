import { defineStore } from 'pinia'
import piniaPersistConfig from '@/stores/helper/persist'
import { trackListData } from '@/mock'
import { AudioState, trackModel } from '@/stores/interface'
import { Song } from '@/api/interface'
/**
 * 音频
 */
export const AudioStore = defineStore({
  id: 'AudioStore',
  state: (): AudioState => ({
    // 歌曲缓存
    trackList: trackListData,
    // 当前播放歌曲索引
    currentSongIndex: 0,
    // 音量
    volume: 50,
    // 音质
    quality: 'exhigh',
    currentPageSongs: [], // 当前页面的歌曲列表
    nextInsertIndex: undefined,
  }),
  actions: {
    //set AudioStore
    setAudioStore<T extends keyof AudioState>(key: T, value: AudioState[T]) {
      this[key] = value
    },
    // 将歌曲插入到当前播放歌曲的下一位；若多次调用，按顺序依次排在上次插入的后一位
    insertNext(track: trackModel) {
      if (!track) return
      const existingIndex = this.trackList.findIndex(t => t.id === track.id)
      if (existingIndex !== -1) {
        // 如果已存在，先移除再插入
        this.trackList.splice(existingIndex, 1)
      }
      const baseIndex = typeof this.nextInsertIndex === 'number'
        ? this.nextInsertIndex
        : (this.currentSongIndex + 1)
      const insertIndex = Math.min(Math.max(baseIndex, 0), this.trackList.length)
      this.trackList.splice(insertIndex, 0, track)
      // 下次插入位置递增
      this.nextInsertIndex = insertIndex + 1
    },
    // 当播放列表切歌/重放时，重置插入游标到当前曲目的下一位
    resetNextInsertIndex() {
      this.nextInsertIndex = this.currentSongIndex + 1
    },
    // 新增歌曲或歌曲数组到 trackList
    addTracks(newTracks: trackModel | trackModel[]) {
      // 收集现有歌曲的ID
      const existingIds = new Set(
        this.trackList.map((track: { id: any }) => track.id)
      )
      // 将参数归一化为数组
      const tracksToAdd = Array.isArray(newTracks) ? newTracks : [newTracks]
      for (const track of tracksToAdd) {
        if (existingIds.has(track.id)) {
          this.currentSongIndex = this.trackList.findIndex(
            (existingTrack: { id: string }) => existingTrack.id === track.id
          )
          break
        } else {
          this.trackList.push(track)
          this.currentSongIndex = this.trackList.length - 1
        }
      }
    },
    // 删除指定歌曲
    deleteTrack(id: number | string) {
      this.trackList = this.trackList.filter(
        (track: { id: string | number }) => track.id !== id
      )
    },
    // 设置当前页面的歌曲列表
    setCurrentPageSongs(songs: Song[]) {
      this.currentPageSongs = songs
    }
  },
  persist: piniaPersistConfig('AudioStore'),
})
