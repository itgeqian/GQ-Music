## todo18 客户端-我的歌单模块

下面是“我的歌单”模块今日开发文档（第1部分：概述与后端），我会分两部分发，下一条补前端与关键交互。

### 一、模块目标
- 为用户提供个人歌单的完整能力：增删改查、上传封面、添加/移除歌曲、评论管理。
- 与管理端歌单管理解耦：管理端仅管理官方歌单；用户歌单仅出现在用户侧与“用户歌单（只读）”页。
- 推荐系统：固定推荐（常驻）+ 个性化/随机补足；支持管理员对任意歌单置顶推荐。

### 二、数据库与实体改动
- 在 `tb_playlist` 增加 `user_id` 字段，用于区分用户歌单与官方歌单。
- 新增推荐表 `tb_playlist_recommendation`（id, playlist_id, weight, created_by, create_time）。

关键实体：
```12:67:src/main/java/cn/edu/seig/vibemusic/model/entity/Playlist.java
@TableName("tb_playlist")
public class Playlist implements Serializable {
  @TableId(value = "id", type = IdType.AUTO)
  private Long playlistId;

  @TableField("title")
  private String title;

  @TableField("cover_url")
  private String coverUrl;

  @TableField("user_id")
  private Long userId; // 我的歌单归属

  @TableField("introduction")
  private String introduction;

  @TableField("style")
  private String style;
}
```

### 三、后端接口设计（用户侧）
- 基础路径：`/user/playlist`
- 能力：创建、更新、删除、分页查询、添加/移除歌曲、更新封面。

控制器：
```1:70:src/main/java/cn/edu/seig/vibemusic/controller/UserPlaylistController.java
@RestController
@RequestMapping("/user/playlist")
public class UserPlaylistController {
  @PostMapping("/create")
  public Result<String> create(@RequestBody @Valid PlaylistAddDTO dto) { ... }

  @PutMapping("/update")
  public Result<String> update(@RequestBody @Valid PlaylistUpdateDTO dto) { ... }

  @DeleteMapping("/delete/{id}")
  public Result<String> delete(@PathVariable("id") Long playlistId) { ... }

  @PostMapping("/my")
  public Result<PageResult<PlaylistVO>> my(@RequestBody @Valid PlaylistDTO dto) { ... }

  @PostMapping("/addSong")
  public Result<String> addSong(@RequestParam Long playlistId, @RequestParam Long songId) { ... }

  @DeleteMapping("/removeSong")
  public Result<String> removeSong(@RequestParam Long playlistId, @RequestParam Long songId) { ... }

  @PatchMapping("/updateCover/{id}")
  public Result<String> updateCover(@PathVariable Long id, @RequestParam("cover") MultipartFile cover) { ... }
}
```

服务接口新增的方法：
```1:84:src/main/java/cn/edu/seig/vibemusic/service/IPlaylistService.java
// =================== 用户侧：我的歌单 ===================
Result<String> addUserPlaylist(PlaylistAddDTO playlistAddDTO);
Result<String> updateUserPlaylist(PlaylistUpdateDTO playlistUpdateDTO);
Result<String> deleteUserPlaylist(Long playlistId);
Result<PageResult<PlaylistVO>> getMyPlaylists(PlaylistDTO playlistDTO);
Result<String> addSongToMyPlaylist(Long playlistId, Long songId);
Result<String> removeSongFromMyPlaylist(Long playlistId, Long songId);

// ================ 管理端只读 + 推荐 =================
Result<PageResult<PlaylistVO>> getUserPlaylistsOnly(PlaylistDTO playlistDTO);
Result<String> recommendPlaylist(Long playlistId, Integer weight);
Result<String> cancelRecommendPlaylist(Long playlistId);
Result<List<PlaylistVO>> getPinnedRecommendedPlaylists(Integer limit);
```

推荐逻辑与官方/用户歌单隔离（节选）：
```55:136:src/main/java/cn/edu/seig/vibemusic/service/impl/PlaylistServiceImpl.java
// 管理端“歌单管理”仅展示官方歌单
queryWrapper.isNull("user_id");

// 推荐：固定推荐常驻 + 个性化/随机补足，保证10条
List<PlaylistVO> pinned = playlistMapper.getPinnedRecommendedPlaylists(10);
int remain = Math.max(0, 10 - pinned.size());
// 未登录：固定 + 随机
// 登录：固定 + 按收藏风格加权推荐 + 再不足随机
```

