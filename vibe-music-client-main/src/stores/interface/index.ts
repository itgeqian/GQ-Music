// import { ParsedLyrics } from '@/utils/parsedLyrics'
/* UserState */
export interface UserState {
  userInfo: Partial<userModel> // 用户信息
  isLoggedIn: boolean // 是否登录
}
// user
export interface userModel {
  avatarUrl: string // 头像
  username: string // 用户名
  userId: number // 用户id
  token?: string // 用户token
}

/* AudioState*/
export interface AudioState {
  trackList: trackModel[] // 歌曲缓存
  currentSongIndex: number // 当前播放歌曲索引
  currentPageSongs: [] // 当前页面的歌曲列表
  volume: number // 音量
  quality: string // 音质
  nextInsertIndex?: number // 下次插入到播放列的位置（可选，持久化）
}
export interface trackModel {
  id: string // 歌曲id
  title: string // 歌曲名
  artist: string // 艺术家
  album: string // 专辑
  cover: string // 封面
  url: string // 音频地址
  duration: number // 时长
  likeStatus: number
//   lyrics?: ParsedLyrics // 歌词
}

/* MenuState */
export interface MenuState {
  menuIndex: string // 当前菜单索引
}

/* SettingState */
export interface SettingState {
  isDrawerCover: boolean // 是否覆盖抽屉
  isOriginalParsed: boolean // 是否解析原文
  isRomaParsed: boolean // 是否解析罗马音
  isTranslatedParsed: boolean // 是否解析翻译
  language: string | null // 当前系统语言
}

/* ThemeState */
export interface ThemeState {
  isDark: boolean // 是否暗黑模式
  primary: string // 主题色
  backgroundUrl?: string
  videoUrl?: string
  themeType?: 'official' | 'custom'
  themeId?: number
  blurStrength?: number // 背景模糊强度(px)
  brightness?: number // 背景亮度(%) 50-150，默认100%
}
