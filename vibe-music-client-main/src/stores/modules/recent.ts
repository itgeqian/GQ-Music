import { defineStore } from 'pinia'
import piniaPersistConfig from '@/stores/helper/persist'
import type { trackModel } from '@/stores/interface'

interface RecentState {
  list: trackModel[]
  limit: number
}

export const useRecentStore = defineStore({
  id: 'RecentStore',
  state: (): RecentState => ({ list: [], limit: 200 }),
  actions: {
    add(track: trackModel) {
      if (!track?.id) return
      // 去重：若已存在则先移除
      this.list = this.list.filter((t) => t.id !== track.id)
      // 头部插入
      this.list.unshift(track)
      // 截断到上限
      if (this.list.length > this.limit) {
        this.list.length = this.limit
      }
    },
    clear() {
      this.list = []
    },
  },
  persist: piniaPersistConfig('RecentStore'),
})