### 四、前端 API（用户侧）映射
```281:321:vibe-music-client-main/src/api/system.ts
export const getMyPlaylists = (data) => http('post','/user/playlist/my',{ data })
export const createMyPlaylist = (data) => http('post','/user/playlist/create',{ data })
export const updateMyPlaylist = (data) => http('put','/user/playlist/update',{ data })
export const deleteMyPlaylist = (id) => http('delete', `/user/playlist/delete/${id}`)
export const addSongToMyPlaylist = (params) => http('post','/user/playlist/addSong',{ params })
export const removeSongFromMyPlaylist = (params) => http('delete','/user/playlist/removeSong',{ params })
export const updateMyPlaylistCover = (id, file) => http('patch', `/user/playlist/updateCover/${id}`, { ... })
```

—— 下条消息将写第2部分（前端实现细节：页面、裁剪上传、添加歌曲、评论管理、以及通用歌曲表的“三个点-添加到歌单”关键代码）。

继续（第2部分：前端实现与关键代码）

### 一、页面与导航
- 新增侧边栏入口与路由：`/my-playlists`。
- 页面文件：`vibe-music-client-main/src/pages/my-playlists/index.vue`。

### 二、页面结构与数据模型
- 搜索/筛选、分页、列表数据与风格常量：
```1:48:vibe-music-client-main/src/pages/my-playlists/index.vue
// 简单的字节单位格式化
const formatBytes = (bytes: number): string => { ... }

type PlaylistRow = {
  playlistId: number
  title: string
  coverUrl?: string
  style?: string
  introduction?: string
}

type SongRow = {
  songId: number
  songName: string
  artistName: string
  album: string
  coverUrl?: string
}
```

- 搜索/筛选、分页、列表获取：
```51:70:vibe-music-client-main/src/pages/my-playlists/index.vue
const form = reactive<{ title: string; style: string | undefined }>({ ... })
const loading = ref(false)
const list = ref<PlaylistRow[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const styleOptions = [ '节奏布鲁斯', '欧美流行', ... '古典' ]
```

```155:180:vibe-music-client-main/src/pages/my-playlists/index.vue
const handleSearch = () => fetchList(true)
const handleReset = () => { form.title = ''; form.style = undefined; fetchList(true) }
const handleCurrentChange = (p: number) => { pageNum.value = p; fetchList() }
const handleSizeChange = (s: number) => { pageSize.value = s; fetchList(true) }

const fetchList = async (reset = false) => {
  if (reset) pageNum.value = 1
  loading.value = true
  try {
    const res: any = await getMyPlaylists({ pageNum: pageNum.value, pageSize: pageSize.value, title: form.title || undefined, style: form.style || undefined })
    if (res.code === 0 && res.data) {
      list.value = (res.data.items || []) as PlaylistRow[]
      total.value = Number(res.data.total || 0)
    } else {
      ElMessage.error(res.message || '获取数据失败')
    }
  } finally { loading.value = false }
}
```

### 三、我的歌单 CRUD
- 对话框模型与校验：
```70:89:vibe-music-client-main/src/pages/my-playlists/index.vue
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogFormRef = ref<FormInstance>()
const dialogForm = reactive<{ playlistId?: number; title: string; style?: string; introduction?: string }>({ ... })
const dialogRules: FormRules = { title: [{ required: true, message: '请输入歌单名称', trigger: 'blur' }] }
```

- 新建/编辑/删除：
```96:138:vibe-music-client-main/src/pages/my-playlists/index.vue
const openEdit = (row: PlaylistRow) => { ... }
const submitDialog = async () => {
  await dialogFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (dialogMode.value === 'create') {
      const res: any = await createMyPlaylist({ title: dialogForm.title, style: dialogForm.style, introduction: dialogForm.introduction })
      ...
    } else {
      const res: any = await updateMyPlaylist({ playlistId: dialogForm.playlistId as number, title: dialogForm.title, style: dialogForm.style, introduction: dialogForm.introduction })
      ...
    }
  })
}
```

```140:149:vibe-music-client-main/src/pages/my-playlists/index.vue
const handleDelete = async (row: PlaylistRow) => {
  await ElMessageBox.confirm(`确认删除歌单「${row.title}」？此操作不可撤销`, '提示', { type: 'warning' })
  const res: any = await deleteMyPlaylist(row.playlistId)
  if (res.code === 0) { ElMessage.success('删除成功'); fetchList() } else { ElMessage.error(res.message || '删除失败') }
}
```

