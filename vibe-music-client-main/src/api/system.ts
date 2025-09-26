import { http } from '@/utils/http'

export type Result = {
  code: number
  message: string
  data?: Array<any> | number | string | object
}

export type ResultTable = {
  code: number
  message: string
  data?: {
    /** 列表数据 */
    items: Array<any>
    /** 总条目数 */
    total?: number
    /** 每页显示条目个数 */
    pageSize?: number
    /** 当前页数 */
    currentPage?: number
  }
}

/** 用户登录 */
export const login = (data: object) => {
  return http<Result>('post', '/user/login', { data })
}

/** 用户登出 */
export const logout = () => {
  return http<Result>('post', '/user/logout')
}

/** 发送邮箱验证码 */
export const sendEmailCode = (email: string) => {
  return http<Result>('get', '/user/sendVerificationCode', {
    params: { email },
  })
}

/** 用户注册 */
export const register = (data: object) => {
  return http<Result>('post', '/user/register', { data })
}

/** 重置密码 */
export const resetPassword = (data: object) => {
  return http<Result>('patch', '/user/resetUserPassword', { data })
}

/** 获取用户信息 */
export const getUserInfo = () => {
  return http<Result>('get', '/user/getUserInfo')
}

/** 更新用户信息 */
export const updateUserInfo = (data: object) => {
  return http<Result>('put', '/user/updateUserInfo', { data })
}

/** 更新用户头像 */
export const updateUserAvatar = (formData: FormData) => {
  return http<Result>('patch', '/user/updateUserAvatar', {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    data: formData,
    transformRequest: [(data) => data], // 防止 axios 处理 FormData
  })
}

/** 注销账号 */
export const deleteUser = () => {
  return http<Result>('delete', '/user/deleteAccount')
}

/** 获取图形验证码 */
export const getCaptcha = () => {
  return http<Result>('get', '/captcha/generate')
}

/** 获取轮播图 */
export const getBanner = () => {
  return http<Result>('get', '/banner/getBannerList')
}

/** 获取推荐歌单 */
export const getRecommendedPlaylists = () => {
  return http<Result>('get', '/playlist/getRecommendedPlaylists')
}

/** 获取推荐歌曲 */
export const getRecommendedSongs = () => {
  return http<Result>('get', '/song/getRecommendedSongs')
}

/** 获取所有歌曲 */
export const getAllSongs = (data: object) => {
  return http<ResultTable>('post', '/song/getAllSongs', { data })
}

/** 获取歌曲详情 */
export const getSongDetail = (id: number) => {
  return http<ResultTable>('get', `/song/getSongDetail/${id}`)
}

/** 获取所有歌手 */
export const getAllArtists = (data: object) => {
  return http<ResultTable>('post', '/artist/getAllArtists', { data })
}

/** 获取歌手详情 */
export const getArtistDetail = (id: number) => {
  return http<Result>('get', `/artist/getArtistDetail/${id}`)
}

/** 获取所有歌单 */
export const getAllPlaylists = (data: object) => {
  return http<ResultTable>('post', '/playlist/getAllPlaylists', { data })
}

/** 获取歌单详情 */
export const getPlaylistDetail = (id: number) => {
  return http<Result>('get', `/playlist/getPlaylistDetail/${id}`)
}

/** 获取用户收藏的歌曲 */
export const getFavoriteSongs = (data: object) => {
  return http<Result>('post', '/favorite/getFavoriteSongs', { data })
}

/** 收藏歌曲 */
export const collectSong = (songId: number) => {
  return http<Result>('post', '/favorite/collectSong', { params: { songId } })
}

/** 取消收藏歌曲 */
export const cancelCollectSong = (songId: number) => {
  return http<Result>('delete', '/favorite/cancelCollectSong', {
    params: { songId },
  })
}

/** 获取用户收藏的歌单 */
export const getFavoritePlaylists = (data: object) => {
  return http<Result>('post', '/favorite/getFavoritePlaylists', { data })
}

/** 收藏歌单 */
export const collectPlaylist = (playlistId: number) => {
  return http<Result>('post', '/favorite/collectPlaylist', {
    params: { playlistId },
  })
}

/** 取消收藏歌单 */
export const cancelCollectPlaylist = (playlistId: number) => {
  return http<Result>('delete', '/favorite/cancelCollectPlaylist', {
    params: { playlistId },
  })
}

/** 获取我关注的歌手 */
export const getFollowArtists = (params: { pageNum: number; pageSize: number; artistName?: string }) => {
  return http<Result>('get', '/favorite/getFollowArtists', { params })
}

/** 关注歌手 */
export const followArtist = (artistId: number) => {
  return http<Result>('post', '/favorite/followArtist', { params: { artistId } })
}

/** 取消关注歌手 */
export const cancelFollowArtist = (artistId: number) => {
  return http<Result>('delete', '/favorite/cancelFollowArtist', { params: { artistId } })
}

