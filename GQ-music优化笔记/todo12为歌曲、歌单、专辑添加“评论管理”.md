### 开发文档：为歌曲/歌单/专辑添加“评论管理”（管理端 + 客户端删除）

本文记录了在管理端为“歌曲管理 / 歌单管理 / 专辑管理”三处新增“评论管理”入口与页面的过程，以及客户端专辑页为本人评论添加删除能力的关键实现。

## 一、后端改动

- 目标
  - 管理端可按资源维度拉取评论列表，并支持删除任意评论（管理员权限）。
  - 解决管理员删除时无 `userId` 导致的 NPE。

### 1) Mapper：按资源维度拉取评论列表

```java
// src/main/java/cn/edu/seig/vibemusic/mapper/CommentMapper.java
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    // 专辑评论（type=2，使用 playlist_id 存放 albumId）
    @Select("SELECT c.id AS commentId, u.username AS username, u.user_avatar AS userAvatar, c.content AS content, c.create_time AS createTime, c.like_count AS likeCount FROM tb_comment c LEFT JOIN tb_user u ON c.user_id = u.id WHERE c.playlist_id = #{albumId} AND c.type = 2 ORDER BY c.id DESC")
    List<CommentVO> getAlbumComments(Long albumId);

    // 歌曲评论（type=0）
    @Select("SELECT c.id AS commentId, u.username AS username, u.user_avatar AS userAvatar, c.content AS content, c.create_time AS createTime, c.like_count AS likeCount FROM tb_comment c LEFT JOIN tb_user u ON c.user_id = u.id WHERE c.song_id = #{songId} AND c.type = 0 ORDER BY c.id DESC")
    List<CommentVO> getSongComments(Long songId);

    // 歌单评论（type=1）
    @Select("SELECT c.id AS commentId, u.username AS username, u.user_avatar AS userAvatar, c.content AS content, c.create_time AS createTime, c.like_count AS likeCount FROM tb_comment c LEFT JOIN tb_user u ON c.user_id = u.id WHERE c.playlist_id = #{playlistId} AND c.type = 1 ORDER BY c.id DESC")
    List<CommentVO> getPlaylistComments(Long playlistId);
}
```

### 2) Service：接口与实现

```java
// src/main/java/cn/edu/seig/vibemusic/service/ICommentService.java
public interface ICommentService extends IService<Comment> {
    Result<List<CommentVO>> getAlbumComments(Long albumId);
    Result<List<CommentVO>> getSongComments(Long songId);
    Result<List<CommentVO>> getPlaylistComments(Long playlistId);
    Result<String> deleteComment(Long commentId);
}
```

```java
// src/main/java/cn/edu/seig/vibemusic/service/impl/CommentServiceImpl.java
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements ICommentService {

    @Override
    public Result<List<CommentVO>> getSongComments(Long songId) {
        return Result.success(commentMapper.getSongComments(songId));
    }

    @Override
    public Result<List<CommentVO>> getPlaylistComments(Long playlistId) {
        return Result.success(commentMapper.getPlaylistComments(playlistId));
    }

    // 允许管理员删除任意评论；普通用户仅能删除自己的评论
    @Override
    @CacheEvict(cacheNames = {"songCache", "playlistCache", "albumCache"}, allEntries = true)
    public Result<String> deleteComment(Long commentId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        String role = claims == null ? null : (String) claims.get(JwtClaimsConstant.ROLE);
        boolean isAdmin = RoleEnum.ADMIN.getRole().equals(role);

        Long userId = null;
        if (!isAdmin) {
            Object userIdObj = claims == null ? null : claims.get(JwtClaimsConstant.USER_ID);
            if (userIdObj == null) return Result.error(MessageConstant.NO_PERMISSION);
            userId = TypeConversionUtil.toLong(userIdObj);
        }

        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) return Result.error(MessageConstant.NOT_FOUND);
        if (!isAdmin && !Objects.equals(comment.getUserId(), userId)) return Result.error(MessageConstant.NO_PERMISSION);

        return commentMapper.deleteById(commentId) == 0
                ? Result.error(MessageConstant.DELETE + MessageConstant.FAILED)
                : Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }
}
```

### 3) Controller（管理端代理接口）

为避免管理端请求 `/comment/**` 受限或缓存策略干扰，增加 `/admin/**` 代理接口：

```java
// src/main/java/cn/edu/seig/vibemusic/controller/AdminController.java
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private ICommentService commentService;

    // 管理端拉取评论列表（歌曲/歌单/专辑）
    @GetMapping("/getSongComments")
    public Result<List<CommentVO>> getSongCommentsAdmin(@RequestParam Long songId) {
        return commentService.getSongComments(songId);
    }

    @GetMapping("/getPlaylistComments")
    public Result<List<CommentVO>> getPlaylistCommentsAdmin(@RequestParam Long playlistId) {
        return commentService.getPlaylistComments(playlistId);
    }

    @GetMapping("/getAlbumComments")
    public Result<List<CommentVO>> getAlbumCommentsAdmin(@RequestParam Long albumId) {
        return commentService.getAlbumComments(albumId);
    }

    // 管理端删除任意评论
    @DeleteMapping("/deleteComment/{id}")
    public Result<String> deleteAnyComment(@PathVariable("id") Long commentId) {
        return commentService.deleteComment(commentId);
    }
}
```

> 权限：`application.yml` 已允许 `ROLE_ADMIN` 访问 `"/admin/"`（以及 `"/comment/"`）。无需额外变更。

---

## 二、管理端前端改动（Vue + Element Plus）

### 1) API 封装