### 四、上传封面（裁剪 + MinIO）
- 裁剪弹窗、右键快捷菜单、Cropper 初始化与 `as any` 兼容处理：
```187:241:vibe-music-client-main/src/pages/my-playlists/index.vue
const cropDialogVisible = ref(false)
const cropPreview = ref<string>('')
const cropImgEl = ref<HTMLImageElement>()
let cropper: Cropper | null = null

const openCropper = async (row: PlaylistRow) => {
  cropTarget.value = row
  await pickImageFile().then(async (file) => {
    ...
    await nextTick()
    if (cropper && (cropper as any).destroy) { (cropper as any).destroy() }
    cropper = new Cropper(cropImgEl.value as HTMLImageElement, {
      aspectRatio: 1, viewMode: 1, background: false, autoCropArea: 1,
      movable: true, zoomable: true, scalable: true, responsive: true,
      crop() {
        const canvas = (cropper as any).getCroppedCanvas({ width: 800, height: 800 })
        cropPreview.value = canvas.toDataURL('image/jpeg', 0.9)
      }
    } as any)
  })
}
```

- 右键菜单与提交上传：
```243:261:vibe-music-client-main/src/pages/my-playlists/index.vue
const onCropContextMenu = (e: MouseEvent) => { e.preventDefault(); ... }
const resetCrop = () => (cropper as any)?.reset?.()
const rotateLeft = () => (cropper as any)?.rotate?.(-45)
...
```

```272:290:vibe-music-client-main/src/pages/my-playlists/index.vue
const submitCropper = async () => {
  (cropper as any).getCroppedCanvas({ width: 800, height: 800 }).toBlob(async (blob: Blob | null) => {
    const file = new File([blob], 'cover.jpg', { type: 'image/jpeg' })
    const res: any = await updateMyPlaylistCover(cropTarget.value!.playlistId, file)
    if (res.code === 0) { ElMessage.success('封面上传成功'); cropDialogVisible.value = false; fetchList() } else { ... }
  }, 'image/jpeg', 0.9)
}
```

### 五、添加歌曲（“选择添加 + 已绑定” 双标签）
- 查询与分页、多选收集：
```295:346:vibe-music-client-main/src/pages/my-playlists/index.vue
const addSongDialogVisible = ref(false)
const songQuery = reactive<{ pageNum: number; pageSize: number; songName?: string; artistName?: string; album?: string }>({ pageNum: 1, pageSize: 10 })
const songList = ref<SongRow[]>([])
const songSelected = ref<Set<number>>(new Set())
...
const fetchSongList = async () => { const res = await getAllSongs({ ... }); songList.value = res.data.items || []; songTotal.value = Number(res.data.total || 0) }
const onSongSelectionChange = (selection: SongRow[]) => { songSelected.value = new Set(selection.map(s => s.songId)) }
```

- 已绑定歌曲获取与移除：
```352:372:vibe-music-client-main/src/pages/my-playlists/index.vue
const fetchBoundList = async () => {
  const res: any = await getPlaylistDetail(addSongTargetPlaylistId.value)
  if (res.code === 0 && res.data && Array.isArray(res.data.songs)) {
    boundList.value = res.data.songs.map((s: any) => ({ songId: s.songId, songName: s.songName, artistName: s.artistName, album: s.album, coverUrl: s.coverUrl }))
  } else { boundList.value = [] }
}
```

```378:396:vibe-music-client-main/src/pages/my-playlists/index.vue
const confirmAddSongs = async () => {
  let ok = 0
  for (const sid of songSelected.value) {
    const res: any = await addSongToMyPlaylist({ playlistId: addSongTargetPlaylistId.value, songId: sid })
    if (res.code === 0) ok++
  }
  if (ok > 0) { ElMessage.success(`已添加 ${ok} 首歌曲`); fetchBoundList(); activeAddTab.value = 'bound' } else { ElMessage.error('添加失败') }
}
```

```398:416:vibe-music-client-main/src/pages/my-playlists/index.vue
const removeBoundSongs = async () => {
  let ok = 0
  for (const sid of boundSelected.value) {
    const res: any = await (await import('@/api/system')).removeSongFromMyPlaylist({ playlistId: addSongTargetPlaylistId.value, songId: sid })
    if (res.code === 0) ok++
  }
  if (ok > 0) { ElMessage.success(`已移除 ${ok} 首歌曲`); fetchBoundList() } else { ElMessage.error('操作失败') }
}
```

