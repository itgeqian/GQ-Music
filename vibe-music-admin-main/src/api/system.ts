import { http } from "@/utils/http";
import { getToken } from "@/utils/auth";

export type Result = {
  code: number;
  message: string;
  data?: Array<any> | number | string | object;
};

export type ResultTable = {
  code: number;
  message: string;
  data?: {
    /** 列表数据 */
    items: Array<any>;
    /** 总条目数 */
    total?: number;
    /** 每页显示条目个数 */
    pageSize?: number;
    /** 当前页数 */
    currentPage?: number;
  };
};

/** 用户管理-获取用户列表 */
export const getUserList = (data: object) => {
  const userData = getToken();
  return http.request<ResultTable>("post", "/admin/getAllUsers", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    }, // 确保是 JSON
    data // 直接传 JSON 对象
  });
};

/** 用户管理-新增用户 */
export const addUser = (data: object) => {
  const userData = getToken();
  return http.request<Result>("post", "/admin/addUser", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 用户管理-编辑用户 */
export const updateUser = (data: object) => {
  const userData = getToken();
  return http.request<Result>("put", "/admin/updateUser", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 用户管理-更新用户状态 */
export const updateUserStatus = (id: number, status: number) => {
  const userData = getToken();
  return http.request<Result>(
    "patch",
    `/admin/updateUserStatus/${id}/${status}`,
    {
      headers: {
        Authorization: userData.accessToken
      }
    }
  );
};

/** 用户管理-删除用户 */
export const deleteUser = (id: number) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deleteUser/${id}`, {
    headers: { Authorization: userData.accessToken }
  });
};

/** 用户管理-批量删除用户 */
export const deleteUsers = (ids: Array<number>) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deleteUsers`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data: ids
  });
};

/** 歌手管理-获取歌手列表 */
export const getArtistList = (data: object) => {
  const userData = getToken();
  return http.request<ResultTable>("post", "/admin/getAllArtists", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 歌手管理-新增歌手 */
export const addArtist = (data: object) => {
  const userData = getToken();
  return http.request<Result>("post", "/admin/addArtist", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 歌手管理-编辑歌手 */
export const updateArtist = (data: object) => {
  const userData = getToken();
  return http.request<Result>("put", "/admin/updateArtist", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 歌手管理-更新歌手头像 */
export const updateArtistAvatar = (id: number, data: object) => {
  const userData = getToken();
  return http.request<Result>("patch", `/admin/updateArtistAvatar/${id}`, {
    headers: {
      "Content-Type": "multipart/form-data",
      Authorization: userData.accessToken
    },
    data,
    responseType: "json" // 确保使用正确的响应类型（可以使用 'json' 或 'blob'）
  });
};

/** 歌手管理-删除歌手 */
export const deleteArtist = (id: number) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deleteArtist/${id}`, {
    headers: { Authorization: userData.accessToken }
  });
};

/** 歌手管理-批量删除歌手 */
export const deleteArtists = (ids: Array<number>) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deleteArtists`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data: ids
  });
};

/** 歌曲管理-获取所有歌手 */
export const getAllArtists = () => {
  const userData = getToken();
  return http.request<Result>("get", "/admin/getAllArtistNames", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    }
  });
};

/** 歌曲管理-获取歌曲列表 */
export const getSongList = (data: object) => {
  const userData = getToken();
  return http.request<ResultTable>("post", "/admin/getAllSongsByArtist", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 歌曲管理-新增歌曲 */
export const addSong = (data: object) => {
  const userData = getToken();
  return http.request<Result>("post", "/admin/addSong", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 歌曲管理-编辑歌曲 */
export const updateSong = (data: object) => {
  const userData = getToken();
  return http.request<Result>("put", "/admin/updateSong", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 歌曲管理-更新歌曲封面 */
export const updateSongCover = (id: number, data: object) => {
  const userData = getToken();
  return http.request<Result>("patch", `/admin/updateSongCover/${id}`, {
    headers: {
      "Content-Type": "multipart/form-data",
      Authorization: userData.accessToken
    },
    data,
    responseType: "json" // 确保使用正确的响应类型（可以使用 'json' 或 'blob'）
  });
};

/** 歌曲管理-更新歌曲音频 */
export const updateSongAudio = (id: number, data: object) => {
  const userData = getToken();
  return http.request<Result>("patch", `/admin/updateSongAudio/${id}`, {
    headers: {
      "Content-Type": "multipart/form-data",
      Authorization: userData.accessToken
    },
    data,
    responseType: "json" // 确保使用正确的响应类型（可以使用 'json' 或 'blob'）
  });
};

/** 歌曲管理-更新歌曲歌词（上传 .lrc） */
export const updateSongLyric = (id: number, data: FormData) => {
  const userData = getToken();
  return http.request<Result>("patch", `/admin/updateSongLyric/${id}`, {
    headers: {
      "Content-Type": "multipart/form-data",
      Authorization: userData.accessToken
    },
    data,
    responseType: "json"
  });
};

/** 歌曲管理-获取歌手已有专辑 */
export const getAlbumsByArtist = (artistId: number) => {
  const userData = getToken();
  return http.request<Result>("get", `/admin/getAlbumsByArtist/${artistId}`, {
    headers: { Authorization: userData.accessToken }
  });
};

/** ================= 评论（Admin 与前台复用接口） ================= */
/** 获取专辑评论（前台接口，管理端同样可用） */
export const getAlbumComments = (albumId: number) => {
  const userData = getToken();
  // 管理端改走 /admin 代理接口，避免 403
  return http.request<Result>("get", `/admin/getAlbumComments`, {
    headers: { Authorization: userData.accessToken },
    params: { albumId }
  });
};

/** 删除评论（管理员可删除任意评论） */
export const deleteComment = (commentId: number) => {
  const userData = getToken();
  // 管理端删除评论统一走 /admin，以避免 comment 路径受限
  return http.request<Result>("delete", `/admin/deleteComment/${commentId}`, {
    headers: { Authorization: userData.accessToken }
  });
};

/** 管理端：获取歌曲评论 */
export const getSongComments = (songId: number) => {
  const userData = getToken();
  return http.request<Result>("get", `/admin/getSongComments`, {
    headers: { Authorization: userData.accessToken },
    params: { songId }
  });
};

/** 管理端：获取歌单评论 */
export const getPlaylistComments = (playlistId: number) => {
  const userData = getToken();
  return http.request<Result>("get", `/admin/getPlaylistComments`, {
    headers: { Authorization: userData.accessToken },
    params: { playlistId }
  });
};

/** 歌曲管理-删除歌曲 */
export const deleteSong = (id: number) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deleteSong/${id}`, {
    headers: { Authorization: userData.accessToken }
  });
};

/** 歌曲管理-批量删除歌曲 */
export const deleteSongs = (ids: Array<number>) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deleteSongs`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data: ids
  });
};

/** 歌曲管理-批量导入歌曲 */
export const batchImportSongs = (formData: FormData) => {
  const userData = getToken();
  return http.request<Result>("post", "/admin/batchImportSongs", {
    headers: {
      "Content-Type": "multipart/form-data",
      Authorization: userData.accessToken
    },
    data: formData,
    timeout: 120000 // 批量导入为长耗时操作，单独提升超时为120s
  });
};

/** ================= 专辑管理（Admin） ================= */
/** 专辑-列表 */
export const getAlbumList = (data: object) => {
  const userData = getToken();
  return http.request<Result>("post", "/admin/getAllAlbums", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 新增专辑 */
export const addAlbum = (data: object) => {
  const userData = getToken();
  return http.request<Result>("post", "/admin/addAlbum", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 更新专辑 */
export const updateAlbum = (data: object) => {
  const userData = getToken();
  return http.request<Result>("put", "/admin/updateAlbum", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 更新专辑封面 */
export const updateAlbumCover = (id: number, data: FormData) => {
  const userData = getToken();
  return http.request<Result>("patch", `/admin/updateAlbumCover/${id}`, {
    headers: {
      "Content-Type": "multipart/form-data",
      Authorization: userData.accessToken
    },
    data,
    responseType: "json"
  });
};

/** 删除专辑 */
export const deleteAlbum = (id: number) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deleteAlbum/${id}`, {
    headers: { Authorization: userData.accessToken }
  });
};

/** 批量删除专辑 */
export const deleteAlbums = (ids: Array<number>) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deleteAlbums`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data: ids
  });
};

/** 歌单管理-获取歌单列表 */
export const getPlaylistList = (data: object) => {
  const userData = getToken();
  return http.request<ResultTable>("post", "/admin/getAllPlaylists", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 主题管理 - 列表（用户端接口直接用） */
export const getThemeList = () => {
  const userData = getToken();
  return http.request<Result>("get", "/admin/theme/list", {
    headers: { Authorization: userData.accessToken }
  });
};

/** 主题管理 - 新增 */
export const addTheme = (file: File, name?: string) => {
  const userData = getToken();
  const fd = new FormData();
  fd.append("file", file);
  if (name) fd.append("name", name);
  return http.request<Result>("post", "/admin/theme/add", {
    headers: { "Content-Type": "multipart/form-data", Authorization: userData.accessToken },
    data: fd
  });
};

/** 主题管理 - 更新状态 */
export const updateThemeStatus = (id: number, status: number) => {
  const userData = getToken();
  return http.request<Result>("patch", `/admin/theme/status/${id}`, {
    headers: { Authorization: userData.accessToken },
    params: { status }
  });
};

/** 主题管理 - 删除 */
export const deleteTheme = (id: number) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/theme/${id}`, {
    headers: { Authorization: userData.accessToken }
  });
};

/** 主题管理 - 更新元信息（名称/排序/VIP） */
export const updateThemeMeta = (id: number, data: { name?: string; sort?: number; needVip?: number }) => {
  const userData = getToken();
  const params: any = {};
  if (data.name !== undefined) params.name = data.name;
  if (data.sort !== undefined) params.sort = data.sort;
  if (data.needVip !== undefined) params.needVip = data.needVip;
  return http.request<Result>("patch", `/admin/theme/update/${id}`, {
    headers: { Authorization: userData.accessToken },
    params
  });
};

/** 歌单管理-新增歌单 */
export const addPlaylist = (data: object) => {
  const userData = getToken();
  return http.request<Result>("post", "/admin/addPlaylist", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 歌单管理-编辑歌单 */
export const updatePlaylist = (data: object) => {
  const userData = getToken();
  return http.request<Result>("put", "/admin/updatePlaylist", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 歌单管理-更新歌单封面 */
export const updatePlaylistCover = (id: number, data: object) => {
  const userData = getToken();
  return http.request<Result>("patch", `/admin/updatePlaylistCover/${id}`, {
    headers: {
      "Content-Type": "multipart/form-data",
      Authorization: userData.accessToken
    },
    data,
    responseType: "json" // 确保使用正确的响应类型（可以使用 'json' 或 'blob'）
  });
};

/** 歌单管理-删除歌单 */
export const deletePlaylist = (id: number) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deletePlaylist/${id}`, {
    headers: { Authorization: userData.accessToken }
  });
};

/** 歌单管理-批量删除歌单 */
export const deletePlaylists = (ids: Array<number>) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deletePlaylists`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data: ids
  });
};

/** ================= 用户歌单（只读）与推荐开关 ================= */
export const getUserPlaylists = (data: object) => {
  const userData = getToken();
  return http.request<ResultTable>("post", "/admin/getUserPlaylists", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

export const recommendPlaylist = (id: number, weight = 100) => {
  const userData = getToken();
  return http.request<Result>("post", `/admin/recommendPlaylist/${id}`, {
    headers: { Authorization: userData.accessToken },
    params: { weight }
  });
};

export const cancelRecommendPlaylist = (id: number) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/cancelRecommendPlaylist/${id}`, {
    headers: { Authorization: userData.accessToken }
  });
};

/** ================= 歌单-歌曲绑定（Admin） ================= */
export const addSongsToPlaylist = (data: { playlistId: number; songIds: number[] }) => {
  const userData = getToken();
  return http.request<Result>("post", "/admin/playlist/addSongs", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

export const removeSongsFromPlaylist = (data: { playlistId: number; songIds: number[] }) => {
  const userData = getToken();
  return http.request<Result>("delete", "/admin/playlist/removeSongs", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

export const getSongsOfPlaylist = (data: { playlistId: number; pageNum: number; pageSize: number; keyword?: string }) => {
  const userData = getToken();
  return http.request<ResultTable>("post", "/admin/playlist/songs", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 反馈管理-获取反馈列表 */
export const getFeedbackList = (data: object) => {
  const userData = getToken();
  return http.request<ResultTable>("post", "/admin/getAllFeedbacks", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 反馈管理-删除反馈 */
export const deleteFeedback = (id: number) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deleteFeedback/${id}`, {
    headers: { Authorization: userData.accessToken }
  });
};

/** 反馈管理-批量删除反馈 */
export const deleteFeedbacks = (ids: Array<number>) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deleteFeedbacks`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data: ids
  });
};

/** 轮播图管理-获取轮播图列表 */
export const getBannerList = (data: object) => {
  const userData = getToken();
  return http.request<ResultTable>("post", "/admin/getAllBanners", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 轮播图管理-新增轮播图 */
export const addBanner = (data: object) => {
  const userData = getToken();
  return http.request<Result>("post", "/admin/addBanner", {
    headers: {
      "Content-Type": "multipart/form-data",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 轮播图管理-编辑轮播图 */
export const updateBanner = (id: number, data: object) => {
  const userData = getToken();
  return http.request<Result>("patch", `/admin/updateBanner/${id}`, {
    headers: {
      "Content-Type": "multipart/form-data",
      Authorization: userData.accessToken
    },
    data
  });
};

/** 轮播图管理-编辑轮播图状态 */
export const updateBannerStatus = (id: number, status: number) => {
  const userData = getToken();
  return http.request<Result>("patch", `/admin/updateBannerStatus/${id}`, {
    headers: {
      Authorization: userData.accessToken
    },
    params: { status }
  });
};

/** 轮播图管理-删除轮播图 */
export const deleteBanner = (id: number) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deleteBanner/${id}`, {
    headers: { Authorization: userData.accessToken }
  });
};

/** 轮播图管理-批量删除轮播图 */
export const deleteBanners = (ids: Array<number>) => {
  const userData = getToken();
  return http.request<Result>("delete", `/admin/deleteBanners`, {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    },
    data: ids
  });
};