```ts
// vibe-music-admin-main/src/api/system.ts
export const getSongComments = (songId: number) =>
  http.request<Result>("get", `/admin/getSongComments`, { headers: { Authorization: getToken().accessToken }, params: { songId } });

export const getPlaylistComments = (playlistId: number) =>
  http.request<Result>("get", `/admin/getPlaylistComments`, { headers: { Authorization: getToken().accessToken }, params: { playlistId } });

export const getAlbumComments = (albumId: number) =>
  http.request<Result>("get", `/admin/getAlbumComments`, { headers: { Authorization: getToken().accessToken }, params: { albumId } });

export const deleteComment = (commentId: number) =>
  http.request<Result>("delete", `/admin/deleteComment/${commentId}`, { headers: { Authorization: getToken().accessToken } });
```

### 2) 路由新增“评论管理”隐藏页

```ts
// 歌曲：vibe-music-admin-main/src/router/modules/song.ts
{
  path: "/song/:id/comments",
  name: "SongComments",
  component: () => import("@/views/song/comments.vue"),
  meta: { title: "歌曲评论管理", showLink: false }
}

// 歌单：vibe-music-admin-main/src/router/modules/playlist.ts
{
  path: "/playlist/:id/comments",
  name: "PlaylistComments",
  component: () => import("@/views/playlist/comments.vue"),
  meta: { title: "歌单评论管理", showLink: false }
}
```

### 3) 列表页操作栏加入“评论管理”入口

```vue
<!-- 歌曲管理：vibe-music-admin-main/src/views/song/index.vue（下拉菜单中） -->
<el-dropdown-item>
  <el-button :class="buttonClass" link type="primary" :size="size"
             :icon="useRenderIcon(ChatLineSquare)"
             @click="$router.push({ name: 'SongComments', params: { id: row.songId } })">
    评论管理
  </el-button>
</el-dropdown-item>

<!-- 歌单管理：vibe-music-admin-main/src/views/playlist/index.vue -->
<el-dropdown-item>
  <el-button :class="buttonClass" link type="primary" :size="size"
             :icon="useRenderIcon(ChatLineSquare)"
             @click="$router.push({ name: 'PlaylistComments', params: { id: row.playlistId } })">
    评论管理
  </el-button>
</el-dropdown-item>

<!-- 专辑管理：vibe-music-admin-main/src/views/album/index.vue（已存在） -->
<el-dropdown-item>
  <el-button :class="buttonClass" link type="primary" :size="size"
             :icon="useRenderIcon(ChatLineSquare)"
             @click="$router.push({ name: 'AlbumComments', params: { id: row.albumId } })">
    评论管理
  </el-button>
</el-dropdown-item>
```

### 4) 新建“评论管理”页面（歌曲 / 歌单）

两者结构一致，表格列含：评论ID、用户、内容、时间、点赞、操作（删除）。

```vue
<!-- 歌曲：vibe-music-admin-main/src/views/song/comments.vue（核心片段） -->
<pure-table :data="dataList" :columns="dynamicColumns" :pagination="pagination">
  <template #operation="{ row }">
    <el-popconfirm title="确认删除该评论？" @confirm="onDelete(row)">
      <template #reference><el-button link type="danger">删除</el-button></template>
    </el-popconfirm>
  </template>
</pure-table>

<script setup lang="ts">
import { getSongComments, deleteComment } from "@/api/system";
const songId = Number(useRoute().params.id);
async function fetchData() { const res:any = await getSongComments(songId); dataList.value = res?.data ?? []; }
function onDelete(row:any){ deleteComment(row.commentId).then(()=>fetchData()); }
</script>
```

```vue
<!-- 歌单：vibe-music-admin-main/src/views/playlist/comments.vue（核心片段） -->
<script setup lang="ts">
import { getPlaylistComments, deleteComment } from "@/api/system";
const playlistId = Number(useRoute().params.id);
async function fetchData() { const res:any = await getPlaylistComments(playlistId); dataList.value = res?.data ?? []; }
function onDelete(row:any){ deleteComment(row.commentId).then(()=>fetchData()); }
</script>
```

> 专辑评论管理页已实现，同样调用 `/admin/getAlbumComments` + `/admin/deleteComment/{id}`。

---

## 三、客户端专辑页：本人评论删除（无弹窗，点即删）

- 接口沿用前台 `/comment/deleteComment/{id}`，后端会校验：普通用户只能删除自己评论；管理员不限。

```ts
// vibe-music-client-main/src/pages/album/[id].vue（核心增量）
import { deleteComment } from '@/api/system'
const currentUsername = computed(() => userStore.userInfo?.username || '')

const handleDelete = async (c: any) => {
  if (!userStore.isLoggedIn) return
  if (c?.username !== currentUsername.value) return
  const res = await deleteComment(c.commentId)
  if (res.code === 0) {
    ElMessage.success('删除成功')
    comments.value = comments.value.filter(item => item.commentId !== c.commentId)
  }
}
```

```vue
<!-- 删除按钮（仅本人显示；已去除二次确认弹窗） -->
<button v-if="userStore.isLoggedIn && c.username === currentUsername"
        class="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        @click="handleDelete(c)">
  <icon-ic:round-delete class="text-base" /> 删除
</button>
```

---

## 四、验证与注意事项

- 验证流程
  - 管理端：歌曲/歌单/专辑 → 操作 … → 评论管理 → 刷新/删除。
  - 客户端：登录后在专辑页发布评论 → 本人评论出现“删除”，可直接删除。
- 权限/403
  - 管理端统一走 `/admin/*` 代理接口，确保 `ROLE_ADMIN` 可访问（`application.yml` 已配置）。
- NPE 修复
  - 删除时管理员跳过 `userId` 校验，避免 `TypeConversionUtil.toLong(null)` 触发 NPE。
- 缓存
  - 删除/点赞使用 `@CacheEvict` 清理相关缓存，确保前台刷新后数据一致。