- 操作下拉（对齐修正）：
```490:510:vibe-music-client-main/src/pages/my-playlists/index.vue
<el-table-column label="操作" ...>
  <div class="inline-flex items-center gap-2 align-middle">
    <el-button ...>查看</el-button>
    <el-button ...>编辑</el-button>
    <el-popconfirm ...><el-button type="danger" link>删除</el-button></el-popconfirm>
    <el-dropdown>
      <span class="text-[hsl(var(--el-color-primary))] cursor-pointer ml-2 align-middle select-none">更多</span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item @click="openCropper(row)">上传封面</el-dropdown-item>
          <el-dropdown-item @click="openAddSong(row)">添加歌曲</el-dropdown-item>
          <el-dropdown-item @click="openComments(row)">评论管理</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</el-table-column>
```

### 六、评论管理
```418:448:vibe-music-client-main/src/pages/my-playlists/index.vue
type PlaylistComment = { commentId: number; username: string; content: string; createTime: string; likeCount: number }
const openComments = async (row: PlaylistRow) => { commentPlaylistId.value = row.playlistId; commentDialogVisible.value = true; fetchComments() }
const fetchComments = async () => { const res: any = await getPlaylistDetail(commentPlaylistId.value); comments.value = (res.data.comments || []) }
const onDeleteComment = async (row: PlaylistComment) => { await ElMessageBox.confirm('确定删除该评论？', '提示', { type: 'warning' }); const res = await deleteComment(row.commentId); ... }
```

### 七、通用歌曲表新增“更多（三个点）→ 添加到歌单”
- 引入 API：
```1:13:vibe-music-client-main/src/components/Table.vue
import { collectSong, cancelCollectSong, getMyPlaylists, addSongToMyPlaylist, createMyPlaylist } from '@/api/system'
```

- 处理菜单命令：
```188:207:vibe-music-client-main/src/components/Table.vue
const handleMoreCommand = async (command: string, row: Song) => {
  if (command === 'like') { const evt = { stopPropagation() {} } as Event; await handleLike(row, evt); return }
  if (command === 'download') { const evt = { stopPropagation() {} } as Event; await downLoadMusic(row, evt); return }
  if (command === 'add') { openAddToDialog(row); return }
}
```

- 菜单和弹窗（列与对话框，含“已有歌单/新建歌单”）：
```398:431:vibe-music-client-main/src/components/Table.vue
<!-- 更多（三个点） -->
<div class="flex items-center ml-1">
  <el-dropdown @command="(cmd:string)=>handleMoreCommand(cmd, row)">
    <el-button text circle @click.stop>
      <icon-hugeicons:more class="text-lg" />
    </el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="like">{{ row.likeStatus===1 ? '取消喜欢' : '我喜欢' }}</el-dropdown-item>
        <el-dropdown-item divided command="add">添加到</el-dropdown-item>
        <el-dropdown-item divided command="download">下载</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</div>
```

```...:...:vibe-music-client-main/src/components/Table.vue
// 弹窗状态 + 拉取我的歌单 + 添加/新建并添加（见文件中 addDialogVisible、fetchMyPlaylists、confirmAddToExisting、createPlaylistAndAdd 实现）
```

### 八、错误与兼容修复记录
- cropper 样式导入从 `cropper.css` 改为 `cropper.min.css`；TypeScript 对 `getCroppedCanvas`、`destroy`、`aspectRatio` 的类型报错通过 `as any` 兼容。
- “更多”下拉的对齐通过 `align-middle`、`inline-flex` 修正，避免偏位。

### 九、测试要点
- 未登录拦截所有需要登录的操作（播放、喜欢、添加到歌单、下载）。
- 我的歌单增删改查与分页过滤正确；编辑后的风格/简介在列表列 `style/introduction` 返回正确。
- 裁剪上传封面：右键菜单可用，文件大小与分辨率展示正确；上传后刷新列表封面。
- 添加歌曲：搜索分页、多选；已绑定标签避免重复；可移除绑定。
- 表格“三个点”：我喜欢/取消喜欢与下载复用现有逻辑；“添加到”弹窗可选已有歌单或新建后添加。

如果需要，我可以把以上内容整理为 Markdown 文档文件放入仓库（如 `docs/我的歌单-开发记录.md`），并补上页面截图与交互动图。