/** 是否已关注歌手 */
export const isArtistFollowed = (artistId: number) => {
  return http<Result>('get', '/favorite/isArtistFollowed', { params: { artistId } })
}

/** 新增歌曲评论 */
export const addSongComment = (data: object) => {
  return http<Result>('post', '/comment/addSongComment', { data })
}

/** 新增歌单评论 */
export const addPlaylistComment = (data: object) => {
  return http<Result>('post', '/comment/addPlaylistComment', { data })
}

// 上传评论图片
export const uploadCommentImage = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http<Result>('post', '/comment/uploadImage', { 
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/** 新增专辑评论 */
export const addAlbumComment = (data: object) => {
  return http<Result>('post', '/comment/addAlbumComment', { data })
}

/** 获取专辑评论列表 */
export const getAlbumComments = (albumId: number) => {
  return http<Result>('get', '/comment/getAlbumComments', { params: { albumId } })
}

/** 获取歌曲评论列表（带 children） */
export const getSongComments = (songId: number) => {
  return http<Result>('get', '/comment/getSongComments', { params: { songId } })
}

/** 获取歌单评论列表（带 children） */
export const getPlaylistComments = (playlistId: number) => {
  return http<Result>('get', '/comment/getPlaylistComments', { params: { playlistId } })
}

/** 点赞评论 */
export const likeComment = (commentId: number) => {
  return http<Result>('patch', `/comment/likeComment/${commentId}`)
}

/** 取消点赞评论 */
export const cancelLikeComment = (commentId: number) => {
  return http<Result>('patch', `/comment/cancelLikeComment/${commentId}`)
}

/** 删除评论 */
export const deleteComment = (commentId: number) => {
  return http<Result>('delete', `/comment/deleteComment/${commentId}`)
}

/** 新增反馈 */
export const addFeedback = (data: { content: string }) => {
  return http<Result>('post', '/feedback/addFeedback', { params: data })
}

/** ================= 专辑模块 API ================= */

/** 获取歌手的专辑分页 */
export const getAlbumsByArtist = (params: { artistId: number; pageNum?: number; pageSize?: number }) => {
  const { artistId, pageNum = 1, pageSize = 20 } = params
  return http<Result>('get', '/album/getAlbumsByArtist', { params: { artistId, pageNum, pageSize } })
}

/** 获取专辑详情 */
export const getAlbumDetail = (id: number) => {
  return http<Result>('get', `/album/getAlbumDetail/${id}`)
}

/** 按专辑ID获取歌曲列表 */
export const getSongsByAlbumId = (params: { albumId: number; pageNum?: number; pageSize?: number }) => {
  const { albumId, pageNum = 1, pageSize = 50 } = params
  return http<Result>('get', '/song/getSongsByAlbumId', { params: { albumId, pageNum, pageSize } })
}

/** 收藏专辑 */
export const collectAlbum = (albumId: number) => {
  return http<Result>('post', '/favorite/collectAlbum', { params: { albumId } })
}

/** 取消收藏专辑 */
export const cancelCollectAlbum = (albumId: number) => {
  return http<Result>('delete', '/favorite/cancelCollectAlbum', { params: { albumId } })
}

/** 获取用户收藏的专辑列表 */
export const getFavoriteAlbums = (params: { pageNum: number; pageSize: number; albumTitle?: string }) => {
  return http<Result>('get', '/favorite/getFavoriteAlbums', { params })
}

/** 判断专辑是否已收藏 */
export const isAlbumCollected = (albumId: number) => {
  return http<Result>('get', '/favorite/isAlbumCollected', { params: { albumId } })
}

// ================= 用户资料/关注 API =================
export const getUserProfile = (id: number) => {
  return http<Result>('get', '/user/profile', { params: { id } })
}

export const followUser = (id: number) => {
  return http<Result>('post', '/user/follow', { params: { id } })
}

export const unfollowUser = (id: number) => {
  return http<Result>('delete', '/user/unfollow', { params: { id } })
}

export const isUserFollowed = (id: number) => {
  return http<Result>('get', '/user/isFollowed', { params: { id } })
}

// 他人收藏/创建（用于个人主页展示他人数据）
export const getFavoriteSongsByUser = (
  userId: number,
  data: { pageNum: number; pageSize: number; songName?: string; artistName?: string; album?: string }
) => {
  return http<Result>('post', '/favorite/getFavoriteSongsByUser', { params: { userId }, data })
}

export const getFavoritePlaylistsByUser = (
  userId: number,
  data: { pageNum: number; pageSize: number; title?: string; style?: string }
) => {
  return http<Result>('post', '/favorite/getFavoritePlaylistsByUser', { params: { userId }, data })
}

export const getUserPlaylistsByUser = (
  userId: number,
  data: { pageNum: number; pageSize: number; title?: string; style?: string }
) => {
  return http<Result>('post', '/user/playlist/byUser', { params: { userId }, data })
}

export const getFavoriteAlbumsByUser = (params: { userId: number; pageNum: number; pageSize: number; albumTitle?: string }) => {
  const { userId, pageNum, pageSize, albumTitle } = params
  return http<Result>('get', '/favorite/getFavoriteAlbumsByUser', { params: { userId, pageNum, pageSize, albumTitle } })
}

/** ================= 搜索/热搜 API ================= */

/** 获取热搜关键字 TopN（默认10） */
export const getHotKeywords = (top = 10) => {
  return http<Result>('get', '/search/getHotKeywords', { params: { top } })
}

/** 主动上报一次关键字计数（可选） */
export const reportKeyword = (keyword: string) => {
  return http<Result>('post', '/search/reportKeyword', { params: { keyword } })
}

/** 搜索用户 */
export const searchUsers = (keyword: string, pageNum: number = 1, pageSize: number = 20) => {
  return http<Result>('get', '/user/search', { params: { keyword, pageNum, pageSize } })
}

/** ================= 歌词 API ================= */
export const getLyric = (songId: number) => {
  return http<Result>('get', `/song/getLyric/${songId}`)
}

/** ================= 主题（壁纸） API ================= */
export const getThemeList = () => {
  return http<Result>('get', '/theme/list')
}

export const getMyTheme = () => {
  return http<Result>('get', '/user/theme')
}

export const setMyThemeById = (themeId: number) => {
  return http<Result>('put', '/user/theme', { params: { themeId } })
}

export const uploadMyTheme = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http<Result>('post', '/user/theme/upload', {
    headers: { 'Content-Type': 'multipart/form-data' },
    data: formData,
    transformRequest: [(d) => d],
  })
}

export const setMyCustomTheme = (data: { imageUrl: string; blurhash?: string; colorPrimary?: string }) => {
  const params = new URLSearchParams()
  params.set('imageUrl', data.imageUrl)
  if (data.blurhash) params.set('blurhash', data.blurhash)
  if (data.colorPrimary) params.set('colorPrimary', data.colorPrimary)
  return http<Result>('post', '/user/theme/custom', { headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, data: params })
}

export const resetMyTheme = () => {
  return http<Result>('delete', '/user/theme')
}

/** ================= 我的歌单（用户侧） API ================= */

/** 最近播放：上报一条 */
export const reportRecentPlay = (data: { songId: number }) => {
  return http<Result>('post', '/recent/play', { data })
}

/** 最近播放：分页查询 */
export const getRecentPlays = (data: { pageNum: number; pageSize: number }) => {
  return http<ResultTable>('post', '/recent/list', { data })
}

/** 最近播放：按用户ID分页查询（用于他人主页） */
export const getRecentPlaysByUser = (data: { userId: number; pageNum: number; pageSize: number }) => {
  return http<ResultTable>('post', '/recent/listByUser', { data })
}

/** 最近播放：删除单条 */
export const removeRecentPlay = (params: { songId: number }) => {
  return http<Result>('delete', '/recent/one', { params })
}

/** 最近播放：清空 */
export const clearRecentPlays = () => {
  return http<Result>('delete', '/recent/clear')
}

/** 关注关系：我关注的人（可选 userId） */
export const getFollowing = (params?: { userId?: number }) => {
  return http<Result>('get', '/user/following', { params })
}

/** 关注关系：关注我的人（可选 userId） */
export const getFollowers = (params?: { userId?: number }) => {
  return http<Result>('get', '/user/followers', { params })
}

/** 获取我的歌单分页 */
export const getMyPlaylists = (data: { pageNum: number; pageSize: number; title?: string; style?: string }) => {
  return http<ResultTable>('post', '/user/playlist/my', { data })
}

/** 创建我的歌单 */
export const createMyPlaylist = (data: { title: string; introduction?: string; style?: string }) => {
  return http<Result>('post', '/user/playlist/create', { data })
}

/** 更新我的歌单 */
export const updateMyPlaylist = (data: { playlistId: number; title: string; introduction?: string; style?: string }) => {
  return http<Result>('put', '/user/playlist/update', { data })
}

/** 删除我的歌单 */
export const deleteMyPlaylist = (id: number) => {
  return http<Result>('delete', `/user/playlist/delete/${id}`)
}

/** 添加歌曲到我的歌单 */
export const addSongToMyPlaylist = (params: { playlistId: number; songId: number }) => {
  return http<Result>('post', '/user/playlist/addSong', { params })
}

/** 从我的歌单移除歌曲 */
export const removeSongFromMyPlaylist = (params: { playlistId: number; songId: number }) => {
  return http<Result>('delete', '/user/playlist/removeSong', { params })
}

/** 更新我的歌单封面 */
export const updateMyPlaylistCover = (id: number, file: File) => {
  const formData = new FormData()
  formData.append('cover', file)
  return http<Result>('patch', `/user/playlist/updateCover/${id}`, {
    headers: { 'Content-Type': 'multipart/form-data' },
    data: formData,
    transformRequest: [(d) => d]
  })
}
