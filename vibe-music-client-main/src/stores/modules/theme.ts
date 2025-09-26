import { defineStore } from 'pinia'
import piniaPersistConfig from '@/stores/helper/persist'
import { ThemeState } from '@/stores/interface'
/**
 * 主题设置
 */
export const themeStore = defineStore({
  id: 'themeStore',
  state: (): ThemeState => ({
    isDark: false,
    primary: '#7E22CE',
    backgroundUrl: '',
    videoUrl: '',
    themeType: 'official' as 'official' | 'custom',
    themeId: undefined as number | undefined,
    blurStrength: 0,
    brightness: 100,
  }),
  actions: {
    setDark(isDark: string | number | boolean) {
      this.isDark = isDark
    },
    setPrimary(primary: string) {
      this.primary = primary
    },
    applyOfficialTheme(themeId: number, url?: string) {
      this.themeType = 'official'
      this.themeId = themeId
      if (url) {
        if (url.endsWith('.mp4') || url.endsWith('.webm') || url.startsWith('video:')) {
          this.videoUrl = url
        } else {
          this.backgroundUrl = url
          this.videoUrl = ''
        }
      }
    },
    applyCustomTheme(url: string) {
      this.themeType = 'custom'
      this.themeId = undefined
      if (url.endsWith('.mp4') || url.endsWith('.webm') || url.startsWith('video:')) {
        this.videoUrl = url
      } else {
        this.backgroundUrl = url
        this.videoUrl = ''
      }
    },
    setBlurStrength(px: number) {
      this.blurStrength = Math.max(0, Math.min(20, px))
    },
    setBrightness(percent: number) {
      // 限制 50% - 150%
      this.brightness = Math.max(50, Math.min(150, Math.round(percent)))
    },
  },
  persist: piniaPersistConfig('ThemeStore'),
})
